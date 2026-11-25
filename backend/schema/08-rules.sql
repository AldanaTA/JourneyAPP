BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Rules are stored as structured content so they can be read, searched, linked, edited in the web app,
-- and later generated back into chapters and books.
--
--   books -> book_chapters (order) -> rule_chapters -> rule_sections (tree, in document order)
--
-- Section bodies are Markdown with two custom link targets:
--   [Battle Tactics](trait:<uuid>)        entity reference (trait, power, item, race, job, creature)
--   ![East Continent map](asset:<uuid>)   image stored in `assets`
-- An entity reference alone on its own line renders as an inline card; inside a sentence it renders as
-- a link. rule_section_entities / rule_section_assets mirror these links so they can be checked,
-- queried ("where is this trait mentioned?"), and kept from dangling.

-- ----------------------------
-- Assets (images)
-- ----------------------------

-- Files live in storage (a Docker volume served by the API to start with); rows hold metadata and the
-- path inside that storage. Content refers to assets by id, never by URL, so storage can move freely.
CREATE TABLE IF NOT EXISTS assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    -- Path relative to the storage root, e.g. "calthora/3f2a...c1.png".
    file_path TEXT NOT NULL,
    original_filename TEXT,
    content_type TEXT NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size >= 0),
    width INT CHECK (width > 0),
    height INT CHECK (height > 0),
    alt_text TEXT,
    -- Hex SHA-256 of the file, so re-importing the same image reuses the existing asset.
    sha256 TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_assets_file_path UNIQUE (file_path),
    CONSTRAINT uq_assets_source_sha256 UNIQUE (source_id, sha256)
);

-- ----------------------------
-- Books and chapters
-- ----------------------------

CREATE TABLE IF NOT EXISTS books (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    title TEXT NOT NULL,
    title_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(title))) STORED,
    description TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_books_title_key_unique
ON books (title_key);

-- A chapter belongs to one source. A book lists chapters through book_chapters, so a setting book can
-- include Core chapters by reference instead of copying them.
CREATE TABLE IF NOT EXISTS rule_chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    title TEXT NOT NULL,
    -- Stable identity used by the importer: the document name without its number, lowercased
    -- ("04_Combat(Journey 100).docx" -> "combat"). Re-imports update the chapter with this key.
    chapter_key TEXT NOT NULL,
    -- The number from the file name ("04_..." -> 4); NULL for chapters created in the web app.
    number INT CHECK (number >= 0),
    description TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_rule_chapters_source_key UNIQUE (source_id, chapter_key)
);

CREATE TABLE IF NOT EXISTS book_chapters (
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES rule_chapters(id) ON DELETE CASCADE,
    sort_order INT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (book_id, chapter_id),
    -- Deferrable so chapters can be reordered (positions swapped) inside one transaction.
    CONSTRAINT uq_book_chapters_order UNIQUE (book_id, sort_order) DEFERRABLE INITIALLY DEFERRED
);

-- ----------------------------
-- Sections
-- ----------------------------

-- One row per heading (H1-H6) with the content under it. parent_id builds the tree; sort_order is the
-- position within the chapter in reading order, so a whole chapter renders with ORDER BY sort_order.
CREATE TABLE IF NOT EXISTS rule_sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL REFERENCES rule_chapters(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES rule_sections(id) ON DELETE CASCADE,

    title TEXT NOT NULL,
    level INT NOT NULL CHECK (level BETWEEN 1 AND 6),
    -- URL-friendly id for deep links, unique within the chapter ("tp-brackets", "tp-brackets-2").
    slug TEXT NOT NULL,
    -- Markdown (see the notes at the top of this file).
    body TEXT NOT NULL DEFAULT '',
    sort_order INT NOT NULL,

    -- Full-text search over the title and body.
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A')
        || setweight(to_tsvector('english', coalesce(body, '')), 'B')
    ) STORED,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_rule_sections_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT uq_rule_sections_slug UNIQUE (chapter_id, slug),
    CONSTRAINT uq_rule_sections_order UNIQUE (chapter_id, sort_order) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_rule_sections_parent_id
ON rule_sections (parent_id);

CREATE INDEX IF NOT EXISTS idx_rule_sections_search
ON rule_sections USING GIN (search_vector);

-- Previous versions of a section, written whenever it is edited (web app undo and change history).
CREATE TABLE IF NOT EXISTS rule_section_revisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id UUID NOT NULL REFERENCES rule_sections(id) ON DELETE CASCADE,

    title TEXT NOT NULL,
    body TEXT NOT NULL,
    -- Who made the change that replaced this version. NULL for system changes such as imports, which
    -- say so in `note` (e.g. "Imported from 04_Combat(Journey 100).docx").
    edited_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    note TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rule_section_revisions_section_id
ON rule_section_revisions (section_id, created_at DESC);

-- ----------------------------
-- Links from sections to entities and assets
-- ----------------------------

-- Every entity reference in a section body. Exactly one entity column is set; deleting the entity
-- removes the row (the body then shows the reference as missing until it is edited).
CREATE TABLE IF NOT EXISTS rule_section_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id UUID NOT NULL REFERENCES rule_sections(id) ON DELETE CASCADE,

    trait_id UUID REFERENCES traits(id) ON DELETE CASCADE,
    power_id UUID REFERENCES powers(id) ON DELETE CASCADE,
    item_id UUID REFERENCES items(id) ON DELETE CASCADE,
    race_id UUID REFERENCES races(id) ON DELETE CASCADE,
    job_id UUID REFERENCES jobs(id) ON DELETE CASCADE,
    creature_id UUID REFERENCES creatures(id) ON DELETE CASCADE,

    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_rule_section_entities_one_entity CHECK (
        num_nonnulls(trait_id, power_id, item_id, race_id, job_id, creature_id) = 1
    )
);

CREATE INDEX IF NOT EXISTS idx_rule_section_entities_section_id ON rule_section_entities (section_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_rule_section_entities_trait_id ON rule_section_entities (trait_id);
CREATE INDEX IF NOT EXISTS idx_rule_section_entities_power_id ON rule_section_entities (power_id);
CREATE INDEX IF NOT EXISTS idx_rule_section_entities_item_id ON rule_section_entities (item_id);
CREATE INDEX IF NOT EXISTS idx_rule_section_entities_race_id ON rule_section_entities (race_id);
CREATE INDEX IF NOT EXISTS idx_rule_section_entities_job_id ON rule_section_entities (job_id);
CREATE INDEX IF NOT EXISTS idx_rule_section_entities_creature_id ON rule_section_entities (creature_id);

-- Every image in a section body. RESTRICT keeps an image from being deleted while a section shows it.
CREATE TABLE IF NOT EXISTS rule_section_assets (
    section_id UUID NOT NULL REFERENCES rule_sections(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES assets(id) ON DELETE RESTRICT,
    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (section_id, asset_id)
);

CREATE INDEX IF NOT EXISTS idx_rule_section_assets_asset_id
ON rule_section_assets (asset_id);

-- ----------------------------
-- Default books
-- ----------------------------

INSERT INTO books (source_id, title, description)
SELECT s.id, b.title, b.description
FROM content_sources s
JOIN (
    VALUES
        ('Core', 'Journey 100', 'The core rules of Journey.'),
        ('Calthora', 'Calthora Setting Book', 'The Calthora setting: world lore, atlas, and player options.')
) AS b(source_name, title, description) ON b.source_name = s.source_name
ON CONFLICT (title_key) DO NOTHING;

COMMIT;
