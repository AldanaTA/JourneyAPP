package com.journey.api_rest_v1.dto.character;

/** An extra attunement the player added, labelled by where it came from ("Stable Body 1"). */
public record AttunementSlotDto(
    String id,
    String label,
    String groupName,
    String notes,
    int sortOrder
) {
}
