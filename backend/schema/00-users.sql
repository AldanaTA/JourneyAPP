BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
    CREATE TYPE user_role AS ENUM (
      'admin',
      'moderator',
      'user'
    );
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    username TEXT NOT NULL,
    email TEXT NOT NULL,
    password_hash TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role user_role NOT NULL,

    PRIMARY KEY (user_id, role)
);

-- REFRESH TOKENS
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash   TEXT NOT NULL,
  expires_at   TIMESTAMPTZ NOT NULL,
  revoked_at   TIMESTAMPTZ,
  user_agent   TEXT,
  ip_address   INET,
  replaced_by_id UUID REFERENCES refresh_tokens(id) ON DELETE SET NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Best practice: store only a hash, not the raw token.
  CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash)
);

-- Best practice: partial index for "active tokens" queries.
-- Login flows typically check active (non-revoked, non-expired) tokens.
CREATE INDEX IF NOT EXISTS refresh_tokens_active_by_user_idx
  ON refresh_tokens(user_id, expires_at)
  WHERE revoked_at IS NULL;

-- Optional: quickly find tokens nearing expiry (cleanup jobs).
CREATE INDEX IF NOT EXISTS refresh_tokens_expires_at_idx
  ON refresh_tokens(expires_at);

COMMIT;