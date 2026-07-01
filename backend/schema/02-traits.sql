BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS traits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), 
    trait_name TEXT NOT NULL,
    LVL INT NOT NULL,
    purchase_Cost INT NOT NULL,
    LVL_UP_Cost INT NOT NULL,
    trait_desc TEXT NOT NULL,
    trait_uses INT Default 0,
    mp_cost INT Default 0,
    ep_cost INT Default 0,
    tp_cost INT Default 0,
    trait_effect TEXT NOT NULL,
    LVL_UP_Effect TEXT Default 'None',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

Create INDEX IF NOT EXISTS idx_trait_name ON traits (trait_name);

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
    'reaction_timing',
    'status_ailments',
    'environmental',
    'passive',
    'declared',
    'triggered',
    'other'
);
CREATE TABLE trait_categories(
    trait_id UUID NOT NULL REFERENCES traits(id) ON DELETE CASCADE,
    category trait_category NOT NULL,
     created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (trait_id, category)
);

CREATE INDEX idx_trait_category
ON trait_categories(category);

COMMIT;