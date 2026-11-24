BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------
-- Enums
-- ----------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'item_rarity') THEN
        CREATE TYPE item_rarity AS ENUM (
            'common',
            'uncommon',
            'rare',
            'very_rare'
        );
    END IF;
END $$;

-- What an item is (its form). Whether it is magic is the separate items.is_magic flag,
-- because a magic item can be any form (a magic sword is still a weapon).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'item_type') THEN
        CREATE TYPE item_type AS ENUM (
            'weapon',
            'armor',
            'shield',
            'ammunition',
            'potion',
            'accessory',
            'magic_item',
            'bag',
            'med_kit',
            'skill_book',
            'tool',
            'alchemy_ingredient',
            'cooking_ingredient',
            'gem',
            'metal',
            'miscellaneous'
        );
    END IF;
END $$;

-- ----------------------------
-- Weapon reference data
-- ----------------------------

-- Weapon types and the effect every weapon of that type grants (e.g. Bow -> Nimble Shot).
CREATE TABLE IF NOT EXISTS weapon_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,

    effect_name TEXT NOT NULL,
    effect TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_weapon_types_name_key_unique
ON weapon_types (name_key);

INSERT INTO weapon_types (name, effect_name, effect)
VALUES
    ('Arcane Focus', 'Flexible Arts', 'The damage type can be changed for the weapon. The defender can use either of the skills listed for their defensive skill roll and you can use 5 MP on a straight success in order to do the damage type effect for that instance of damage.'),
    ('Bow', 'Nimble Shot', 'Once per round when you take the dash action, you can take the reaction attack action.'),
    ('Colossal', 'Heavy Swing', 'Once per round, when you make a strong attack and get a straight success, you can apply the damage type effect of your colossal weapon''s damage type.'),
    ('Firearm', 'Called Shot', 'At the start of the round choose a target within your weapon''s range, the first attack you make against that target gains a 5% critical chance bonus.'),
    ('Martial', 'Skilled Strike', 'Once per round, when you get a straight success on a reaction attack, you can add 1 more weapon die equal to the largest weapon die you have on the attack.'),
    ('Simple', 'Reliable Strike', 'Once per round you can add a 10% bonus to a weapon attack.')
ON CONFLICT (name_key)
DO UPDATE SET
    name = EXCLUDED.name,
    effect_name = EXCLUDED.effect_name,
    effect = EXCLUDED.effect,
    updated_at = NOW();

CREATE TABLE IF NOT EXISTS weapon_properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,

    description TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_weapon_properties_name_key_unique
ON weapon_properties (name_key);

INSERT INTO weapon_properties (name, description)
VALUES
    ('Ammunition', 'The weapon requires ammunition to function. This weapon receives half of the ammo used at the end of the combat session unless it is a firearm.'),
    ('Burst', 'The weapon can shoot two or more projectiles (max: mag). All of the projectiles go to one target adding a 1d6 weapon die per ammunition spent. Your range is halved for the attack.'),
    ('Colossal', 'The creature must have a Strength Attribute of 70 in order to wield the weapon. If they do not meet the requirement then all rolls done with the weapon are done under the Difficult condition. Light attacks minimum skill rating increases to 60%. You gain a +30% to your weapon skill rating for strong attacks.'),
    ('Crusher', 'Creatures have the -20% on parry skill rolls against this weapon.'),
    ('Defense', 'This weapon gains +20% on parry skill rolls.'),
    ('Drawed', 'The weapon has a minimum range of 30ft. The weapon gains 5 ft for every 5 points of strength if it does not have the heavy or colossal property, 5ft for every 10 points of strength if it has the heavy property, and 5 ft for every 15 points of strength if it has the colossal property.'),
    ('Finesse', 'The creature can use 1 TP to take the reaction attack action when they successfully dodge a melee attack.'),
    ('Heavy', 'The creature must have a Strength Attribute of 45 in order to wield the weapon. If they do not meet the requirement then all rolls done with the weapon are done under the Difficult condition. Light attacks minimum skill rating increases to 50%. You gain a +20% to your weapon skill rating for strong attacks.'),
    ('Light', 'You gain a 20% bonus when you take the Attack action and choose to make light attacks.'),
    ('Loading', 'The weapon requires 2TP to load. The weapon needs to be loaded when projectiles equal to the magazine size (Mag) are used.'),
    ('Long', 'The creature has -20% on attack rolls when another creature is engaged within 5ft of it. The weapon also has the reach property.'),
    ('Nimble', 'This weapon gains the +20% to the weapon skill when used for a reaction attack.'),
    ('Reach', 'You can attack creatures 10ft away instead of 5 ft away.'),
    ('Magic Shield', 'The creature can use a magic skill to parry melee attacks.'),
    ('Thrown', 'The weapon has a minimum range of 20ft. The weapon gains 5 ft for every 5 points of strength if it does not have the heavy or colossal property, 5ft for every 10 points of strength if it has the heavy property, and 5 ft for every 15 points of strength if it has the colossal property. The weapon requires no TP to draw and 1 TP to stow all weapons of this type. Additionally you can carry three of these weapons in 1 inventory slot.'),
    ('Two-Hand', 'The weapon requires two-hands to wield effectively. If the weapon is not two-handed then all actions using the weapon are done under the Difficult condition. You gain a +20% to attacks.'),
    ('Variable', 'All creatures attacked with this weapon have a -20% on dodge skill rolls against this weapon.'),
    ('Versatile', 'The weapon can be two-handed to increase the attack rolls of this weapon by 30%. The state of the weapon, one-hand or two-handed, must be declared at the start of the round to get this benefit.'),
    ('Quick', 'You do not trigger reaction attacks by using this weapon for a ranged attack.')
