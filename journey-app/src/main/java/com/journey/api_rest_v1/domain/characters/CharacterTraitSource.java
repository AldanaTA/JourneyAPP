package com.journey.api_rest_v1.domain.characters;

/** Where a character's trait came from: a race, the job's free picks, or bought with EXP. Mirrors the Postgres {@code character_trait_source} enum. */
public enum CharacterTraitSource {
    RACIAL,
    JOB,
    PURCHASED
}
