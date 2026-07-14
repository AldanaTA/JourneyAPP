BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS character_sheets(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    details JSONB DEFAULT '{"schema_version": "1.0", "type": "character_sheet", "content": {}}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS characters(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    character_sheet_id UUID NOT NULL REFERENCES character_sheets(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    details JSONB DEFAULT '{"schema_version": "1.0", "type": "character_sheet", "content": {}}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

COMMIT;