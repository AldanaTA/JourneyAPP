package com.journey.api_rest_v1.dto.race;

import java.util.List;

import com.journey.api_rest_v1.domain.races.RaceSize;

/**
 * Request body for the race filter endpoint.
 * Every field is optional; null values are ignored so the client only sends what the user set.
 * {@code sizes} matches races that can be any of the given sizes: a single-size race must equal one of
 * them, and a ranged race (e.g. S - M) matches any size inside its range.
 * The movement filter is paired with an operator (lte, lt, eq, gt, gte) that defaults to lte.
 */
public record RaceFilterRequest(
    List<RaceSize> sizes,
    Integer movement,
    String movementOperator,
    Integer page,
    Integer size,
    String name,
    String traitName
) {
    /** Returns the requested page, defaulting to the first page. */
    public int getPage() {
        return page != null && page >= 0 ? page : 0;
    }

    /** Returns the requested page size, defaulting to 5 like the trait filter. */
    public int getSize() {
        return size != null && size > 0 ? size : 5;
    }

    /** Returns the normalized movement comparison operator. */
    public String getMovementOperator() {
        if (movementOperator == null || movementOperator.isBlank()) {
            return "lte";
        }
        return movementOperator.trim().toLowerCase();
    }
}
