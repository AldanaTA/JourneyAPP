BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users, how they sign in, and their sessions.
--
--   users ──< auth_identities   (password, Google, Discord, ...; a user can have several)
--   users ──< refresh_tokens    (one per session/device, rotated on every use)
--   refresh_tokens ──< access_tokens (short-lived tokens issued from that session)
--
-- Security rules:
-- - Passwords are never stored. Password identities store a slow hash (Argon2id or bcrypt) only.
-- - Tokens are never stored. Rows hold the SHA-256 hash of the token; the raw token is shown to the
--   client once and then only compared by hash, so a database leak cannot be used to sign in.

-- ----------------------------
-- Users
-- ----------------------------

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    display_name TEXT NOT NULL,
    -- Primary contact email (may differ from the emails of individual sign-in identities).
    email TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_url TEXT,

    -- Disabled users cannot sign in or refresh; existing tokens should be revoked when this is set.
    disabled_at TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Emails are optional but unique when present, ignoring case.
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_unique
ON users (LOWER(email))
WHERE email IS NOT NULL;

-- ----------------------------
-- Sign-in identities
-- ----------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'auth_provider') THEN
        CREATE TYPE auth_provider AS ENUM (
            'password',
            'google',
            'discord'
        );
    END IF;
END $$;

-- One row per way a user can sign in. Linking Google and Discord to the same user is two rows.
CREATE TABLE IF NOT EXISTS auth_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    provider auth_provider NOT NULL,
    -- The provider's stable id for the user: the "sub" claim for Google/Discord, the normalized
    -- (lowercased, trimmed) email or username for password sign-in.
    provider_subject TEXT NOT NULL,

    -- Email reported by the provider, kept for display and account linking.
    email TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,

    -- Only for provider = 'password': an Argon2id or bcrypt hash, including its salt and parameters.
    password_hash TEXT,
    password_updated_at TIMESTAMP WITH TIME ZONE,

    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_auth_identities_provider_subject UNIQUE (provider, provider_subject),
    -- Password identities must have a hash; OAuth identities must not.
    CONSTRAINT chk_auth_identities_password_hash CHECK (
        (provider = 'password') = (password_hash IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_auth_identities_user_id
ON auth_identities (user_id);

-- At most one password per user.
CREATE UNIQUE INDEX IF NOT EXISTS idx_auth_identities_one_password_per_user
ON auth_identities (user_id)
WHERE provider = 'password';

-- ----------------------------
-- Refresh tokens (sessions)
-- ----------------------------

-- A refresh token lives for days/weeks and is exchanged for new access tokens. Every exchange rotates
-- it: the old row is revoked and points at its replacement. All tokens rotated from the same sign-in
-- share a family_id, so if a revoked token is ever presented again (it was stolen), the whole family can
-- be revoked at once.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- The identity used to sign in for this session.
    identity_id UUID REFERENCES auth_identities(id) ON DELETE SET NULL,

    -- Hex SHA-256 of the raw token.
    token_hash TEXT NOT NULL,
    family_id UUID NOT NULL,
    replaced_by_id UUID REFERENCES refresh_tokens(id) ON DELETE SET NULL,

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    -- "rotated", "logout", "reuse_detected", "password_changed", "admin", ...
    revoked_reason TEXT,

    -- For "active sessions" lists.
    user_agent TEXT,
    ip_address INET,
    last_used_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_refresh_tokens_not_own_replacement CHECK (replaced_by_id IS NULL OR replaced_by_id <> id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
ON refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id
ON refresh_tokens (family_id);

-- Finds expired tokens for clean-up jobs.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
ON refresh_tokens (expires_at);

-- ----------------------------
-- Access tokens
-- ----------------------------

-- Short-lived tokens (minutes) sent with each API request. Storing them (as hashes) makes them
-- revocable immediately and lets the API look up the user and scopes with one query.
CREATE TABLE IF NOT EXISTS access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- The session that issued this token; revoking or deleting the session removes its access tokens.
    refresh_token_id UUID REFERENCES refresh_tokens(id) ON DELETE CASCADE,

    -- Hex SHA-256 of the raw token.
    token_hash TEXT NOT NULL,
    -- Optional permission scopes, e.g. {"content:read", "content:write"}.
    scopes TEXT[] NOT NULL DEFAULT '{}',

    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_access_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_access_tokens_expiry CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_access_tokens_user_id
ON access_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_access_tokens_refresh_token_id
ON access_tokens (refresh_token_id);

CREATE INDEX IF NOT EXISTS idx_access_tokens_expires_at
ON access_tokens (expires_at);

COMMIT;