ON CONFLICT (name_key)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

-- ----------------------------
-- Items
-- ----------------------------

-- Inheritance (base_item_id):
--   * Always the item's own:  name, rarity, cost, description, effect, is_magic,
--                             requires_attunement, charges, stack_limit, bundle_quantity.
--   * Inherited from the base unless set on this item:
--       - material_id                     (NULL = inherit / none)
--       - item_weapons row                (whole row replaces the base's)
--       - item_weapon_properties rows     (own list replaces the base's)
--       - details                         (merged: base.details || item.details)
--   * A derived item keeps its base's item_type; the base chain must not loop.
--
-- details holds type-specific fields:
--   armor / shield:      {"strength_req": 60, "modifiers": [{"skill": "Dodge", "value": 20}, ...]}
--   ammunition:          {"ammo_type": "arrow" | "bullet"}
--   bag:                 {"inventory_slots": 4}
--   skill_book:          {"skills": ["Alchemy"], "skill_bonus": 10, "sessions": 2}
--   tool:                {"discipline": "alchemy" | "enchanting" | "smithing"}
--   alchemy_ingredient:  {"role": "tonic" | "resistance" | "poison" | "enhancer"}
--   gem:                 {"magic_item_effect": "..."}
--   metal:               {"armor_effect": "...", "weapon_effect": "..."}
CREATE TABLE IF NOT EXISTS items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,

    item_type item_type NOT NULL,

    -- The item this one is based on (e.g. a magic sword based on Iron Long Sword).
    base_item_id UUID REFERENCES items(id) ON DELETE RESTRICT,
    -- The metal the item is made of (a 'metal' item, e.g. Iron).
    material_id UUID REFERENCES items(id) ON DELETE RESTRICT,

    is_magic BOOLEAN NOT NULL DEFAULT FALSE,
    rarity item_rarity NOT NULL,

    description TEXT,
    effect TEXT,

    -- Price in bronze ("20 Gold" -> 2000); NULL when the item has no listed price.
    cost_bronze INT CHECK (cost_bronze >= 0),
    -- Units that fit in one inventory slot.
    stack_limit INT NOT NULL DEFAULT 1 CHECK (stack_limit >= 1),
    -- Units bought for cost_bronze ("Standard Arrows x10" -> 10).
    bundle_quantity INT NOT NULL DEFAULT 1 CHECK (bundle_quantity >= 1),

    -- Attuned items count toward a character's attunement limit.
    requires_attunement BOOLEAN NOT NULL DEFAULT FALSE,
    -- NULL = passive / no charges.
    charges INT CHECK (charges >= 0),

    details JSONB NOT NULL DEFAULT '{}'::JSONB,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_items_not_own_base CHECK (base_item_id IS NULL OR base_item_id <> id),
    CONSTRAINT chk_items_not_own_material CHECK (material_id IS NULL OR material_id <> id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_items_name_key_unique
ON items (name_key);

CREATE INDEX IF NOT EXISTS idx_items_item_type
ON items (item_type);

CREATE INDEX IF NOT EXISTS idx_items_base_item_id
ON items (base_item_id);

CREATE INDEX IF NOT EXISTS idx_items_material_id
ON items (material_id);

-- Weapon stats. Present for base weapons; present on a derived item only when it overrides them.
CREATE TABLE IF NOT EXISTS item_weapons (
    item_id UUID PRIMARY KEY REFERENCES items(id) ON DELETE CASCADE,

    weapon_type_id UUID NOT NULL REFERENCES weapon_types(id),

    damage_dice TEXT NOT NULL,    -- "2d6"
    damage_bonus TEXT,            -- "5", "PD", "MD"
    damage_type_id UUID NOT NULL REFERENCES damage_types(id),

    range_ft INT CHECK (range_ft >= 0),   -- NULL for melee
    mag INT CHECK (mag >= 1),             -- NULL when the weapon has no magazine

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_item_weapons_weapon_type_id
ON item_weapons (weapon_type_id);

CREATE INDEX IF NOT EXISTS idx_item_weapons_damage_type_id
ON item_weapons (damage_type_id);

-- Weapon properties. References items (not item_weapons) so a derived item can override its
-- properties without overriding its stats. An item's own list replaces its base's.
CREATE TABLE IF NOT EXISTS item_weapon_properties (
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES weapon_properties(id),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (item_id, property_id)
);

CREATE INDEX IF NOT EXISTS idx_item_weapon_properties_property_id
ON item_weapon_properties (property_id);

COMMIT;
