package com.journey.api_rest_v1.dto.character;

import com.journey.api_rest_v1.domain.races.RaceSize;

/**
 * One of a character's races.
 */
public record CharacterRaceDto(
    String id,
    String name,
    RaceSize minSize,
    RaceSize maxSize,
    Integer movement,
    String languages
) {
}
