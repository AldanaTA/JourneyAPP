package com.journey.api_rest_v1.dto.character;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding or changing slots the player manages (inventory slots and attunement slots alike).
 * To add several at once, name the group and give a count: {"group_name": "Spatial Storage", "count": 3} makes
 * "Spatial Storage 1" to "Spatial Storage 3", or continues the numbering if the group already has slots.
 * Without a group, label is used as given. count and group_name only apply when adding.
 */
public record SaveInventorySlotRequest(
    @Size(max = 100) String label,
    @Size(max = 100) String group_name,
    @Min(1) @Max(50) Integer count,
    String notes,
    Integer sort_order
) {
}
