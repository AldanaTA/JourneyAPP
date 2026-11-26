BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------
-- Enums
-- ----------------------------

-- What a source is:
--   core     - the core rules
--   book     - an official book or setting (e.g. Calthora)
--   campaign - a storyteller's own content for one campaign
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'source_kind') THEN
        CREATE TYPE source_kind AS ENUM (
            'core',
            'book',
            'campaign'
        );
    END IF;
END $$;

-- Who can read a source:
--   public   - everyone
--   entitled - users who own it (user_entitlements), plus its members
--   members  - only its members (source_members), e.g. a campaign's players
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'source_visibility') THEN
        CREATE TYPE source_visibility AS ENUM (
            'public',
            'entitled',
            'members'
        );
    END IF;
END $$;

-- Ordered from least to most access: viewers read, editors also write, owners also manage members.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'source_role') THEN
        CREATE TYPE source_role AS ENUM (
            'viewer',
            'editor',
            'owner'
        );
    END IF;
END $$;

-- ----------------------------
-- Content sources
-- ----------------------------

-- Every piece of content (traits, powers, items, races, jobs, creatures, rules, assets) belongs to a
-- source, so read/write access is granted per source rather than per row.
CREATE TABLE IF NOT EXISTS content_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    source_name TEXT NOT NULL,
    source_desc TEXT NOT NULL,

    kind source_kind NOT NULL DEFAULT 'book',
    visibility source_visibility NOT NULL DEFAULT 'entitled',
    -- The storyteller who owns a campaign; NULL for official content (core and books).
    -- RESTRICT: a user who still owns campaigns cannot be deleted until the campaigns are transferred
    -- or deleted on purpose (deleting them automatically would remove content players rely on).
    owner_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_content_sources_campaign_owner CHECK (kind <> 'campaign' OR owner_user_id IS NOT NULL)
);

-- Official names (core and books) are unique across the app; campaign names only need to be unique per
-- owner, so two storytellers can both have a campaign called "Homebrew".
CREATE UNIQUE INDEX IF NOT EXISTS idx_content_sources_official_name_unique
ON content_sources (source_name)
WHERE kind IN ('core', 'book');

CREATE UNIQUE INDEX IF NOT EXISTS idx_content_sources_campaign_name_unique
ON content_sources (owner_user_id, source_name)
WHERE kind = 'campaign';

CREATE INDEX IF NOT EXISTS idx_content_sources_owner_user_id
ON content_sources (owner_user_id);

INSERT INTO content_sources (source_name, source_desc, kind, visibility)
VALUES
    ('Core', 'The core content source for the game.', 'core', 'public'),
    ('Calthora', 'The Calthora content source for the game.', 'book', 'public')
ON CONFLICT (source_name) WHERE kind IN ('core', 'book') DO NOTHING;

-- ----------------------------
-- Access
-- ----------------------------

-- People granted access to a source: a campaign's players (viewer), trusted co-authors (editor), and
-- the owner. Members can read the source whatever its visibility.
CREATE TABLE IF NOT EXISTS source_members (
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role source_role NOT NULL,

    -- Who added this member; NULL when granted by the system.
    granted_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (source_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_source_members_user_id
ON source_members (user_id);

-- Books a user owns (purchased or granted). Owning a book lets the user read an 'entitled' source.
CREATE TABLE IF NOT EXISTS user_entitlements (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    -- Where the entitlement came from: "purchase", "gift", "admin", ...
    origin TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (user_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_user_entitlements_source_id
ON user_entitlements (source_id);

COMMIT;
