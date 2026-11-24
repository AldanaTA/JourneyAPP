package com.journey.api_rest_v1.domain.races;

/**
 * Creature size of a race, mirroring the Postgres {@code size} enum.
 * Constant names match the database labels exactly (T, S, M, L, H, C) and are declared
 * smallest to largest, so {@link #ordinal()} gives size order.
 */
public enum RaceSize {
    /** Tiny. */
    T,
    /** Small. */
    S,
    /** Medium. */
    M,
    /** Large. */
    L,
    /** Huge. */
    H,
    /** Colossal. */
    C
}
