BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Follows the creature stat block in the Storyteller Tools (Creature Creation):
--   Name / Threat LVL / Type / Size / Description / Languages
--   Lore
--   Combat: HP, WP, MP, EP, PRIO, MVM, PD, MD
--   Skills, Augments (Immune / Resistant / Weak to damage types or status conditions)
--   Abilities: Passives / Triggers / Declared (attacks and power casts are declared)
-- Threat points are only used to build a creature and are not stored.

-- ----------------------------
-- Enums
-- ----------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'augment_kind') THEN
        CREATE TYPE augment_kind AS ENUM (
            'immune',
            'resistant',
            'weak'
        );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ability_timing') THEN
        CREATE TYPE ability_timing AS ENUM (
            'passive',
            'triggered',
            'declared'
        );
    END IF;
END $$;

-- ----------------------------
-- Creature types (per source)
-- ----------------------------

-- Each source defines its own creature types; a creature must use a type from its own source.
CREATE TABLE IF NOT EXISTS creature_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,
    description TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Target of the (creature_type_id, source_id) foreign key on creatures.
    CONSTRAINT uq_creature_types_id_source UNIQUE (id, source_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_creature_types_source_name_key_unique
ON creature_types (source_id, name_key);

WITH calthora_source AS (
    SELECT id
    FROM content_sources
    WHERE source_name = 'Calthora'
)
INSERT INTO creature_types (
    source_id,
    name,
    description
)
SELECT
    calthora_source.id,
    new_type.name,
    new_type.description
FROM calthora_source
CROSS JOIN (
    VALUES
        ('Amphibian', 'These are creatures that require land and water to survive, such as frogs and salamanders.'),
        ('Aques', 'These are creatures that live in water, such as fish and whales.'),
        ('Beast', 'These are creatures that are typically wild and untamed, such as lions and bears.'),
        ('Bird', 'These are creatures that have feathers and wings, such as eagles and penguins.'),
        ('Celestial', 'These are creatures that are associated with light magic, such as sereaphim and unicorns.'),
        ('Construct', 'These are creatures that are artificially created, such as golems and robots.'),
        ('Dragon', 'These are large, powerful creatures that are often depicted as having wings and the ability to breathe fire.'),
        ('Fiend', 'These are creatures that are associated with dark magic, such as imps and skale'),
        ('Monstrosity', 'These are creatures that are often grotesque or unatural appearnace, such as chimeras and hydras.'),
        ('Reptile', 'These are creatures that are typically cold-blooded and have scales, such as snakes and lizards.'),
        ('Humanoid', 'These are creatures that have a human-like appearance, such as goblins and gnolls'),
        ('Insect', 'These are creatures that have an exoskeleton and three body segments, such as ants and beatles'),
        ('Sprina', 'These are nature spririts that are often associated with plants and the elements such as dryads and nymphs.'),
        ('Undead', 'These are creatures that have died but continue to exist in some form, such as zombies and skeletons.')
) AS new_type(name, description)
ON CONFLICT (source_id, name_key) DO NOTHING;

-- ----------------------------
-- Creatures
-- ----------------------------

CREATE TABLE IF NOT EXISTS creatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,

    creature_type_id UUID NOT NULL,
    threat_lvl INT NOT NULL CHECK (threat_lvl >= 1),

    -- Size or size range ("S-M"); max_size is set only for a range, like races.
    min_size size NOT NULL DEFAULT 'M',
    max_size size,

    description TEXT,
    languages TEXT,
    lore TEXT,

    -- Combat
    hp INT NOT NULL CHECK (hp >= 0),
    wp INT CHECK (wp >= 0),               -- optional; points here can go to health instead
    mp INT NOT NULL DEFAULT 0 CHECK (mp >= 0),
    ep INT NOT NULL DEFAULT 0 CHECK (ep >= 0),
    prio INT NOT NULL DEFAULT 0,
    movement INT NOT NULL DEFAULT 30 CHECK (movement >= 0),   -- MVM in feet
    pd INT NOT NULL DEFAULT 0,
    md INT NOT NULL DEFAULT 0,

    -- Listed skill ratings: [{"skill": "Brawl", "rating": 140}, ...]. Unlisted skills are 40%.
    skills JSONB NOT NULL DEFAULT '[]'::JSONB,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- The type must belong to the creature's own source.
    CONSTRAINT fk_creatures_type_same_source
        FOREIGN KEY (creature_type_id, source_id)
        REFERENCES creature_types (id, source_id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_creatures_name_key_unique
ON creatures (name_key);

CREATE INDEX IF NOT EXISTS idx_creatures_creature_type_id
ON creatures (creature_type_id);

CREATE INDEX IF NOT EXISTS idx_creatures_threat_lvl
ON creatures (threat_lvl);

-- Immunities, resistances, and weaknesses. Each one targets exactly one damage type or status condition.
CREATE TABLE IF NOT EXISTS creature_augments (
    creature_id UUID NOT NULL REFERENCES creatures(id) ON DELETE CASCADE,
    kind augment_kind NOT NULL,

    -- The name of the damage type or status condition ("Fire", "Blinded", ...), kept for display and as
    -- part of the key.
    target TEXT NOT NULL,
    damage_type_id UUID REFERENCES damage_types(id),
    status_condition_id UUID REFERENCES status_conditions(id),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (creature_id, kind, target),
    CONSTRAINT chk_creature_augments_one_target CHECK (num_nonnulls(damage_type_id, status_condition_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_creature_augments_damage_type_id
ON creature_augments (damage_type_id);

CREATE INDEX IF NOT EXISTS idx_creature_augments_status_condition_id
ON creature_augments (status_condition_id);

-- Passives, triggers, and declared abilities other than attacks.
-- effect is the creature's own (condensed) text. trait_id / power_id link to the full game trait or
-- power when the ability is one; both are NULL for abilities unique to the creature.
CREATE TABLE IF NOT EXISTS creature_abilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creature_id UUID NOT NULL REFERENCES creatures(id) ON DELETE CASCADE,

    timing ability_timing NOT NULL,
    name TEXT NOT NULL,
    effect TEXT NOT NULL,

    trait_id UUID REFERENCES traits(id) ON DELETE SET NULL,
    power_id UUID REFERENCES powers(id) ON DELETE SET NULL,
    lvl INT CHECK (lvl >= 1),              -- trait/power level, e.g. "casts Darkness at LVL 7"

    uses INT CHECK (uses >= 0),
    mp_cost INT CHECK (mp_cost >= 0),
    ep_cost INT CHECK (ep_cost >= 0),
    tp_cost INT CHECK (tp_cost >= 0),

    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_creature_abilities_one_link CHECK (trait_id IS NULL OR power_id IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_creature_abilities_creature_id
ON creature_abilities (creature_id);

CREATE INDEX IF NOT EXISTS idx_creature_abilities_trait_id
ON creature_abilities (trait_id);

CREATE INDEX IF NOT EXISTS idx_creature_abilities_power_id
ON creature_abilities (power_id);

-- Declared attacks: "Attack: Skill, Range: ft, <properties>, Damage: 1d4 + PD damage type".
CREATE TABLE IF NOT EXISTS creature_attacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creature_id UUID NOT NULL REFERENCES creatures(id) ON DELETE CASCADE,

    name TEXT NOT NULL,                    -- "Bite", "Longsword"
    skill TEXT NOT NULL,                   -- "Brawl", "Martial"
    range_ft INT CHECK (range_ft >= 0),
    damage_dice TEXT NOT NULL,             -- "2d6"
    damage_bonus TEXT,                     -- "12", "PD", "MD"
    damage_type_id UUID NOT NULL REFERENCES damage_types(id),
    mag INT CHECK (mag >= 1),

    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_creature_attacks_creature_id
ON creature_attacks (creature_id);

CREATE INDEX IF NOT EXISTS idx_creature_attacks_damage_type_id
ON creature_attacks (damage_type_id);

-- Weapon properties on an attack (Light, Crusher, Ammunition, ...), shared with item weapons.
CREATE TABLE IF NOT EXISTS creature_attack_properties (
    attack_id UUID NOT NULL REFERENCES creature_attacks(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES weapon_properties(id),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (attack_id, property_id)
);

CREATE INDEX IF NOT EXISTS idx_creature_attack_properties_property_id
ON creature_attack_properties (property_id);

COMMIT;
