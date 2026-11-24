package com.journey.api_rest_v1.domain.creatures;

/**
 * Mirrors the Postgres {@code augment_kind} enum. Values are stored lowercase via {@link AugmentKindConverter}.
 */
public enum AugmentKind {
    IMMUNE,
    RESISTANT,
    WEAK
}
