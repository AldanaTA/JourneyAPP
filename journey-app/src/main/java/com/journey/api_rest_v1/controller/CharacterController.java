package com.journey.api_rest_v1.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.CharacterExpAwardDto;
import com.journey.api_rest_v1.dto.character.CharacterReferenceDataDto;
import com.journey.api_rest_v1.dto.character.CharacterSummaryDto;
import com.journey.api_rest_v1.dto.character.CreateCharacterRequest;
import com.journey.api_rest_v1.dto.character.CreateExpAwardRequest;
import com.journey.api_rest_v1.dto.character.CreationCheckDto;
import com.journey.api_rest_v1.dto.character.SaveCharacterRequest;
import com.journey.api_rest_v1.dto.character.SetCharacterJobRequest;
import com.journey.api_rest_v1.dto.character.SetCharacterRacesRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterAttributeRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterSkillRequest;
import com.journey.api_rest_v1.service.CharacterService;
import com.journey.api_rest_v1.service.impl.CharacterSaveService;

import jakarta.validation.Valid;

/**
 * The signed-in user's characters under {@code /api/v1/characters}. Only the owner can see or change a
 * character (anyone else gets 404). Changes return the whole character sheet.
 *
 * <p>Creation flow: POST a draft, then set the races, job, attributes, skills, traits, and powers in any order;
 * GET /creation-check lists what is still missing, and POST /finalize makes the character active.</p>
 */
@RestController
@RequestMapping("/api/v1/characters")
public class CharacterController {

    private final CharacterService characterService;
    private final CharacterSaveService saveService;

    /** Wires the character services. */
    public CharacterController(CharacterService characterService, CharacterSaveService saveService) {
        this.characterService = characterService;
        this.saveService = saveService;
    }

    /** The starting sheets and skills character creation chooses from. */
    @GetMapping("/reference-data")
    public ResponseEntity<CharacterReferenceDataDto> getReferenceData() {
        return ResponseEntity.ok(characterService.getReferenceData());
    }

    /** The signed-in user's characters, most recently changed first. */
    @GetMapping
    public ResponseEntity<List<CharacterSummaryDto>> listMine() {
        return ResponseEntity.ok(characterService.listMine());
    }

    /** Creates a draft character. */
    @PostMapping
    public ResponseEntity<CharacterDto> create(@Valid @RequestBody CreateCharacterRequest request) {
        return new ResponseEntity<>(characterService.create(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CharacterDto> get(@PathVariable String id) {
        return ResponseEntity.ok(characterService.get(id));
    }

    /** Changes identity, sheet, size, point pools, capacities, bonuses, or status; only set fields change. */
    @PatchMapping("/{id}")
    public ResponseEntity<CharacterDto> update(@PathVariable String id, @Valid @RequestBody UpdateCharacterRequest request) {
        return ResponseEntity.ok(characterService.update(id, request));
    }

    /**
     * Saves the whole character as the editor has it (Save and autosave): identity, sources, races, job,
     * attributes, skills, traits, powers, capacities, and inventory, checked together in one transaction.
     * Parts left out (null) are not changed. Returns the character and its inventory.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CharacterSaveService.SavedCharacter> save(@PathVariable String id,
            @Valid @RequestBody SaveCharacterRequest request) {
        return ResponseEntity.ok(saveService.save(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        characterService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Sets the races (one, or several for a mixed race) and whose language set the character speaks. */
    @PutMapping("/{id}/races")
    public ResponseEntity<CharacterDto> setRaces(@PathVariable String id, @Valid @RequestBody SetCharacterRacesRequest request) {
        return ResponseEntity.ok(characterService.setRaces(id, request));
    }

    /** Sets or clears the job; the previous job's free traits are removed. */
    @PutMapping("/{id}/job")
    public ResponseEntity<CharacterDto> setJob(@PathVariable String id, @Valid @RequestBody SetCharacterJobRequest request) {
        return ResponseEntity.ok(characterService.setJob(id, request));
    }

    /** Changes one attribute: body, strength, speed, or magic. */
    @PatchMapping("/{id}/attributes/{attribute}")
    public ResponseEntity<CharacterDto> updateAttribute(@PathVariable String id, @PathVariable String attribute,
            @Valid @RequestBody UpdateCharacterAttributeRequest request) {
        return ResponseEntity.ok(characterService.updateAttribute(id, attribute, request));
    }

    /** Changes one skill (creation swap, EXP, job points, failures, modifiers). */
    @PatchMapping("/{id}/skills/{skillId}")
    public ResponseEntity<CharacterDto> updateSkill(@PathVariable String id, @PathVariable String skillId,
            @Valid @RequestBody UpdateCharacterSkillRequest request) {
        return ResponseEntity.ok(characterService.updateSkill(id, skillId, request));
    }

    @GetMapping("/{id}/exp-awards")
    public ResponseEntity<List<CharacterExpAwardDto>> listExpAwards(@PathVariable String id) {
        return ResponseEntity.ok(characterService.listExpAwards(id));
    }

    /** Gives the character EXP (negative corrects a mistake). */
    @PostMapping("/{id}/exp-awards")
    public ResponseEntity<CharacterDto> addExpAward(@PathVariable String id, @Valid @RequestBody CreateExpAwardRequest request) {
        return new ResponseEntity<>(characterService.addExpAward(id, request), HttpStatus.CREATED);
    }

    /** What still breaks the character creation rules (empty when ready to finish). */
    @GetMapping("/{id}/creation-check")
    public ResponseEntity<CreationCheckDto> checkCreation(@PathVariable String id) {
        return ResponseEntity.ok(characterService.checkCreation(id));
    }

    /** Finishes character creation (draft -> active); 422 listing the problems if rules are not met. */
    @PostMapping("/{id}/finalize")
    public ResponseEntity<CharacterDto> finalizeCreation(@PathVariable String id) {
        return ResponseEntity.ok(characterService.finalizeCreation(id));
    }
}
