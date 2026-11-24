package com.journey.api_rest_v1.mapper;

import com.journey.api_rest_v1.domain.items.Item;
import com.journey.api_rest_v1.dto.item.CreateItemRequest;
import com.journey.api_rest_v1.dto.item.ItemResponseDto;

/**
 * Maps between item entities and their request/response DTOs.
 */
public interface ItemMapper {

    /** Returns a copy of the given create request (kept for parity with the other mappers). */
    CreateItemRequest CreateRequest(CreateItemRequest request);

    /** Converts an item entity into the DTO returned by the API, resolving inheritance from its base items. */
    ItemResponseDto CreateResponse(Item item);
}
