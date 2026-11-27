BEGIN;

-- What a character uses up during play, on top of 09-user_characters.sql.
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

COMMIT;
