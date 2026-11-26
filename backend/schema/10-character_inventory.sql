BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- A character's inventory, following the Inventory rules and the character sheet's Inventory tab.
--
--   character_inventories        one row per character: money, base attunement limit, stack-limit multiplier
--   character_inventory_slots    slots the player adds and labels (from a bag, a trait, ...)
--   character_attunement_slots   extra attunements the player adds and labels (from a trait, an item, ...)
--   character_items              every item the character has, and where it is
--
-- Slots of both kinds can be made in groups: a trait that gives 3 slots becomes "Spatial Storage 1..3" with
-- group_name "Spatial Storage", so the group can be listed, extended, or removed together.
--
-- Every character has these fixed places:
--   armor, left_hand, right_hand (or both_hands), bag   one item each, the equipped gear
--   components                                          magic components pouch, no slot limit
--   quest                                               quest items, no slot limit
-- plus money in character_inventories. Everything else goes in a slot the player manages. The schema does
-- not create slots from a bag's or trait's slot count: the player adds and labels them (e.g. "Common Bag 1",
-- "Pack Mule 1"), so storytellers can house-rule capacity freely.
--
-- What the database enforces: one item in each equipped place, a two-handed item blocking both hands, one
-- stack per slot, slots and items belonging to the same character. What the API enforces: a stack's
-- quantity within the item's stack_limit, and attuned items within the attunement limit (both depend on
-- the items table, which can change).

-- ----------------------------
-- Enums
-- ----------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'inventory_location') THEN
        CREATE TYPE inventory_location AS ENUM (
            'armor',
            'left_hand',
            'right_hand',
            -- A two-handed item: takes both hands, so neither hand can hold anything else.
            'both_hands',
            'bag',
            'components',
            'quest',
            -- In one of the player's own slots (character_items.slot_id).
            'slot'
        );
    END IF;
END $$;

-- ----------------------------
-- Money and attunement
-- ----------------------------

