BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Campaigns: a storyteller's game with its players, its own content, rules, notes, and campaign versions of the
-- players' characters.
--
-- A campaign IS a content source of kind 'campaign' (01-content_source.sql), so everything content already has
-- works for it unchanged:
--   name, description, owner      content_sources.source_name / source_desc / owner_user_id
--   players and co-storytellers   source_members: owner = the storyteller, editor = co-storyteller,
--                                 viewer = player (a source's members can read it whatever its visibility)
--   its own powers, traits,       rows in those tables with source_id = the campaign (as for any book)
--   creatures, items (and races, jobs)
--   its own rules                 books / rule_chapters / rule_sections with source_id = the campaign
--   images for rules and notes    assets with source_id = the campaign
-- Deleting the campaign's content source deletes the campaign and everything in it (run it after
-- SET CONSTRAINTS ALL DEFERRED, see the character versions below).
--
-- This file adds what only campaigns have:
--   campaigns                       the join links (players, co-storytellers), one row per campaign source
--   campaign_allowed_sources        books the campaign allows (core is always allowed and is not listed)
--   campaign_note_folders / notes   the storytellers' notes, in folders, written in the rules' markdown format
--   characters.campaign_id          campaign versions of characters
--   campaign_messages               chat, rolls, and the combat log, with whispers (storytellers see them all)

-- ----------------------------
-- Campaigns
-- ----------------------------

-- Lets campaigns point at their content source and know it is a campaign: (id, kind) must match a source.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_content_sources_id_kind') THEN
        ALTER TABLE content_sources ADD CONSTRAINT uq_content_sources_id_kind UNIQUE (id, kind);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS campaigns (
    -- The campaign's content source (same id).
    id UUID PRIMARY KEY,
    kind source_kind NOT NULL DEFAULT 'campaign' CHECK (kind = 'campaign'),

    -- The game link: <app>/join/<join_code>. Anyone signed in with the link joins as a player while joining is
    -- open. A new code (the API's "reset link") makes old links stop working.
    join_code TEXT NOT NULL DEFAULT translate(encode(gen_random_bytes(12), 'base64'), '+/', '-_'),
    join_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- A second link, <app>/join/<storyteller_join_code>, joins as a co-storyteller. Only the owner sees it, and it
    -- works even while joining is closed for players.
    storyteller_join_code TEXT NOT NULL DEFAULT translate(encode(gen_random_bytes(12), 'base64'), '+/', '-_'),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Deleting the source deletes the campaign.
    CONSTRAINT fk_campaigns_source FOREIGN KEY (id, kind)
        REFERENCES content_sources (id, kind) ON DELETE CASCADE,
    CONSTRAINT uq_campaigns_join_code UNIQUE (join_code)
);

-- For databases created before the co-storyteller link existed.
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS storyteller_join_code TEXT NOT NULL
    DEFAULT translate(encode(gen_random_bytes(12), 'base64'), '+/', '-_');
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_campaigns_storyteller_join_code') THEN
        ALTER TABLE campaigns ADD CONSTRAINT uq_campaigns_storyteller_join_code UNIQUE (storyteller_join_code);
    END IF;
END $$;

-- ----------------------------
-- Allowed sources
-- ----------------------------

-- The books (and other campaigns' content, if the storyteller can read it) players may build characters from.
-- The core rules and the campaign's own content are always allowed and are never listed here (the API refuses
-- them).
CREATE TABLE IF NOT EXISTS campaign_allowed_sources (
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES content_sources(id) ON DELETE CASCADE,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    PRIMARY KEY (campaign_id, source_id),
    CONSTRAINT chk_campaign_allowed_sources_not_self CHECK (campaign_id <> source_id)
);

CREATE INDEX IF NOT EXISTS idx_campaign_allowed_sources_source_id
ON campaign_allowed_sources (source_id);

-- ----------------------------
-- Notes
-- ----------------------------

-- Campaign notes are the storytellers' (owner and co-storytellers): players never see them. Players keep their own
-- notes on their characters (character, trait, power, and item notes).
--
-- Folders nest (parent_id).
CREATE TABLE IF NOT EXISTS campaign_note_folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    parent_id UUID,

    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    -- SET NULL: a storyteller who leaves or is deleted does not take the campaign's folders with them.
    author_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Target of the folder keys below: a folder or note can only sit in a folder of the same campaign.
    CONSTRAINT uq_campaign_note_folders_campaign UNIQUE (campaign_id, id),
    -- Deleting a folder deletes what is in it.
    CONSTRAINT fk_campaign_note_folders_parent FOREIGN KEY (campaign_id, parent_id)
        REFERENCES campaign_note_folders (campaign_id, id) ON DELETE CASCADE,
    CONSTRAINT chk_campaign_note_folders_not_own_parent CHECK (parent_id IS DISTINCT FROM id)
);

