BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'power_type') THEN
        CREATE TYPE power_type AS ENUM ('Destruction', 'Support', 'Sabotage', 'Utility');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS powers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,

    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,

    lvl INT NOT NULL,
    type power_type NOT NULL,

    -- COST
    tp_cost INT NOT NULL,
    hp_cost INT,
    mp_cost INT,
    ep_cost INT,

    -- COMPONENTS
    material_components TEXT DEFAULT 'None',
    verbal BOOLEAN NOT NULL,
    sight BOOLEAN NOT NULL,
    somatic BOOLEAN NOT NULL,
    is_distinct BOOLEAN NOT NULL,
    concentration BOOLEAN NOT NULL,

    -- RANGE, AREA, DURATION
    range TEXT DEFAULT '5',
    area TEXT DEFAULT '0',
    duration TEXT DEFAULT 'Instantaneous',

    -- EFFECT
    effect TEXT NOT NULL,
    empower_effect TEXT DEFAULT 'None',
    lvl_up_effect TEXT DEFAULT 'None',

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_powers_name_key_unique
ON powers (name_key);

CREATE INDEX IF NOT EXISTS idx_powers_type
ON powers (type);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'power_category') THEN
        CREATE TYPE power_category AS ENUM (
            'damage',
            'defense',
            'healing_recovery',
            'buff',
            'debuff',
            'control',
            'mobility',
            'strong_attack',
            'light_attack',
            'magic',
            'summoning_companions',
            'acid',
            'bludgeoning',
            'cold',
            'dark',
            'fire',
            'force',
            'light',
            'lightning',
            'piercing',
            'poison',
            'psychic',
            'slashing',
            'bleeding',
            'blessed',
            'blinded',
            'charmed',
            'cursed',
            'dazed',
            'deafened',
            'enfeeble',
            'exhaustion',
            'frightened',
            'impaired',
            'incapacitated',
            'infatuated',
            'inspired',
            'invisible',
            'mighty',
            'petrified',
            'prone',
            'restrained',
            'rush',
            'silenced',
            'sluggish',
            'stunned',
            'suppressed',
            'unconscious',
            'vulnerable',
            'declared',
            'triggered'
        );
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS power_categories (
    power_id UUID NOT NULL REFERENCES powers(id) ON DELETE CASCADE,
    category power_category NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (power_id, category)
);

CREATE INDEX IF NOT EXISTS idx_power_categories_category
ON power_categories (category);

COMMIT;