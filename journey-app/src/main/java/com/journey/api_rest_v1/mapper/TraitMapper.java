package com.journey.api_rest_v1.mapper;

import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.dto.trait.CreateTraitRequest;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;

/**
 * Maps between trait entities and their request/response DTOs.
 */
public interface TraitMapper {

    /** Returns a copy of the given create request (kept for parity with {@link PowerMapper}). */
    CreateTraitRequest CreateRequest(CreateTraitRequest request);

    /** Converts a trait entity into the DTO returned by the API. */
    TraitResponseDto CreateResponse(Trait trait);
}
