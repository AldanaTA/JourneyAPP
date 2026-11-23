BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS races (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    race_name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_races_race_name UNIQUE (race_name)
);

CREATE TABLE IF NOT EXISTS race_traits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE CASCADE,
    trait_name TEXT NOT NULL,

    trait_name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(trait_name))) STORED,

    lvl INT NOT NULL,
    purchase_cost INT NOT NULL,
    lvl_up_cost INT NOT NULL,

    trait_desc TEXT NOT NULL,

    trait_uses INT DEFAULT 0,
    mp_cost INT DEFAULT 0,
    ep_cost INT DEFAULT 0,
    tp_cost INT DEFAULT 0,

    trait_effect TEXT NOT NULL,
    lvl_up_effect TEXT DEFAULT 'None',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_race_traits_trait_name UNIQUE (race_id, trait_name)
);

COMMIT;