package com.journey.api_rest_v1.domain.creatures;

/**
 * Mirrors the Postgres {@code ability_timing} enum. Values are stored lowercase via {@link AbilityTimingConverter}.
 */
public enum AbilityTiming {
    PASSIVE,
    TRIGGERED,
    DECLARED
}
