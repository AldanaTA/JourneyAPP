package com.journey.api_rest_v1.mapper;

import com.journey.api_rest_v1.domain.creatures.Creature;
import com.journey.api_rest_v1.domain.creatures.CreatureType;
import com.journey.api_rest_v1.dto.creature.CreateCreatureRequest;
import com.journey.api_rest_v1.dto.creature.CreatureResponseDto;
import com.journey.api_rest_v1.dto.creature.CreatureTypeDto;

/**
 * Maps between creature entities and their request/response DTOs.
 */
public interface CreatureMapper {

    /** Returns the given create request (kept for parity with the other mappers; records are immutable). */
    CreateCreatureRequest CreateRequest(CreateCreatureRequest request);

    /** Converts a creature entity into the DTO returned by the API. */
    CreatureResponseDto CreateResponse(Creature creature);

    /** Converts a creature type entity into its DTO. */
    CreatureTypeDto CreateTypeResponse(CreatureType type);
}
