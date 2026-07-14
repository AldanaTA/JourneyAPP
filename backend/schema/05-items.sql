BEGIN;

CREATE TABLE IF NOT EXISTS item_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_item_categories_category UNIQUE (category)
);

CREATE TABLE IF NOT EXISTS items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID REFERENCES content_sources(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    
    details JSONB  DEFAULT '{"schema_version": "1.0", "type": "item", "content": {}}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS item_category_assignments (
    item_id UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES item_categories(id) ON DELETE CASCADE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (item_id, category_id)
);

WITH core_source AS (
    SELECT id
    FROM content_sources
    WHERE source_name = 'Core'
)
INSERT INTO item_categories (
    source_id,
    category,
    description
)
SELECT
    core_source.id,
    new_category.category,
    new_category.description
FROM core_source
CROSS JOIN (
    VALUES
        ('Weapons', 'Items used for combat, including swords, bows, and firearms.'),
        ('Armor', 'Protective gear worn to reduce damage from attacks.'),
        ('Potions', 'Consumable liquids that provide various effects when used.'),
        ('Ammunition', 'Projectiles used with ranged weapons, such as arrows, bullets, or bolts.'),
        ('Materials', 'Raw materials used for crafting, building, or other purposes.'),
        ('Alchemy_Ingredients', 'Components used in alchemical processes to create potions, elixirs, and other magical substances.'),
        ('Blacksmithing_Components', 'Materials and components used in blacksmithing to create weapons, armor, and other metal items.'),
        ('Cooking_Ingredients', 'Ingredients used in cooking to prepare meals, beverages, and other consumables.'),
        ('Magic_Items', 'Rare and powerful items with unique properties.'),
        ('Tools', 'Items used for crafting, gathering, or other non-combat purposes.'),
        ('Miscellaneous', 'Items that do not fit into the other categories.')
) AS new_category(category, description)
ON CONFLICT (category) DO NOTHING;

COMMIT;