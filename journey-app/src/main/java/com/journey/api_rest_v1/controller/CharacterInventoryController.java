package com.journey.api_rest_v1.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.character.CharacterInventoryDto;
import com.journey.api_rest_v1.dto.character.RenameSlotGroupRequest;
import com.journey.api_rest_v1.dto.character.SaveCharacterItemRequest;
import com.journey.api_rest_v1.dto.character.SaveInventorySlotRequest;
import com.journey.api_rest_v1.dto.character.UpdateInventoryRequest;
import com.journey.api_rest_v1.service.CharacterInventoryService;

import jakarta.validation.Valid;

/**
 * A character's inventory under {@code /api/v1/characters/{id}/inventory}: money, attunement limit, stack-limit
 * multiplier, the inventory and attunement slots the player manages (singly or in numbered groups), and the items
 * carried. Changes return the whole inventory.
 */
@RestController
@RequestMapping("/api/v1/characters/{id}/inventory")
public class CharacterInventoryController {

    private final CharacterInventoryService inventoryService;

    /** Wires the inventory service. */
    public CharacterInventoryController(CharacterInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<CharacterInventoryDto> get(@PathVariable String id) {
        return ResponseEntity.ok(inventoryService.get(id));
    }

    /** Changes money, the attunement limit, or notes; only set fields change. */
    @PatchMapping
    public ResponseEntity<CharacterInventoryDto> update(@PathVariable String id, @Valid @RequestBody UpdateInventoryRequest request) {
        return ResponseEntity.ok(inventoryService.update(id, request));
    }

    /** Adds one slot, or a numbered group: {"group_name": "Spatial Storage", "count": 3}. */
    @PostMapping("/slots")
    public ResponseEntity<CharacterInventoryDto> addSlots(@PathVariable String id, @Valid @RequestBody SaveInventorySlotRequest request) {
        return new ResponseEntity<>(inventoryService.addSlots(id, request), HttpStatus.CREATED);
    }

    /** Renames a group and its numbered labels: PATCH .../slots?group=Spatial Storage {"name": "Pocket Dimension"}. */
    @PatchMapping("/slots")
    public ResponseEntity<CharacterInventoryDto> renameSlotGroup(@PathVariable String id, @RequestParam("group") String group,
            @Valid @RequestBody RenameSlotGroupRequest request) {
        return ResponseEntity.ok(inventoryService.renameSlotGroup(id, group, request.name()));
    }

    /** Removes every slot of a group: DELETE .../slots?group=Spatial Storage (409 if any holds an item). */
    @DeleteMapping("/slots")
    public ResponseEntity<CharacterInventoryDto> deleteSlotGroup(@PathVariable String id, @RequestParam("group") String group) {
        return ResponseEntity.ok(inventoryService.deleteSlotGroup(id, group));
    }

    @PatchMapping("/slots/{slotId}")
    public ResponseEntity<CharacterInventoryDto> updateSlot(@PathVariable String id, @PathVariable String slotId,
            @Valid @RequestBody SaveInventorySlotRequest request) {
        return ResponseEntity.ok(inventoryService.updateSlot(id, slotId, request));
    }

    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<CharacterInventoryDto> deleteSlot(@PathVariable String id, @PathVariable String slotId) {
        return ResponseEntity.ok(inventoryService.deleteSlot(id, slotId));
    }

    /** Adds one extra attunement, or a numbered group: {"group_name": "Stable Body", "count": 2}. */
    @PostMapping("/attunement-slots")
    public ResponseEntity<CharacterInventoryDto> addAttunementSlots(@PathVariable String id,
            @Valid @RequestBody SaveInventorySlotRequest request) {
        return new ResponseEntity<>(inventoryService.addAttunementSlots(id, request), HttpStatus.CREATED);
    }

    @PatchMapping("/attunement-slots/{slotId}")
    public ResponseEntity<CharacterInventoryDto> updateAttunementSlot(@PathVariable String id, @PathVariable String slotId,
            @Valid @RequestBody SaveInventorySlotRequest request) {
        return ResponseEntity.ok(inventoryService.updateAttunementSlot(id, slotId, request));
    }

    @DeleteMapping("/attunement-slots/{slotId}")
    public ResponseEntity<CharacterInventoryDto> deleteAttunementSlot(@PathVariable String id, @PathVariable String slotId) {
        return ResponseEntity.ok(inventoryService.deleteAttunementSlot(id, slotId));
    }

    /** Renames an attunement slot group and its numbered labels. */
    @PatchMapping("/attunement-slots")
    public ResponseEntity<CharacterInventoryDto> renameAttunementSlotGroup(@PathVariable String id,
            @RequestParam("group") String group, @Valid @RequestBody RenameSlotGroupRequest request) {
        return ResponseEntity.ok(inventoryService.renameAttunementSlotGroup(id, group, request.name()));
    }

    /** Removes every attunement slot of a group: DELETE .../attunement-slots?group=Stable Body. */
    @DeleteMapping("/attunement-slots")
    public ResponseEntity<CharacterInventoryDto> deleteAttunementSlotGroup(@PathVariable String id,
            @RequestParam("group") String group) {
        return ResponseEntity.ok(inventoryService.deleteAttunementSlotGroup(id, group));
    }

    @PostMapping("/items")
    public ResponseEntity<CharacterInventoryDto> addItem(@PathVariable String id, @Valid @RequestBody SaveCharacterItemRequest request) {
        return new ResponseEntity<>(inventoryService.addItem(id, request), HttpStatus.CREATED);
    }

    /** Changes or moves an item (e.g. {"location": "SLOT", "slot_id": ...}). */
    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CharacterInventoryDto> updateItem(@PathVariable String id, @PathVariable String itemId,
            @Valid @RequestBody SaveCharacterItemRequest request) {
        return ResponseEntity.ok(inventoryService.updateItem(id, itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<CharacterInventoryDto> deleteItem(@PathVariable String id, @PathVariable String itemId) {
        return ResponseEntity.ok(inventoryService.deleteItem(id, itemId));
    }
}