-- Coins are kept per denomination, as on the sheet (1 platinum = 100 gold = 1000 silver = 10000 bronze);
-- the API can offer "make change" without the database converting anything.
CREATE TABLE IF NOT EXISTS character_inventories (
    character_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,

    platinum INT NOT NULL DEFAULT 0 CHECK (platinum >= 0),
    gold INT NOT NULL DEFAULT 0 CHECK (gold >= 0),
    silver INT NOT NULL DEFAULT 0 CHECK (silver >= 0),
    bronze INT NOT NULL DEFAULT 0 CHECK (bronze >= 0),

    -- Base number of attuned items (3 by the rules). The effective limit adds one per attunement slot
    -- (character_attunement_slots), e.g. 3 + "Stable Body 1", "Stable Body 2" = 5.
    attunement_limit INT NOT NULL DEFAULT 3 CHECK (attunement_limit >= 0),
    -- Multiplies every item's stack limit, e.g. 2 for Supply Sergeant ("doubles stack limits").
    stack_limit_multiplier INT NOT NULL DEFAULT 1 CHECK (stack_limit_multiplier >= 1),

    notes TEXT NOT NULL DEFAULT '',

    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ----------------------------
-- Player-managed slots
-- ----------------------------

-- Each slot carries one stack of items (up to the item's stack limit).
CREATE TABLE IF NOT EXISTS character_inventory_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,

    -- Whatever the player calls it: "Common Bag 1", "Bag of Holding 3", "Pack Mule".
    label TEXT NOT NULL DEFAULT '',
    -- Slots made together share a group ("Spatial Storage"); their labels are "<group> <n>".
    group_name TEXT,
    notes TEXT NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Target of character_items' slot foreign key, so an item can only go in its own character's slot.
    CONSTRAINT uq_character_inventory_slots_character UNIQUE (character_id, id)
);

CREATE INDEX IF NOT EXISTS idx_character_inventory_slots_character_id
ON character_inventory_slots (character_id, sort_order);

-- ----------------------------
-- Extra attunements
-- ----------------------------

-- One row per extra item a character can attune to, labelled by where it came from ("Stable Body 1").
-- The effective attunement limit is character_inventories.attunement_limit plus the number of rows here.
CREATE TABLE IF NOT EXISTS character_attunement_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,

    label TEXT NOT NULL DEFAULT '',
    group_name TEXT,
    notes TEXT NOT NULL DEFAULT '',
    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_character_attunement_slots_character_id
ON character_attunement_slots (character_id, sort_order);

-- ----------------------------
-- Items
-- ----------------------------

-- One row per stack of items the character has. Usually a compendium item; custom_name covers things
-- that are not in the compendium (a sealed letter, a stranger's key), and can also rename a compendium
-- item ("Grandpa's Sword" for an Iron Long Sword).
CREATE TABLE IF NOT EXISTS character_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,

    -- RESTRICT: a compendium item a character carries cannot be deleted out from under them.
    item_id UUID REFERENCES items(id) ON DELETE RESTRICT,
    custom_name TEXT,

    location inventory_location NOT NULL,
    slot_id UUID,

    quantity INT NOT NULL DEFAULT 1 CHECK (quantity >= 1),
    -- This stack's own limit, when it differs from the item's stack limit x the character's multiplier
    -- (a storyteller ruling, a special container). NULL = the usual limit.
    stack_limit INT CHECK (stack_limit >= 1),
    -- Attuned items count toward character_inventories.attunement_limit.
    attuned BOOLEAN NOT NULL DEFAULT FALSE,
    -- Charges left; NULL when the item has no charges.
    charges_remaining INT CHECK (charges_remaining >= 0),
    notes TEXT NOT NULL DEFAULT '',
    -- Order within a place that holds several items (components, quest).
    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Removing a slot is refused while something is in it; move or drop the item first.
    CONSTRAINT fk_character_items_slot FOREIGN KEY (character_id, slot_id)
        REFERENCES character_inventory_slots (character_id, id),

    CONSTRAINT chk_character_items_named CHECK (item_id IS NOT NULL OR NULLIF(TRIM(custom_name), '') IS NOT NULL),
    CONSTRAINT chk_character_items_slot CHECK ((location = 'slot') = (slot_id IS NOT NULL))
);

-- For databases created before these columns existed.
ALTER TABLE character_inventories ADD COLUMN IF NOT EXISTS stack_limit_multiplier INT NOT NULL DEFAULT 1
    CHECK (stack_limit_multiplier >= 1);
ALTER TABLE character_inventory_slots ADD COLUMN IF NOT EXISTS group_name TEXT;
ALTER TABLE character_items ADD COLUMN IF NOT EXISTS stack_limit INT CHECK (stack_limit >= 1);

CREATE INDEX IF NOT EXISTS idx_character_items_character_id
ON character_items (character_id, location, sort_order);

CREATE INDEX IF NOT EXISTS idx_character_items_item_id
ON character_items (item_id);

-- One stack per slot.
CREATE UNIQUE INDEX IF NOT EXISTS idx_character_items_one_per_slot
ON character_items (slot_id)
WHERE slot_id IS NOT NULL;

-- One item each in armor and bag.
CREATE UNIQUE INDEX IF NOT EXISTS idx_character_items_one_armor
ON character_items (character_id)
WHERE location = 'armor';

CREATE UNIQUE INDEX IF NOT EXISTS idx_character_items_one_bag
ON character_items (character_id)
WHERE location = 'bag';

-- Each hand holds one item, and a two-handed item takes both.
CREATE UNIQUE INDEX IF NOT EXISTS idx_character_items_left_hand
ON character_items (character_id)
WHERE location IN ('left_hand', 'both_hands');

CREATE UNIQUE INDEX IF NOT EXISTS idx_character_items_right_hand
ON character_items (character_id)
WHERE location IN ('right_hand', 'both_hands');

-- ----------------------------
-- Views
-- ----------------------------

-- Inventory checks the database cannot do on its own; the API reads these before saving and the
-- sheet can show them as warnings.
CREATE OR REPLACE VIEW character_inventory_summary AS
SELECT
    c.id AS character_id,
    (SELECT COUNT(*) FROM character_inventory_slots s WHERE s.character_id = c.id) AS slot_count,
    (SELECT COUNT(*) FROM character_items ci WHERE ci.character_id = c.id AND ci.location = 'slot') AS used_slots,
    (SELECT COUNT(*) FROM character_items ci WHERE ci.character_id = c.id AND ci.attuned) AS attuned_count,
    -- Effective limit: the base plus one per attunement slot.
    CAST(COALESCE(inv.attunement_limit, 3)
        + (SELECT COUNT(*) FROM character_attunement_slots a WHERE a.character_id = c.id) AS INT) AS attunement_limit,
    -- Stacks holding more than their limit (own limit, else item limit x the character's multiplier).
    (SELECT COUNT(*) FROM character_items ci JOIN items i ON i.id = ci.item_id
      WHERE ci.character_id = c.id AND ci.location = 'slot'
        AND ci.quantity > COALESCE(ci.stack_limit, i.stack_limit * COALESCE(inv.stack_limit_multiplier, 1))) AS overfull_stacks,
    -- Attuned items that do not require attunement.
    (SELECT COUNT(*) FROM character_items ci JOIN items i ON i.id = ci.item_id
      WHERE ci.character_id = c.id AND ci.attuned AND NOT i.requires_attunement) AS needless_attunements,
    -- Total wealth in bronze, for sorting or display.
    COALESCE(inv.platinum, 0) * 10000 + COALESCE(inv.gold, 0) * 100 + COALESCE(inv.silver, 0) * 10
        + COALESCE(inv.bronze, 0) AS wealth_bronze,
    COALESCE(inv.attunement_limit, 3) AS base_attunement_limit,
    COALESCE(inv.stack_limit_multiplier, 1) AS stack_limit_multiplier
FROM characters c
LEFT JOIN character_inventories inv ON inv.character_id = c.id;

COMMIT;
