BEGIN;

-- What a character uses up during play, on top of 09-user_characters.sql, and copying characters (run after
-- 12-campaigns.sql: this replaces its copy function with one that also copies uses).
--
-- Traits with uses (traits.trait_uses, or the character's edited trait_uses) count the uses spent since the last
-- rest; the player resets them on a rest. Capacities spent during play (HP, WP, EP, MP) are already tracked by
-- characters.current_hp / current_wp / current_ep / current_mp, and temporary HP by characters.temp_hp.
ALTER TABLE character_traits ADD COLUMN IF NOT EXISTS uses_spent INT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_character_traits_uses_spent') THEN
        ALTER TABLE character_traits ADD CONSTRAINT chk_character_traits_uses_spent CHECK (uses_spent >= 0);
    END IF;
END $$;

-- ----------------------------
-- Copying characters
-- ----------------------------

-- Deep-copies a character (every character table, uses spent included) and returns the copy's id. target_campaign:
-- the campaign the copy belongs to, or NULL for a character of the player's own (e.g. exporting a campaign version
-- back to the player's list). origin_character_id remembers the character it was copied from.
-- When a column is added to a character table, add it to the matching INSERT here.
CREATE OR REPLACE FUNCTION copy_character(source_character UUID, target_campaign UUID) RETURNS UUID AS $$
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
            notes, uses_spent, trait_name, purchase_cost, lvl_up_cost, trait_desc, trait_uses, mp_cost, ep_cost, tp_cost,
            trait_effect, lvl_up_effect)
    SELECT copy_id, trait_id, source, race_id, job_id, lvl, exp_spent, job_points_spent, notes, uses_spent, trait_name,
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

-- Bringing a character into a campaign (see 12-campaigns.sql): only characters outside any campaign, through the
-- copy above.
CREATE OR REPLACE FUNCTION copy_character_to_campaign(source_character UUID, target_campaign UUID) RETURNS UUID AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM characters WHERE id = source_character AND campaign_id IS NOT NULL) THEN
        RAISE EXCEPTION 'Only characters outside a campaign can be brought into one'
            USING ERRCODE = 'check_violation', CONSTRAINT = 'chk_characters_copy_from_original';
    END IF;
    RETURN copy_character(source_character, target_campaign);
END;
$$ LANGUAGE plpgsql;

COMMIT;
