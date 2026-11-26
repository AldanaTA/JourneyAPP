package com.journey.api_rest_v1.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.character.AddCharacterPowerRequest;
import com.journey.api_rest_v1.dto.character.AddCharacterTraitRequest;
import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.UpdateCharacterPowerRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterTraitRequest;
import com.journey.api_rest_v1.service.CharacterAbilityService;

import jakarta.validation.Valid;

/**
 * A character's traits and powers under {@code /api/v1/characters/{id}}. PATCH levels them and stores the
 * character's own edits; {@code reset} returns fields to the compendium values. Changes return the whole sheet.
 */
@RestController
@RequestMapping("/api/v1/characters/{id}")
public class CharacterAbilityController {

    private final CharacterAbilityService abilityService;

    /** Wires the ability service. */
    public CharacterAbilityController(CharacterAbilityService abilityService) {
        this.abilityService = abilityService;
    }

    /** Adds a racial, job, or purchased trait. */
    @PostMapping("/traits")
    public ResponseEntity<CharacterDto> addTrait(@PathVariable String id, @Valid @RequestBody AddCharacterTraitRequest request) {
        return new ResponseEntity<>(abilityService.addTrait(id, request), HttpStatus.CREATED);
    }

    @PatchMapping("/traits/{traitId}")
    public ResponseEntity<CharacterDto> updateTrait(@PathVariable String id, @PathVariable String traitId,
            @Valid @RequestBody UpdateCharacterTraitRequest request) {
        return ResponseEntity.ok(abilityService.updateTrait(id, traitId, request));
    }

    @DeleteMapping("/traits/{traitId}")
    public ResponseEntity<CharacterDto> removeTrait(@PathVariable String id, @PathVariable String traitId) {
        return ResponseEntity.ok(abilityService.removeTrait(id, traitId));
    }

    @PostMapping("/powers")
    public ResponseEntity<CharacterDto> addPower(@PathVariable String id, @Valid @RequestBody AddCharacterPowerRequest request) {
        return new ResponseEntity<>(abilityService.addPower(id, request), HttpStatus.CREATED);
    }

    @PatchMapping("/powers/{powerId}")
    public ResponseEntity<CharacterDto> updatePower(@PathVariable String id, @PathVariable String powerId,
            @Valid @RequestBody UpdateCharacterPowerRequest request) {
        return ResponseEntity.ok(abilityService.updatePower(id, powerId, request));
    }

    @DeleteMapping("/powers/{powerId}")
    public ResponseEntity<CharacterDto> removePower(@PathVariable String id, @PathVariable String powerId) {
        return ResponseEntity.ok(abilityService.removePower(id, powerId));
    }
}
