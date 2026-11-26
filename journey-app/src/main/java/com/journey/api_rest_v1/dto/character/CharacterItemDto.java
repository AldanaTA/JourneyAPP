package com.journey.api_rest_v1.dto.character;

import com.journey.api_rest_v1.domain.characters.InventoryLocation;

/**
 * A carried item. name is the custom name if set, otherwise the compendium name. stackLimit is the limit in
 * effect: stackLimitOverride if set, else the compendium baseStackLimit x the character's stack-limit multiplier.
 */
public record CharacterItemDto(
    String id,
    String itemId,
    String name,
    String customName,
    String compendiumName,
    String itemType,
    InventoryLocation location,
    String slotId,
    int quantity,
    Integer stackLimit,
    Integer baseStackLimit,
    Integer stackLimitOverride,
    boolean attuned,
    boolean requiresAttunement,
    Integer chargesRemaining,
    Integer maxCharges,
    String notes,
    int sortOrder
) {
}
