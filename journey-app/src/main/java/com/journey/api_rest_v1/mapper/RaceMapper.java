package com.journey.api_rest_v1.mapper;

import com.journey.api_rest_v1.domain.races.Race;
import com.journey.api_rest_v1.dto.race.CreateRaceRequest;
import com.journey.api_rest_v1.dto.race.RaceResponseDto;

/**
 * Maps between race entities and their request/response DTOs.
 */
public interface RaceMapper {

    /** Returns a copy of the given create request (kept for parity with {@link JobMapper}). */
    CreateRaceRequest CreateRequest(CreateRaceRequest request);

    /** Converts a race entity into the DTO returned by the API. */
    RaceResponseDto CreateResponse(Race race);
}
