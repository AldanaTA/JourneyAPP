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
