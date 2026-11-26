BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Player characters, following the Character Creation chapter and the Physical / Magic / Balanced
-- character sheets.
--
--   character_sheets   starting attributes (Physical, Magic, Balanced)
--   skills             the skill list per source, combat or life
--   characters ──< character_races       one race, or several for a mixed race
--              ──< character_attributes  EXP and job points spent on Body / Strength / Speed / Magic
--              ──< character_skills      creation swaps, EXP, failures, modifiers
--              ──< character_traits      racial, job (free), and purchased traits with their level
--              ──< character_powers      purchased powers with their level
--              ──< character_exp_awards  EXP gained from quests (and corrections)
--
-- Two point pools:
--   EXP          starting_exp + awards. Spent on attributes, skills, traits, and powers.
--   Job points   job_points (50). Spent on the job's free traits; whatever is left can go to attributes
--                or skills instead. Never on purchased traits or powers.
-- Every row records how much of each pool it used, so a character's budget is a sum (see
-- character_point_budget) and no separate ledger can drift out of step.
--
-- What the database enforces: values in range, one row per thing, a job trait only while the character
-- has that job, a racial trait only from one of the character's races. What the API enforces (and
-- character_point_budget reports): totals within budget, racial traits adding up to 50 EXP, creation skill
-- swaps balancing within combat and within life skills, and that a picked trait is offered by the
-- race or job (race_traits / job_traits are rebuilt on every re-import, so they cannot be foreign keys here).

-- ----------------------------
-- Enums
-- ----------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'skill_category') THEN
        -- Creation swaps only move points between skills of the same category.
        CREATE TYPE skill_category AS ENUM ('combat', 'life');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'character_attribute') THEN
        CREATE TYPE character_attribute AS ENUM ('body', 'strength', 'speed', 'magic');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'character_trait_source') THEN
        -- racial: granted by one of the character's races
        -- job: picked from the job's list with job points
        -- purchased: bought with EXP (any trait, including job traits bought later)
        CREATE TYPE character_trait_source AS ENUM ('racial', 'job', 'purchased');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'character_status') THEN
        -- draft: being created, rules may be unmet; active: finished and in play; retired: kept for history
        CREATE TYPE character_status AS ENUM ('draft', 'active', 'retired');
    END IF;
END $$;

-- ----------------------------
-- Character sheets
-- ----------------------------

-- The three starting sheets. Each spreads 100 attribute points differently.
CREATE TABLE IF NOT EXISTS character_sheets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,
    description TEXT NOT NULL DEFAULT '',

    base_body INT NOT NULL CHECK (base_body >= 0),
    base_strength INT NOT NULL CHECK (base_strength >= 0),
    base_speed INT NOT NULL CHECK (base_speed >= 0),
    base_magic INT NOT NULL CHECK (base_magic >= 0),

    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_character_sheets_name UNIQUE (source_id, name_key)
);

INSERT INTO character_sheets (source_id, name, description, base_body, base_strength, base_speed, base_magic, sort_order)
SELECT s.id, v.name, v.description, v.body, v.strength, v.speed, v.magic, v.sort_order
FROM content_sources s
JOIN (
    VALUES
        ('Physical', 'More points in Body and Strength; leans towards melee combatants.', 35, 35, 20, 10, 1),
        ('Magic', 'Most points in Magic; leans towards magic combatants.', 25, 10, 10, 55, 2),
        ('Balanced', 'An even spread of attributes; the most flexible of the three.', 30, 20, 30, 20, 3)
) AS v(name, description, body, strength, speed, magic, sort_order) ON s.source_name = 'Core'
ON CONFLICT (source_id, name_key) DO NOTHING;

-- ----------------------------
-- Skills
-- ----------------------------

-- Skills can differ by setting, so they belong to a source like other content.
CREATE TABLE IF NOT EXISTS skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,
    category skill_category NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    -- Every skill starts at 40% (minimum 5%).
    base_rating INT NOT NULL DEFAULT 40 CHECK (base_rating >= 5),
    -- Weapon skills name the weapon type they are rolled for (Bows -> Bow, ...).
    weapon_type_id UUID REFERENCES weapon_types(id) ON DELETE SET NULL,

    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT uq_skills_name UNIQUE (source_id, name_key)
);

