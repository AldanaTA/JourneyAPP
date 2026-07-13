BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS traits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

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
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_traits_trait_name_key_unique
ON traits (trait_name_key);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'trait_category') THEN
        CREATE TYPE trait_category AS ENUM (
            'damage',
            'defense',
            'healing_recovery',
            'buff',
            'debuff',
            'control',
            'mobility',
            'weapon',
            'magic',
            'summoning_companions',
            'transformation',
            'resource_economy',
            'skill',
            'utility_exploration',
            'crafting_items',
            'stealth_deception',
            'social_roleplay',
            'status_ailments',
            'environmental',
            'passive',
            'declared',
            'triggered',
            'other'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS trait_categories (
    trait_id UUID NOT NULL REFERENCES traits(id) ON DELETE CASCADE,
    category trait_category NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (trait_id, category)
);

CREATE INDEX IF NOT EXISTS idx_trait_categories_category
ON trait_categories(category);

COMMIT;