CREATE INDEX IF NOT EXISTS idx_campaign_note_folders_parent
ON campaign_note_folders (campaign_id, parent_id, sort_order);

-- A note is one markdown document, written like a rule section: entity links such as
-- [Raise Dead](power:<uuid>) and images from the campaign's assets work the same way.
CREATE TABLE IF NOT EXISTS campaign_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    -- NULL: at the top level, outside any folder.
    folder_id UUID,

    title TEXT NOT NULL CHECK (btrim(title) <> ''),
    body TEXT NOT NULL DEFAULT '',
    author_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    -- Who saved it last (any storyteller of the campaign may edit any note).
    edited_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    sort_order INT NOT NULL DEFAULT 0,

    -- Generated full-text search over title and body, like rule sections.
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A')
        || setweight(to_tsvector('english', coalesce(body, '')), 'B')
    ) STORED,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT fk_campaign_notes_folder FOREIGN KEY (campaign_id, folder_id)
        REFERENCES campaign_note_folders (campaign_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_campaign_notes_folder
ON campaign_notes (campaign_id, folder_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_campaign_notes_search
ON campaign_notes USING GIN (search_vector);

-- Folders cannot be moved into themselves or below themselves (a parent_id cycle would hide them forever).
CREATE OR REPLACE FUNCTION campaign_note_folders_no_cycle() RETURNS trigger AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL AND EXISTS (
        WITH RECURSIVE up AS (
            SELECT id, parent_id FROM campaign_note_folders WHERE id = NEW.parent_id
            UNION ALL
            SELECT f.id, f.parent_id FROM campaign_note_folders f JOIN up ON f.id = up.parent_id
        )
        SELECT 1 FROM up WHERE id = NEW.id
    ) THEN
        RAISE EXCEPTION 'A folder cannot be moved into itself or one of its own folders'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'chk_campaign_note_folders_no_cycle';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_campaign_note_folders_no_cycle ON campaign_note_folders;
CREATE TRIGGER trg_campaign_note_folders_no_cycle
BEFORE INSERT OR UPDATE OF parent_id ON campaign_note_folders
FOR EACH ROW EXECUTE FUNCTION campaign_note_folders_no_cycle();

-- ----------------------------
-- Campaign versions of characters
-- ----------------------------

-- A player brings a character into a campaign as a deep copy (copy_character_to_campaign below): a new character
-- with its own rows in every character table (races, attributes, skills, traits and their edits, powers, EXP
-- awards, sources, inventory, slots, items), and campaign_id set. The campaign version can then level up, take
-- storyteller rulings, and use campaign content without changing the player's original, and the other way round.
-- origin_character_id only remembers which character it was copied from.
--
-- CASCADE: deleting a campaign deletes its character versions (they may use the campaign's own traits and items,
-- which go with it). The originals stay in the players' lists.
ALTER TABLE characters ADD COLUMN IF NOT EXISTS campaign_id UUID REFERENCES campaigns(id) ON DELETE CASCADE;
ALTER TABLE characters ADD COLUMN IF NOT EXISTS origin_character_id UUID REFERENCES characters(id) ON DELETE SET NULL;

-- Content in use by a character (or an image used by a rule) still cannot be deleted: the check stays immediate
-- for every ordinary delete. The keys become DEFERRABLE so that deleting a campaign can run
--     SET CONSTRAINTS ALL DEFERRED;  DELETE FROM content_sources WHERE id = <campaign>;
-- in one transaction: the check then runs at commit, after the cascade has also removed the campaign's own
-- character versions (otherwise one of them using one of the campaign's traits would block the delete). A
-- character outside the campaign that uses campaign content still blocks it, at commit.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT con.conname, rel.relname AS tbl, att.attname AS col, ref.relname AS ref_tbl
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_class ref ON ref.oid = con.confrelid
        JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = con.conkey[1]
        WHERE con.contype = 'f' AND NOT con.condeferrable AND array_length(con.conkey, 1) = 1
          AND (rel.relname, att.attname, ref.relname) IN (
              ('character_traits', 'trait_id', 'traits'),
              ('character_powers', 'power_id', 'powers'),
              ('character_items', 'item_id', 'items'),
              ('character_races', 'race_id', 'races'),
              ('characters', 'job_id', 'jobs'),
              ('rule_section_assets', 'asset_id', 'assets'))
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', fk.tbl, fk.conname);
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I(id) '
                       'ON DELETE NO ACTION DEFERRABLE INITIALLY IMMEDIATE', fk.tbl, fk.conname, fk.col, fk.ref_tbl);
    END LOOP;
END $$;

CREATE INDEX IF NOT EXISTS idx_characters_campaign_id
ON characters (campaign_id)
WHERE campaign_id IS NOT NULL;

-- A character is brought into a campaign once (a player may still bring several different characters).
CREATE UNIQUE INDEX IF NOT EXISTS idx_characters_campaign_origin_unique
ON characters (campaign_id, origin_character_id)
WHERE campaign_id IS NOT NULL AND origin_character_id IS NOT NULL;

-- ----------------------------
-- Chat
-- ----------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'campaign_message_kind') THEN
        -- message: something said; roll: a dice roll and its result; combat: a combat event (damage, healing,
        -- a condition, the turn order); system: written by the app ("Quill joined the campaign").
        CREATE TYPE campaign_message_kind AS ENUM ('message', 'roll', 'combat', 'system');
    END IF;
END $$;

-- The campaign's chat, roll, and combat log.
--
-- Who can read a message:
--   not a whisper       every member
--   a whisper           its sender, its recipients (campaign_message_recipients), and the storytellers
--                       (a whisper with no recipients goes to the storytellers only, e.g. a secret roll)
-- Storytellers read every message, including whispers between players. campaign_messages_for() applies this.
CREATE TABLE IF NOT EXISTS campaign_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    kind campaign_message_kind NOT NULL DEFAULT 'message',

    -- Shown by the sender's display name. NULL for system messages, or once the sender's account is deleted.
    sender_user_id UUID REFERENCES users(id) ON DELETE SET NULL,

    is_whisper BOOLEAN NOT NULL DEFAULT FALSE,

    -- The text (markdown, entity links like [Raise Dead](power:<uuid>) work); for rolls and combat a summary line.
    body TEXT NOT NULL DEFAULT '',
    -- What rolls and combat events need beyond the text, written by the API, e.g.
    --   roll:   {"formula": "1d100", "skill": "Dodge", "target": 47, "rolls": [23], "total": 23, "outcome": "success"}
    --   combat: {"event": "damage", "target": "Goblin 2", "amount": 7, "damageType": "Fire"}
    data JSONB,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- Set when the sender edits the text.
    edited_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_campaign_messages_content CHECK (
        (kind IN ('roll', 'combat') AND data IS NOT NULL) OR (kind IN ('message', 'system') AND btrim(body) <> '')
    ),
    CONSTRAINT chk_campaign_messages_system CHECK (kind <> 'system' OR (sender_user_id IS NULL AND NOT is_whisper))
);

