BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Damage types are core rules shared by weapons (and later powers), so they live in their own
-- file that runs before every content table.
CREATE TABLE IF NOT EXISTS damage_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,

    -- What happens when the damage type effect is applied (e.g. on a straight success).
    effect TEXT NOT NULL,
    -- Skills a defender can use against this damage type, e.g. {'Dodge', 'Weapon Skill'}.
    defenses TEXT[] NOT NULL DEFAULT '{}',

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_damage_types_name_key_unique
ON damage_types (name_key);

INSERT INTO damage_types (name, effect, defenses)
VALUES
    ('Acid', 'The target takes 2d6 extra damage on a hit for the next two attacks that they get hit by.', '{Dodge}'),
    ('Bludgeoning', 'The target is knocked prone.', '{Dodge, Weapon Skill}'),
    ('Cold', 'The target''s movement is reduced by half until the start of the next round.', '{Dodge, Resilience}'),
    ('Dark', 'The target cannot gain healing from any source and the caster gains HP equal to the damage dealt.', '{Self}'),
    ('Fire', 'The target''s damage is reduced by half for the next attack that they get a success on.', '{Dodge, Resilience}'),
    ('Force', 'The target takes force damage equal to half the caster''s MD.', '{Dodge, Resilience}'),
    ('Light', 'The next attack roll against the target has a 40% bonus.', '{Self}'),
    ('Lightning', 'The target has a 20% penalty on non magic defensive rolls until the end of the next round.', '{Dodge, Resilience}'),
    ('Piercing', 'The target''s parry skill rolls have a 40% penalty until the start of the next round.', '{Dodge, Weapon Skill}'),
    ('Poison', 'The target takes 2d6 poison damage at the start of the next round.', '{Dodge, Resilience}'),
    ('Psychic', 'The next action that requires a skill roll has a 20% penalty.', '{Mind, Self}'),
    ('Slashing', 'The creatures adjacent to the target also take half of the rolled damage.', '{Dodge, Weapon Skill}')
ON CONFLICT (name_key)
DO UPDATE SET
    name = EXCLUDED.name,
    effect = EXCLUDED.effect,
    defenses = EXCLUDED.defenses,
    updated_at = NOW();

-- Status conditions are core rules (like damage types). Creatures can be immune, resistant, or weak to
-- them, and other content can reference them. Settings may add more rows.
CREATE TABLE IF NOT EXISTS status_conditions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,
    name_key TEXT GENERATED ALWAYS AS (LOWER(TRIM(name))) STORED,

    -- The rules text; paragraphs are separated by blank lines.
    description TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_status_conditions_name_key_unique
ON status_conditions (name_key);

INSERT INTO status_conditions (name, description)
VALUES
    ('Bleeding', E'A bleeding creature takes xd6 damage when they take an action where x is the amount of TP the action took.'),
    ('Blessed', E'A blessed creature has the Favorable condition on all skill rolls.'),
    ('Blinded', E'A creature that is blinded fails all rolls that require sight. A blinded creature does weapon and dodge skill rolls with a 30% penalty.'),
    ('Charmed', E'A charmed creature cannot attack or target the charmer with harmful magical effects\n\nThe charmer has the Favorable condition on any skill roll to socially interact with the charmer'),
    ('Cursed', E'A cursed creature has the Difficult condition on all skill rolls.'),
    ('Dazed', E'The dazed creature gets their TP reduced by 2 and movement halved.'),
    ('Deafened', E'A deafened creature can''t hear and automatically fails in skill rolls that require hearing and perception rolls receive a 50% penalty on perception skill rolls.'),
    ('Enfeeble', E'The creature reduces their PD by half the amount whenever they add their PD to an action.'),
    ('Exhaustion', E'An exhausted creature has -15% for all skill checks for each level of exhaustion\n\nAn exhausted creature does not regain WP on a long rest.\n\nIf a creature gains 10 lvls of exhaustion then that creature dies.'),
    ('Frightened', E'A frightened creature has a 75% penalty on skill rolls while the source of its fear is within line of sight.\n\nThe creature can''t willingly move closer to the source of its fear.'),
    ('Impaired', E'An impaired creature has a 60% penalty on weapon skills rolls, dodge rolls, parkour rolls, and athletics rolls.'),
    ('Incapacitated', E'The creature has a TP of 0, cannot speak, and cannot spend uses of their traits.'),
    ('Infatuated', E'An infatuated creature cannot attack or target the infatuator with harmful magical effects\n\nThe infatuator has the Easy condition on any skill check to socially interact with the infatuated.'),
    ('Inspired', E'An inspired creature has a 25% bonus on all skill rolls.'),
    ('Invisible', E'An invisible creature is impossible to see without the aid of magic or a special sense. For the purpose of hiding, the creature is obscured.\n\nThe creature is also considered always hidden.'),
    ('Mighty', E'The creature may add their PD twice whenever they add their PD to an action.'),
    ('Petrified', E'The creature is under the stunned status condition and takes half damage from all sources.'),
    ('Prone', E'A prone creature''s only movement option is to crawl, unless it stands up and thereby ends the condition.\n\nAttack rolls against the creature are done under the Favorable condition if the attacker is within 5 feet of the creature, and the creature''s melee attack rolls are done with the Difficult condition.\n\nAttacks done more than 5 ft away have the Difficult condition against the creature.\n\nThe creature does Dodge skill rolls under the Difficult condition.'),
    ('Restrained', E'A restrained creature''s move becomes 0 and it can''t benefit from any bonus to its move.\n\nAttack rolls against the creature are done with a 50% Bonus, and the creature''s melee attack rolls are done with a 50% Penalty.\n\nThe creature has a 50% penalty on Dodge and Parry skill rolls.'),
    ('Rush', E'A creature with rush doubles the Priority.'),
    ('Shrouded', E'A creature who is shrouded is undetectable by magic. They cannot be targeted by any power.'),
    ('Silenced', E'A silenced creature cannot use mp to activate powers, items, or traits.'),
    ('Stunned', E'The creature automatically fails parry and dodge skill rolls and is under the incapacitated and restrained status conditions.'),
    ('Sluggishness', E'The creature with sluggish halves their priority.'),
    ('Suppressed', E'The creature reduces their MD by half the amount whenever they add their MD to an action.'),
    ('Surge', E'The creature may add their MD twice whenever they add their MD to an action.'),
    ('Unconscious', E'The creature falls unconscious, unable to activate traits, powers, or take actions and is under the effects of the vulnerable status condition. If a creature is hit while unconscious the status condition ends immediately.'),
    ('Vulnerable', E'A vulnerable creature is under the effects of the incapacitated (see status condition) and restrained,\n\nThe creature drops whatever it''s holding and falls prone.\n\nThe creature automatically fails all skill rolls that require mobility.\n\nAttack rolls against the creature have the Easy condition.\n\nAny attack that hits the creature is a critical hit if the attacker is within 5 feet of the creature.'),
    ('Weaken', E'The creature loses resistance to the stated damage type or status condition. If they are neutral to the damage type or status condition they are weak to that damage type or status condition instead.')
ON CONFLICT (name_key)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'size') THEN
        CREATE TYPE size AS ENUM (
            'T',
            'S',
            'M',
            'L',
            'H',
            'C'
        );
    END IF;
END $$;

COMMIT;
