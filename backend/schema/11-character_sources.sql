BEGIN;

-- The content sources a character is built from (chosen on the first page of character creation). Pickers
-- for races, jobs, traits, powers, and items only offer content from these sources. The core rules are
-- always included by the app, so they do not need a row here.
CREATE TABLE IF NOT EXISTS character_content_sources (
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (character_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_character_content_sources_source_id
ON character_content_sources (source_id);

-- Calthora is the base setting, so every user can read it and build characters with it.
UPDATE content_sources
SET visibility = 'public', updated_at = NOW()
WHERE kind = 'book' AND source_name = 'Calthora' AND visibility <> 'public';

COMMIT;
