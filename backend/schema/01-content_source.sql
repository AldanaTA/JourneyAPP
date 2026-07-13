BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS content_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    source_name TEXT NOT NULL,
    source_desc TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_content_sources_source_name UNIQUE (source_name)
);

INSERT INTO content_sources (source_name, source_desc)
VALUES
    ('Core', 'The core content source for the game.'),
    ('Calthora', 'The Calthora content source for the game.'),
ON CONFLICT (source_name) DO NOTHING;

COMMIT;