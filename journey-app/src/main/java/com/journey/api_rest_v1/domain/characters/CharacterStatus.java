package com.journey.api_rest_v1.domain.characters;

/** Lifecycle of a character: drafts may break creation rules, active characters are in play. Mirrors the Postgres {@code character_status} enum. */
public enum CharacterStatus {
    DRAFT,
    ACTIVE,
    RETIRED
}
