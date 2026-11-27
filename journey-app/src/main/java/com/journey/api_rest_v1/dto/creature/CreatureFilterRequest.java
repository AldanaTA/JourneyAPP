package com.journey.api_rest_v1.dto.creature;

import java.util.List;

import com.journey.api_rest_v1.domain.races.RaceSize;

/**
 * Request body for the creature filter endpoint. Every field is optional.
 * {@code creatureTypes} are type names (matched across sources); {@code sizes} match a creature that can
 * be any of them (inside its size range); {@code weakTo}/{@code resistantTo}/{@code immuneTo} are damage
 * type or status condition names.
 */
public record CreatureFilterRequest(
    List<String> creatureTypes,
    Integer minThreatLvl,
    Integer maxThreatLvl,
    List<RaceSize> sizes,
    List<String> weakTo,
    List<String> resistantTo,
    List<String> immuneTo,
    Integer page,
    Integer size,
    String name,
    /** Only creatures from these sources (e.g. one campaign's); empty = every source the caller can read. */
    List<String> sourceIds
) {
    /** Returns the requested page, defaulting to the first page. */
    public int getPage() {
        return page != null && page >= 0 ? page : 0;
    }

    /** Returns the requested page size, defaulting to 5 like the other filters. */
    public int getSize() {
        return size != null && size > 0 ? size : 5;
    }
}
