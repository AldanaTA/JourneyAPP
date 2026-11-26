package com.journey.api_rest_v1.dto.character;

/** A player-managed slot and the stack in it (null when empty). groupName is set for slots made together. */
public record InventorySlotDto(
    String id,
    String label,
    String groupName,
    String notes,
    int sortOrder,
    CharacterItemDto item
) {
}
