package com.journey.api_rest_v1.dto.character;

import java.util.List;

/**
 * A character's whole inventory. attunementLimit = baseAttunementLimit + one per attunement slot.
 * warnings lists rule problems (overfull stacks, too many attunements).
 */
public record CharacterInventoryDto(
    int platinum,
    int gold,
    int silver,
    int bronze,
    long wealthBronze,
    int baseAttunementLimit,
    int attunementLimit,
    int attunedCount,
    int stackLimitMultiplier,
    String notes,
    CharacterItemDto armor,
    CharacterItemDto leftHand,
    CharacterItemDto rightHand,
    CharacterItemDto bothHands,
    CharacterItemDto bag,
    List<CharacterItemDto> components,
    List<CharacterItemDto> quest,
    List<InventorySlotDto> slots,
    List<AttunementSlotDto> attunementSlots,
    List<String> warnings
) {
}