-- Newest first, a page at a time.
CREATE INDEX IF NOT EXISTS idx_campaign_messages_campaign_created
ON campaign_messages (campaign_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_campaign_messages_sender
ON campaign_messages (sender_user_id)
WHERE sender_user_id IS NOT NULL;

-- Who a whisper is for (besides its sender and the storytellers, who always see it). The API only adds members.
CREATE TABLE IF NOT EXISTS campaign_message_recipients (
    message_id UUID NOT NULL REFERENCES campaign_messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    PRIMARY KEY (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_campaign_message_recipients_user
ON campaign_message_recipients (user_id);

-- How far each member has read, for unread counts.
CREATE TABLE IF NOT EXISTS campaign_chat_reads (
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (campaign_id, user_id)
);

-- The messages of a campaign that a member can read (nothing for a non-member). Page it with
--   SELECT * FROM campaign_messages_for(:campaign, :user) WHERE created_at < :before
--   ORDER BY created_at DESC, id DESC LIMIT 50
CREATE OR REPLACE FUNCTION campaign_messages_for(target_campaign UUID, viewer UUID)
RETURNS SETOF campaign_messages AS $$
    SELECT msg.*
    FROM campaign_messages msg
    JOIN content_sources s ON s.id = msg.campaign_id
    CROSS JOIN LATERAL (
        SELECT s.owner_user_id = viewer
               OR EXISTS (SELECT 1 FROM source_members m WHERE m.source_id = s.id AND m.user_id = viewer
                          AND m.role IN ('owner', 'editor')) AS storyteller,
               s.owner_user_id = viewer
               OR EXISTS (SELECT 1 FROM source_members m WHERE m.source_id = s.id AND m.user_id = viewer) AS member
    ) who
    WHERE msg.campaign_id = target_campaign
      AND who.member
      AND (NOT msg.is_whisper
           OR who.storyteller
           OR msg.sender_user_id = viewer
           OR EXISTS (SELECT 1 FROM campaign_message_recipients r WHERE r.message_id = msg.id AND r.user_id = viewer));
$$ LANGUAGE sql STABLE;

-- ----------------------------
-- Copying a character into a campaign
-- ----------------------------

-- Deep-copies a player's character into a campaign and returns the new character's id. Everything is copied in
-- one go, so the copy is never half made. Only characters outside any campaign can be copied, and each one only
-- once per campaign. The API checks that the caller owns the character and is a player of the campaign.
-- When a column is added to a character table, add it to the matching INSERT here.
CREATE OR REPLACE FUNCTION copy_character_to_campaign(source_character UUID, target_campaign UUID) RETURNS UUID AS $$
DECLARE
    original characters%ROWTYPE;
    copy_id UUID;
    slot RECORD;
    slot_map JSONB := '{}'::jsonb;
BEGIN
    SELECT * INTO original FROM characters WHERE id = source_character;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Character % not found', source_character USING ERRCODE = 'no_data_found';
    END IF;
    IF original.campaign_id IS NOT NULL THEN
        RAISE EXCEPTION 'Only characters outside a campaign can be brought into one'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'chk_characters_copy_from_original';
    END IF;

    -- The character, as a draft until its races and language race exist (an active character needs them).
    INSERT INTO characters (user_id, status, name, description, motive, ideals, strengths, flaws, learned_languages,
            history, important_characters, notes, character_sheet_id, job_id, size, starting_exp, job_points,
            current_hp, current_wp, current_ep, current_mp, temp_hp, hp_bonus, wp_bonus, ep_bonus, mp_bonus,
            physical_damage_bonus, magic_damage_bonus, priority_bonus, movement_bonus, campaign_id, origin_character_id)
    VALUES (original.user_id, 'draft', original.name, original.description, original.motive, original.ideals,
            original.strengths, original.flaws, original.learned_languages, original.history,
            original.important_characters, original.notes, original.character_sheet_id, original.job_id, original.size,
            original.starting_exp, original.job_points, original.current_hp, original.current_wp, original.current_ep,
            original.current_mp, original.temp_hp, original.hp_bonus, original.wp_bonus, original.ep_bonus,
            original.mp_bonus, original.physical_damage_bonus, original.magic_damage_bonus, original.priority_bonus,
            original.movement_bonus, target_campaign, original.id)
    RETURNING id INTO copy_id;

    INSERT INTO character_races (character_id, race_id, sort_order)
    SELECT copy_id, race_id, sort_order FROM character_races WHERE character_id = source_character;
    UPDATE characters SET language_race_id = original.language_race_id, status = original.status WHERE id = copy_id;

    INSERT INTO character_attributes (character_id, attribute, exp_spent, job_points_spent, bonus, modifier)
    SELECT copy_id, attribute, exp_spent, job_points_spent, bonus, modifier
    FROM character_attributes WHERE character_id = source_character;

    INSERT INTO character_skills (character_id, skill_id, creation_adjustment, exp_spent, job_points_spent, failures,
            permanent_modifier, temporary_modifier)
    SELECT copy_id, skill_id, creation_adjustment, exp_spent, job_points_spent, failures, permanent_modifier,
           temporary_modifier
    FROM character_skills WHERE character_id = source_character;

    INSERT INTO character_traits (character_id, trait_id, source, race_id, job_id, lvl, exp_spent, job_points_spent,
            notes, trait_name, purchase_cost, lvl_up_cost, trait_desc, trait_uses, mp_cost, ep_cost, tp_cost,
            trait_effect, lvl_up_effect)
    SELECT copy_id, trait_id, source, race_id, job_id, lvl, exp_spent, job_points_spent, notes, trait_name,
           purchase_cost, lvl_up_cost, trait_desc, trait_uses, mp_cost, ep_cost, tp_cost, trait_effect, lvl_up_effect
    FROM character_traits WHERE character_id = source_character;

    INSERT INTO character_powers (character_id, power_id, lvl, exp_spent, notes, name, type, tp_cost, hp_cost, mp_cost,
            ep_cost, material_components, verbal, sight, somatic, is_distinct, concentration, range, area, duration,
            effect, empower_effect, lvl_up_effect)
    SELECT copy_id, power_id, lvl, exp_spent, notes, name, type, tp_cost, hp_cost, mp_cost, ep_cost,
           material_components, verbal, sight, somatic, is_distinct, concentration, range, area, duration, effect,
           empower_effect, lvl_up_effect
    FROM character_powers WHERE character_id = source_character;

    -- EXP awards too, so the copy has the same EXP to spend (the history is the original's up to now).
    INSERT INTO character_exp_awards (character_id, amount, reason, awarded_by_user_id, created_at)
    SELECT copy_id, amount, reason, awarded_by_user_id, created_at
    FROM character_exp_awards WHERE character_id = source_character;

    INSERT INTO character_content_sources (character_id, source_id)
    SELECT copy_id, source_id FROM character_content_sources WHERE character_id = source_character;

    INSERT INTO character_inventories (character_id, platinum, gold, silver, bronze, attunement_limit,
            stack_limit_multiplier, notes)
    SELECT copy_id, platinum, gold, silver, bronze, attunement_limit, stack_limit_multiplier, notes
    FROM character_inventories WHERE character_id = source_character;

    -- Slots get new ids; items follow their slot to the copy's matching slot.
    FOR slot IN SELECT * FROM character_inventory_slots WHERE character_id = source_character LOOP
        slot_map := slot_map || jsonb_build_object(slot.id::text, gen_random_uuid());
        INSERT INTO character_inventory_slots (id, character_id, label, group_name, notes, sort_order, created_at)
        VALUES ((slot_map ->> slot.id::text)::uuid, copy_id, slot.label, slot.group_name, slot.notes, slot.sort_order,
                slot.created_at);
    END LOOP;

    INSERT INTO character_attunement_slots (character_id, label, group_name, notes, sort_order, created_at)
    SELECT copy_id, label, group_name, notes, sort_order, created_at
    FROM character_attunement_slots WHERE character_id = source_character;

    INSERT INTO character_items (character_id, item_id, custom_name, location, slot_id, quantity, stack_limit,
            attuned, charges_remaining, notes, sort_order, created_at)
    SELECT copy_id, item_id, custom_name, location, (slot_map ->> slot_id::text)::uuid, quantity, stack_limit,
           attuned, charges_remaining, notes, sort_order, created_at
    FROM character_items WHERE character_id = source_character;

    RETURN copy_id;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------
-- Views
-- ----------------------------

-- Each campaign with its owner, size, and member list counts, for campaign lists.
CREATE OR REPLACE VIEW campaign_overview AS
SELECT
    c.id AS campaign_id,
    s.source_name AS name,
    s.source_desc AS description,
    s.owner_user_id,
    c.join_enabled,
    (SELECT COUNT(*) FROM source_members m WHERE m.source_id = c.id AND m.role = 'viewer') AS player_count,
    (SELECT COUNT(*) FROM source_members m WHERE m.source_id = c.id AND m.role IN ('editor', 'owner')) AS storyteller_count,
    (SELECT COUNT(*) FROM characters ch WHERE ch.campaign_id = c.id) AS character_count,
    c.created_at,
    GREATEST(c.updated_at, s.updated_at) AS updated_at
FROM campaigns c
JOIN content_sources s ON s.id = c.id;

COMMIT;
