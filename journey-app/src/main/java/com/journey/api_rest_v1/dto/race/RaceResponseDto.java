package com.journey.api_rest_v1.dto.race;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.journey.api_rest_v1.domain.races.RaceSize;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;

/**
 * Race payload returned to clients by the race endpoints.
 * {@code maxRaceSize} is null unless the race spans a size range ({@code minRaceSize} to {@code maxRaceSize}).
 * {@code traits} are in the race's display order.
 * It is Serializable so it can be stored in the Redis cache.
 */
public record RaceResponseDto(
    String id,
    String raceName,
    RaceSize minRaceSize,
    RaceSize maxRaceSize,
    Integer movement,
    String languages,
    String raceDescription,
    List<TraitResponseDto> traits,
    String contentSourceName
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