INSERT INTO skills (source_id, name, category, weapon_type_id, sort_order)
SELECT s.id, v.name, v.category::skill_category, wt.id, v.sort_order
FROM content_sources s
JOIN (
    VALUES
        ('First Aid', 'combat', NULL, 1),
        ('Dodge', 'combat', NULL, 2),
        ('Resilience', 'combat', NULL, 3),
        ('Mind', 'combat', NULL, 4),
        ('Self', 'combat', NULL, 5),
        ('Destruction Magic', 'combat', NULL, 6),
        ('Support Magic', 'combat', NULL, 7),
        ('Sabotage Magic', 'combat', NULL, 8),
        ('Bows', 'combat', 'bow', 9),
        ('Colossal Weapons', 'combat', 'colossal', 10),
        ('Firearms', 'combat', 'firearm', 11),
        ('Brawl', 'combat', NULL, 12),
        ('Martial Weapons', 'combat', 'martial', 13),
        ('Simple Weapons', 'combat', 'simple', 14),
        ('Athletics', 'life', NULL, 101),
        ('Alchemy', 'life', NULL, 102),
        ('Animal Handling', 'life', NULL, 103),
        ('Contortion', 'life', NULL, 104),
        ('Convince', 'life', NULL, 105),
        ('Cooking', 'life', NULL, 106),
        ('Insight', 'life', NULL, 107),
        ('Intelligence', 'life', NULL, 108),
        ('Investigation', 'life', NULL, 109),
        ('Navigation', 'life', NULL, 110),
        ('Parkour', 'life', NULL, 111),
        ('Perception', 'life', NULL, 112),
        ('Performance', 'life', NULL, 113),
        ('Runes', 'life', NULL, 114),
        ('Sleight of Hand', 'life', NULL, 115),
        ('Smithing', 'life', NULL, 116),
        ('Stealth', 'life', NULL, 117),
        ('Survival', 'life', NULL, 118)
) AS v(name, category, weapon_type_key, sort_order) ON s.source_name = 'Core'
LEFT JOIN weapon_types wt ON wt.name_key = v.weapon_type_key
ON CONFLICT (source_id, name_key) DO NOTHING;

-- ----------------------------
-- Characters
-- ----------------------------

