BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS creature_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_creature_categories_category UNIQUE (category)
);

CREATE TABLE IF NOT EXISTS creatures(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,
    creature_category_id UUID NOT NULL REFERENCES creature_categories(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    details JSONB DEFAULT '{"schema_version": "1.0", "type": "creature", "content": {}}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

WITH calthora_source AS (
    SELECT id
    FROM content_sources
    WHERE source_name = 'Calthora'
)
INSERT INTO creature_categories (
    source_id,
    category,
    description
)
SELECT
    calthora_source.id,
    new_category.category,
    new_category.description
FROM calthora_source
CROSS JOIN (
    VALUES
        ('amphibians', 'These are creatures that require land and water to survive, such as frogs and salamanders.'),
        ('aques','These are creatures that live in water, such as fish and whales.'),
        ('beasts', 'These are creatures that are typically wild and untamed, such as lions and bears.'),
        ('birds', 'These are creatures that have feathers and wings, such as eagles and penguins.'),
        ('celestials', 'These are creatures that are associated with light magic, such as sereaphim and unicorns.'),
        ('constructs', 'These are creatures that are artificially created, such as golems and robots.'),
        ('dragons', 'These are large, powerful creatures that are often depicted as having wings and the ability to breathe fire.'),
        ('fiends', 'These are creatures that are associated with dark magic, such as imps and skale'),
        ('monstrosities', 'These are creatures that are often grotesque or unatural appearnace, such as chimeras and hydras.'),
        ('reptiles', 'These are creatures that are typically cold-blooded and have scales, such as snakes and lizards.'),
        ('humanoids', 'These are creatures that have a human-like appearance, such as goblins and gnolls'),
        ('insects', 'These are creatures that have an exoskeleton and three body segments, such as ants and beatles'),
        ('sprina', 'These are nature spririts that are often associated with plants and the elements such as dryads and nymphs.'),
        ('undead', 'These are creatures that have died but continue to exist in some form, such as zombies and skeletons.')
) AS new_category(category, description)
ON CONFLICT (category) DO NOTHING;
COMMIT;