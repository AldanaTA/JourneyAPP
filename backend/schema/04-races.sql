BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS races (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    race_name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(race_name))) STORED,
    min_race_size size DEFAULT 'M' NOT NULL,
    max_race_size size,
    movement INT NOT NULL DEFAULT 30,
    languages TEXT NOT NULL DEFAULT 'Common',
    race_description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_races_race_name UNIQUE (race_name)
);

-- Case-insensitive uniqueness; the race importer upserts on this (ON CONFLICT (name_key)).
CREATE UNIQUE INDEX IF NOT EXISTS idx_races_name_key_unique
ON races (name_key);

CREATE TABLE IF NOT EXISTS race_traits (
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    trait_id UUID NOT NULL REFERENCES traits(id) ON DELETE CASCADE,
    trait_name TEXT NOT NULL,

    sort_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (race_id, trait_id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_race_traits_sort_order UNIQUE (race_id, sort_order)
);

COMMIT;