CREATE TABLE IF NOT EXISTS characters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status character_status NOT NULL DEFAULT 'draft',

    -- Identity
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    motive TEXT NOT NULL DEFAULT '',
    ideals TEXT NOT NULL DEFAULT '',
    strengths TEXT NOT NULL DEFAULT '',
    flaws TEXT NOT NULL DEFAULT '',
    -- Languages learned in play (about a month of study each), on top of the race's language set.
    learned_languages TEXT NOT NULL DEFAULT '',
    history TEXT NOT NULL DEFAULT '',
    important_characters TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',

    -- Choices. Nullable while a draft is being filled in; required once the character is active.
    -- RESTRICT: a sheet or job in use by a character cannot be deleted out from under it.
    character_sheet_id UUID REFERENCES character_sheets(id) ON DELETE RESTRICT,
    job_id UUID REFERENCES jobs(id) ON DELETE RESTRICT,
    -- Chosen within the size range of one of the character's races (checked by the API).
    size size,
    -- The race whose language set the character speaks (races.languages, e.g. "Common and Dwarvish").
    -- Must be one of the character's races; a mixed race picks one of them (see fk_characters_language_race).
    language_race_id UUID,

    -- Point pools (settings may change the defaults).
    starting_exp INT NOT NULL DEFAULT 150 CHECK (starting_exp >= 0),
    job_points INT NOT NULL DEFAULT 50 CHECK (job_points >= 0),

    -- Capacities: current values during play. Maximums are derived (see character_stats); the bonus
    -- columns hold extra maximum from traits, items, or the storyteller.
    current_hp INT,
    current_wp INT,
    current_ep INT,
    current_mp INT,
    temp_hp INT NOT NULL DEFAULT 0 CHECK (temp_hp >= 0),
    hp_bonus INT NOT NULL DEFAULT 0,
    wp_bonus INT NOT NULL DEFAULT 0,
    ep_bonus INT NOT NULL DEFAULT 0,
    mp_bonus INT NOT NULL DEFAULT 0,

    -- Bonuses to derived combat stats ("Bonus" column of the Action Bar).
    physical_damage_bonus INT NOT NULL DEFAULT 0,
    magic_damage_bonus INT NOT NULL DEFAULT 0,
    priority_bonus INT NOT NULL DEFAULT 0,
    movement_bonus INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Target of the job-trait foreign key below: a job trait must belong to the character's current job.
    CONSTRAINT uq_characters_id_job UNIQUE (id, job_id),
    CONSTRAINT chk_characters_complete CHECK (
        status = 'draft'
        OR (character_sheet_id IS NOT NULL AND job_id IS NOT NULL AND size IS NOT NULL AND language_race_id IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_characters_user_id
ON characters (user_id);

-- ----------------------------
-- Races (one, or several for a mixed race)
-- ----------------------------

CREATE TABLE IF NOT EXISTS character_races (
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    race_id UUID NOT NULL REFERENCES races(id) ON DELETE RESTRICT,
    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (character_id, race_id)
);

CREATE INDEX IF NOT EXISTS idx_character_races_race_id
ON character_races (race_id);

-- The language set must come from one of the character's own races. Added here because character_races is
-- created after characters. Removing that race clears the choice (only language_race_id is set to NULL);
-- an active character must pick another language race first, since chk_characters_complete requires one.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_characters_language_race') THEN
        ALTER TABLE characters
            ADD CONSTRAINT fk_characters_language_race FOREIGN KEY (id, language_race_id)
            REFERENCES character_races (character_id, race_id)
            ON DELETE SET NULL (language_race_id);
    END IF;
END $$;

-- ----------------------------
-- Attributes
-- ----------------------------

-- Value = sheet base + exp_spent + job_points_spent + bonus + modifier (1 point = +1).
-- A character gets a row per attribute when created.
CREATE TABLE IF NOT EXISTS character_attributes (
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    attribute character_attribute NOT NULL,

    exp_spent INT NOT NULL DEFAULT 0 CHECK (exp_spent >= 0),
    -- Leftover job points put into this attribute.
    job_points_spent INT NOT NULL DEFAULT 0 CHECK (job_points_spent >= 0),
    -- Permanent bonus from traits or items ("Bonus" on the sheet).
    bonus INT NOT NULL DEFAULT 0,
    -- Temporary change from powers or status conditions ("Modify" on the sheet).
    modifier INT NOT NULL DEFAULT 0,

    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (character_id, attribute)
);

-- ----------------------------
-- Skills
-- ----------------------------

-- Rating = base_rating + creation_adjustment + exp_spent + job_points_spent + permanent_modifier
--          + temporary_modifier, never below 5%.
-- A character gets a row per skill of its setting when created.
CREATE TABLE IF NOT EXISTS character_skills (
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,

    -- Creation swap: lower a skill by up to 20 to raise another of the same category by the same amount.
    -- Within each category the adjustments add up to 0 (checked by the API; see character_point_budget).
    creation_adjustment INT NOT NULL DEFAULT 0 CHECK (creation_adjustment >= -20),
    exp_spent INT NOT NULL DEFAULT 0 CHECK (exp_spent >= 0),
    job_points_spent INT NOT NULL DEFAULT 0 CHECK (job_points_spent >= 0),
    -- Marks from failures (1) and fumbles (2); 3 are erased on a rest to raise the rating by 1d6.
    failures INT NOT NULL DEFAULT 0 CHECK (failures >= 0),
    -- Lasting changes: 1d6 raises from failures, skill books, traits ("Permanent Modification").
    permanent_modifier INT NOT NULL DEFAULT 0,
    -- Changes from equipment, powers, or status conditions ("Temporary Modification").
    temporary_modifier INT NOT NULL DEFAULT 0,

    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (character_id, skill_id)
);

-- ----------------------------
-- Traits
-- ----------------------------

-- One row per trait the character has, with where it came from and what it cost.
--   racial:    race_id set; costs nothing (the race's traits come with it). For a mixed race the chosen
--              racial traits' purchase costs add up to 50 EXP.
--   job:       job_id set to the character's job; job_points_spent = the trait's purchase cost.
--   purchased: exp_spent includes the purchase cost.
-- Levelling up any trait (half its EXP cost per level, max level 6) adds to exp_spent.
--
-- Players edit traits for their character instead of the app levelling them automatically: the columns
-- under "Character's version" override the compendium trait for this character only. NULL means "use the
-- compendium value", so untouched fields keep up with compendium fixes and clearing a field resets it.
-- character_trait_details shows the result. Editing costs here does not change exp_spent; the API or the
-- player records what was actually spent.
CREATE TABLE IF NOT EXISTS character_traits (
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    trait_id UUID NOT NULL REFERENCES traits(id) ON DELETE RESTRICT,
    source character_trait_source NOT NULL,

    race_id UUID,
    job_id UUID,

    lvl INT NOT NULL DEFAULT 1 CHECK (lvl BETWEEN 1 AND 6),
    exp_spent INT NOT NULL DEFAULT 0 CHECK (exp_spent >= 0),
    job_points_spent INT NOT NULL DEFAULT 0 CHECK (job_points_spent >= 0),
    -- The player's own notes: chosen options, summons, uses left, anything to keep track of.
    notes TEXT NOT NULL DEFAULT '',

    -- Character's version (NULL = compendium value; see above).
    trait_name TEXT,
    purchase_cost INT CHECK (purchase_cost >= 0),
    lvl_up_cost INT CHECK (lvl_up_cost >= 0),
    trait_desc TEXT,
    trait_uses INT CHECK (trait_uses >= 0),
    mp_cost INT CHECK (mp_cost >= 0),
    ep_cost INT CHECK (ep_cost >= 0),
    tp_cost INT CHECK (tp_cost >= 0),
    trait_effect TEXT,
    lvl_up_effect TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (character_id, trait_id),

    -- Removing a race from the character removes the traits it granted.
    CONSTRAINT fk_character_traits_race FOREIGN KEY (character_id, race_id)
        REFERENCES character_races (character_id, race_id) ON DELETE CASCADE,
    -- A job trait only while the character has that job: changing jobs requires removing them first.
    CONSTRAINT fk_character_traits_job FOREIGN KEY (character_id, job_id)
        REFERENCES characters (id, job_id),

    CONSTRAINT chk_character_traits_racial CHECK ((source = 'racial') = (race_id IS NOT NULL)),
    CONSTRAINT chk_character_traits_job CHECK ((source = 'job') = (job_id IS NOT NULL)),
    -- Job points only pay for job traits.
    CONSTRAINT chk_character_traits_job_points CHECK (source = 'job' OR job_points_spent = 0)
);

CREATE INDEX IF NOT EXISTS idx_character_traits_trait_id
ON character_traits (trait_id);

-- ----------------------------
-- Powers
-- ----------------------------

-- exp_spent = 10 to learn the power + 5 * LVL for each level gained.
--
-- Like traits, players edit powers for their character: the "Character's version" columns override the
-- compendium power (NULL = compendium value); character_power_details shows the result. To remove a cost
-- the compendium power has, set it to 0 rather than NULL.
CREATE TABLE IF NOT EXISTS character_powers (
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
    power_id UUID NOT NULL REFERENCES powers(id) ON DELETE RESTRICT,

    lvl INT NOT NULL DEFAULT 1 CHECK (lvl >= 1),
    exp_spent INT NOT NULL DEFAULT 0 CHECK (exp_spent >= 0),
    -- The player's own notes, e.g. the summons from Army of the Dead or Raise Dead and their HP.
    notes TEXT NOT NULL DEFAULT '',

    -- Character's version (NULL = compendium value; see above).
    name TEXT,
    -- Text rather than the power_type enum, because powers.type is varchar in some databases.
    type TEXT CHECK (type IN ('Destruction', 'Support', 'Sabotage', 'Utility')),
    tp_cost INT CHECK (tp_cost >= 0),
    hp_cost INT CHECK (hp_cost >= 0),
    mp_cost INT CHECK (mp_cost >= 0),
    ep_cost INT CHECK (ep_cost >= 0),
    material_components TEXT,
    verbal BOOLEAN,
    sight BOOLEAN,
    somatic BOOLEAN,
    is_distinct BOOLEAN,
    concentration BOOLEAN,
    range TEXT,
    area TEXT,
    duration TEXT,
    effect TEXT,
    empower_effect TEXT,
    lvl_up_effect TEXT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (character_id, power_id)
);

CREATE INDEX IF NOT EXISTS idx_character_powers_power_id
ON character_powers (power_id);

-- ----------------------------
-- EXP awards
-- ----------------------------

-- EXP gained after creation (quests, training). Negative amounts correct mistakes.
CREATE TABLE IF NOT EXISTS character_exp_awards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    character_id UUID NOT NULL REFERENCES characters(id) ON DELETE CASCADE,

    amount INT NOT NULL CHECK (amount <> 0),
    reason TEXT NOT NULL DEFAULT '',
    -- The storyteller (or player) who gave it.
    awarded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_character_exp_awards_character_id
ON character_exp_awards (character_id, created_at);

-- ----------------------------
-- Views
-- ----------------------------

-- Where each character's points went, and whether creation rules are met.
-- The API checks these before saving a change and before a draft becomes active.
CREATE OR REPLACE VIEW character_point_budget AS
SELECT
    c.id AS character_id,

    c.starting_exp + COALESCE(awards.total, 0) AS total_exp,
    COALESCE(attr.exp, 0) AS attribute_exp,
    COALESCE(sk.exp, 0) AS skill_exp,
    COALESCE(tr.exp, 0) AS trait_exp,
    COALESCE(pw.exp, 0) AS power_exp,
    c.starting_exp + COALESCE(awards.total, 0)
        - COALESCE(attr.exp, 0) - COALESCE(sk.exp, 0) - COALESCE(tr.exp, 0) - COALESCE(pw.exp, 0) AS exp_remaining,

    c.job_points,
    COALESCE(tr.job_points, 0) AS job_trait_points,
    COALESCE(attr.job_points, 0) + COALESCE(sk.job_points, 0) AS job_points_on_stats,
    c.job_points - COALESCE(tr.job_points, 0) - COALESCE(attr.job_points, 0) - COALESCE(sk.job_points, 0)
        AS job_points_remaining,

    (SELECT COUNT(*) FROM character_races cr WHERE cr.character_id = c.id) AS race_count,
    COALESCE(tr.racial_cost, 0) AS racial_trait_cost,

    -- Creation swaps must balance (0) within each category.
    COALESCE(sk.combat_adjustment, 0) AS combat_skill_adjustment,
    COALESCE(sk.life_adjustment, 0) AS life_skill_adjustment
FROM characters c
LEFT JOIN (
    SELECT character_id, SUM(amount) AS total
    FROM character_exp_awards GROUP BY character_id
) awards ON awards.character_id = c.id
LEFT JOIN (
    SELECT character_id, SUM(exp_spent) AS exp, SUM(job_points_spent) AS job_points
    FROM character_attributes GROUP BY character_id
) attr ON attr.character_id = c.id
LEFT JOIN (
    SELECT cs.character_id,
           SUM(cs.exp_spent) AS exp,
           SUM(cs.job_points_spent) AS job_points,
           SUM(cs.creation_adjustment) FILTER (WHERE s.category = 'combat') AS combat_adjustment,
           SUM(cs.creation_adjustment) FILTER (WHERE s.category = 'life') AS life_adjustment
    FROM character_skills cs JOIN skills s ON s.id = cs.skill_id
    GROUP BY cs.character_id
) sk ON sk.character_id = c.id
LEFT JOIN (
    SELECT ct.character_id,
           SUM(ct.exp_spent) AS exp,
           SUM(ct.job_points_spent) AS job_points,
           SUM(COALESCE(ct.purchase_cost, t.purchase_cost)) FILTER (WHERE ct.source = 'racial') AS racial_cost
    FROM character_traits ct JOIN traits t ON t.id = ct.trait_id
    GROUP BY ct.character_id
) tr ON tr.character_id = c.id
LEFT JOIN (
    SELECT character_id, SUM(exp_spent) AS exp
    FROM character_powers GROUP BY character_id
) pw ON pw.character_id = c.id;

-- Attribute values and the stats derived from them, using the character sheet formulas.
CREATE OR REPLACE VIEW character_stats AS
WITH attrs AS (
    SELECT
        c.id AS character_id,
        COALESCE(sh.base_body, 0) + COALESCE(SUM(a.exp_spent + a.job_points_spent + a.bonus + a.modifier)
            FILTER (WHERE a.attribute = 'body'), 0) AS body,
        COALESCE(sh.base_strength, 0) + COALESCE(SUM(a.exp_spent + a.job_points_spent + a.bonus + a.modifier)
            FILTER (WHERE a.attribute = 'strength'), 0) AS strength,
        COALESCE(sh.base_speed, 0) + COALESCE(SUM(a.exp_spent + a.job_points_spent + a.bonus + a.modifier)
            FILTER (WHERE a.attribute = 'speed'), 0) AS speed,
        COALESCE(sh.base_magic, 0) + COALESCE(SUM(a.exp_spent + a.job_points_spent + a.bonus + a.modifier)
            FILTER (WHERE a.attribute = 'magic'), 0) AS magic
    FROM characters c
    LEFT JOIN character_sheets sh ON sh.id = c.character_sheet_id
    LEFT JOIN character_attributes a ON a.character_id = c.id
    GROUP BY c.id, sh.base_body, sh.base_strength, sh.base_speed, sh.base_magic
)
SELECT
    c.id AS character_id,
    attrs.body,
    attrs.strength,
    attrs.speed,
    attrs.magic,

    attrs.body + c.hp_bonus AS max_hp,
    attrs.body / 2 + c.wp_bonus AS max_wp,
    attrs.strength + c.ep_bonus AS max_ep,
    attrs.magic + c.mp_bonus AS max_mp,

    (attrs.strength / 15) * 2 + c.physical_damage_bonus AS physical_damage,
    (attrs.magic / 15) * 2 + c.magic_damage_bonus AS magic_damage,
    attrs.speed / 15 + c.priority_bonus AS priority,
    -- Movement starts from the slowest of the character's races (the API can store a chosen race's
    -- movement as a bonus instead if a mixed race should use another).
    COALESCE((SELECT MIN(r.movement) FROM character_races cr JOIN races r ON r.id = cr.race_id
              WHERE cr.character_id = c.id), 30)
        + (attrs.speed / 15) * 5 + c.movement_bonus AS movement_ft
FROM characters c
JOIN attrs ON attrs.character_id = c.id;

-- The languages a character speaks: the chosen race's language set plus any learned since.
CREATE OR REPLACE VIEW character_languages AS
SELECT
    c.id AS character_id,
    c.language_race_id,
    r.race_name AS language_race_name,
    r.languages AS race_languages,
    c.learned_languages
FROM characters c
LEFT JOIN races r ON r.id = c.language_race_id;

-- Each character's traits as they apply to that character: their edits, otherwise the compendium trait.
-- is_customized is true when any field is edited, so the sheet can mark it and offer "reset".
CREATE OR REPLACE VIEW character_trait_details AS
SELECT
    ct.character_id,
    ct.trait_id,
    ct.source,
    ct.race_id,
    ct.job_id,
    ct.lvl,
    ct.exp_spent,
    ct.job_points_spent,
    ct.notes,
    COALESCE(ct.trait_name, t.trait_name) AS trait_name,
    COALESCE(ct.purchase_cost, t.purchase_cost) AS purchase_cost,
    COALESCE(ct.lvl_up_cost, t.lvl_up_cost) AS lvl_up_cost,
    COALESCE(ct.trait_desc, t.trait_desc) AS trait_desc,
    COALESCE(ct.trait_uses, t.trait_uses) AS trait_uses,
    COALESCE(ct.mp_cost, t.mp_cost) AS mp_cost,
    COALESCE(ct.ep_cost, t.ep_cost) AS ep_cost,
    COALESCE(ct.tp_cost, t.tp_cost) AS tp_cost,
    COALESCE(ct.trait_effect, t.trait_effect) AS trait_effect,
    COALESCE(ct.lvl_up_effect, t.lvl_up_effect) AS lvl_up_effect,
    num_nonnulls(ct.trait_name, ct.purchase_cost, ct.lvl_up_cost, ct.trait_desc, ct.trait_uses, ct.mp_cost,
                 ct.ep_cost, ct.tp_cost, ct.trait_effect, ct.lvl_up_effect) > 0 AS is_customized
FROM character_traits ct
JOIN traits t ON t.id = ct.trait_id;

-- Each character's powers as they apply to that character: their edits, otherwise the compendium power.
CREATE OR REPLACE VIEW character_power_details AS
SELECT
    cp.character_id,
    cp.power_id,
    cp.lvl,
    cp.exp_spent,
    cp.notes,
    COALESCE(cp.name, p.name) AS name,
    COALESCE(cp.type, p.type::TEXT) AS type,
    COALESCE(cp.tp_cost, p.tp_cost) AS tp_cost,
    COALESCE(cp.hp_cost, p.hp_cost) AS hp_cost,
    COALESCE(cp.mp_cost, p.mp_cost) AS mp_cost,
    COALESCE(cp.ep_cost, p.ep_cost) AS ep_cost,
    COALESCE(cp.material_components, p.material_components) AS material_components,
    COALESCE(cp.verbal, p.verbal) AS verbal,
    COALESCE(cp.sight, p.sight) AS sight,
    COALESCE(cp.somatic, p.somatic) AS somatic,
    COALESCE(cp.is_distinct, p.is_distinct) AS is_distinct,
    COALESCE(cp.concentration, p.concentration) AS concentration,
    COALESCE(cp.range, p.range) AS range,
    COALESCE(cp.area, p.area) AS area,
    COALESCE(cp.duration, p.duration) AS duration,
    COALESCE(cp.effect, p.effect) AS effect,
    COALESCE(cp.empower_effect, p.empower_effect) AS empower_effect,
    COALESCE(cp.lvl_up_effect, p.lvl_up_effect) AS lvl_up_effect,
    num_nonnulls(cp.name, cp.type, cp.tp_cost, cp.hp_cost, cp.mp_cost, cp.ep_cost, cp.material_components,
                 cp.verbal, cp.sight, cp.somatic, cp.is_distinct, cp.concentration, cp.range, cp.area,
                 cp.duration, cp.effect, cp.empower_effect, cp.lvl_up_effect) > 0 AS is_customized
FROM character_powers cp
JOIN powers p ON p.id = cp.power_id;

-- Skill ratings with every modifier applied.
CREATE OR REPLACE VIEW character_skill_ratings AS
SELECT
    cs.character_id,
    cs.skill_id,
    s.name,
    s.category,
    GREATEST(5, s.base_rating + cs.creation_adjustment + cs.exp_spent + cs.job_points_spent
                + cs.permanent_modifier + cs.temporary_modifier) AS rating,
    cs.failures
FROM character_skills cs
JOIN skills s ON s.id = cs.skill_id;

COMMIT;
