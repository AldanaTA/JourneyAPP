package com.journey.api_rest_v1.dto.character;

import com.journey.api_rest_v1.domain.characters.InventoryLocation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body for adding or changing a carried item; when changing, only the fields that are set change.
 * An item is a compendium item (item_id), a custom one (custom_name), or a renamed compendium item (both).
 * location SLOT needs slot_id (a slot_id alone also means SLOT); ARMOR, BAG, and each hand hold one item;
 * BOTH_HANDS takes both hands. Adding a compendium item defaults charges_remaining to its charges.
 * stack_limit gives this stack its own limit (a ruling or special container); reset_stack_limit returns it to the
 * item's stack limit x the character's multiplier.
 */
public record SaveCharacterItemRequest(
    String item_id,
    @Size(max = 200) String custom_name,
    InventoryLocation location,
    String slot_id,
    @Min(1) Integer quantity,
    @Min(1) Integer stack_limit,
    Boolean reset_stack_limit,
    Boolean attuned,
    @Min(0) Integer charges_remaining,
    String notes,
    Integer sort_order
) {
}
