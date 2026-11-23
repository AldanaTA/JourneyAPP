BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'size') THEN
        CREATE TYPE size AS ENUM (
            'T',
            'S',
            'M',
            'L',
            'H',
            'C'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS races (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    race_name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(race_name))) STORED,
    race_size size DEFAULT 'M' NOT NULL,
    movement INT NOT NULL DEFAULT 30,
    languages TEXT NOT NULL DEFAULT 'Common',
    race_description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_races_race_name UNIQUE (race_name)
);

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