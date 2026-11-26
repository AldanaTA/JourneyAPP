package com.journey.api_rest_v1.service;

import com.journey.api_rest_v1.dto.character.CharacterInventoryDto;
import com.journey.api_rest_v1.dto.character.SaveCharacterItemRequest;
import com.journey.api_rest_v1.dto.character.SaveInventorySlotRequest;
import com.journey.api_rest_v1.dto.character.UpdateInventoryRequest;

/**
 * A character's inventory: money, attunement limit, stack-limit multiplier, the inventory and attunement slots the
 * player manages (singly or in numbered groups), and the items carried. Every change returns the whole inventory.
 */
public interface CharacterInventoryService {

    CharacterInventoryDto get(String characterId);

    CharacterInventoryDto update(String characterId, UpdateInventoryRequest request);

    /** Adds one inventory slot, or a numbered group ("Spatial Storage 1..n"). */
    CharacterInventoryDto addSlots(String characterId, SaveInventorySlotRequest request);

    CharacterInventoryDto updateSlot(String characterId, String slotId, SaveInventorySlotRequest request);

    /** Removes an empty slot (409 while it holds an item). */
    CharacterInventoryDto deleteSlot(String characterId, String slotId);

    /** Removes every slot of a group, e.g. when the trait that gave them is lost (409 if any holds an item). */
    CharacterInventoryDto deleteSlotGroup(String characterId, String groupName);

    /** Renames a slot group and its numbered labels (409 if a group with the new name exists). */
    CharacterInventoryDto renameSlotGroup(String characterId, String groupName, String newName);

    /** Adds one extra attunement, or a numbered group ("Stable Body 1..n"). */
    CharacterInventoryDto addAttunementSlots(String characterId, SaveInventorySlotRequest request);

    CharacterInventoryDto updateAttunementSlot(String characterId, String slotId, SaveInventorySlotRequest request);

    /** Removes an extra attunement (409 if the character would then be attuned to more than the limit). */
    CharacterInventoryDto deleteAttunementSlot(String characterId, String slotId);

    /** Removes every attunement slot of a group (409 if the character would then be over the limit). */
    CharacterInventoryDto deleteAttunementSlotGroup(String characterId, String groupName);

    /** Renames an attunement slot group and its numbered labels (409 if the new name is taken). */
    CharacterInventoryDto renameAttunementSlotGroup(String characterId, String groupName, String newName);

    CharacterInventoryDto addItem(String characterId, SaveCharacterItemRequest request);

    /** Changes or moves an item; only the fields that are set change. */
    CharacterInventoryDto updateItem(String characterId, String itemId, SaveCharacterItemRequest request);

    CharacterInventoryDto deleteItem(String characterId, String itemId);
}
