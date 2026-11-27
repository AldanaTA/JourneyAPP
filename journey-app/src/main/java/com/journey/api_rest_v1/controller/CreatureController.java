package com.journey.api_rest_v1.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.dto.creature.CreateCreatureRequest;
import com.journey.api_rest_v1.dto.creature.CreatureFilterRequest;
import com.journey.api_rest_v1.dto.creature.CreatureResponseDto;
import com.journey.api_rest_v1.dto.creature.CreatureTypeDto;
import com.journey.api_rest_v1.service.CreatureService;

import com.journey.api_rest_v1.service.campaign.ContentAccess;

import jakarta.validation.Valid;

/**
 * REST endpoints for compendium creatures under {@code /api/v1/creatures}.
 * Mirrors {@link RaceController}: CRUD, a paged list, a POST filter endpoint, plus the creature types.
 */
@RestController
@RequestMapping("/api/v1/creatures")
public class CreatureController {

    private final CreatureService creatureService;
    private final ContentAccess contentAccess;

    /** Wires the creature service. */
    public CreatureController(CreatureService creatureService, ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
        this.creatureService = creatureService;
    }

    /** Creates a creature and returns it with 201 Created. */
    @PostMapping()
    public ResponseEntity<CreatureResponseDto> createCreature(@Valid @RequestBody CreateCreatureRequest request) {
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(creatureService.createCreature(request), HttpStatus.CREATED);
    }

    /** Returns creature types; pass contentSourceId to list one source's types. */
    @GetMapping("/types")
    public ResponseEntity<List<CreatureTypeDto>> getCreatureTypes(
            @RequestParam(required = false) String contentSourceId) {
        return new ResponseEntity<>(creatureService.getCreatureTypes(contentSourceId), HttpStatus.OK);
    }

    /** Returns a single creature by id. */
    @GetMapping("/{id}")
    public ResponseEntity<CreatureResponseDto> getCreature(@PathVariable String id) {
        contentAccess.requireReadable(ContentAccess.Kind.CREATURE, id);
        return new ResponseEntity<>(creatureService.getCreatureById(id), HttpStatus.OK);
    }

    /** Returns a sorted page of all creatures. */
    @GetMapping()
    public ResponseEntity<Iterable<CreatureResponseDto>> getAllCreatures(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size,
        @RequestParam(defaultValue = "name") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(creatureService.getAllCreatures(page, size, sortBy, sortDir), HttpStatus.OK);
    }

    /** Returns a page of creatures matching the filters in the request body. */
    @PostMapping("/filter-by-category")
    public ResponseEntity<Iterable<CreatureResponseDto>> filterCreatures(@Valid @RequestBody CreatureFilterRequest request) {
        return new ResponseEntity<>(creatureService.filterCreatures(request), HttpStatus.OK);
    }

    /** Replaces an existing creature and returns the updated version. */
    @PutMapping("/{id}")
    public ResponseEntity<CreatureResponseDto> updateCreature(@PathVariable String id,
            @Valid @RequestBody CreateCreatureRequest request) {
        contentAccess.requireWritable(ContentAccess.Kind.CREATURE, id);
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(creatureService.updateCreature(id, request), HttpStatus.OK);
    }

    /** Deletes a creature and returns a confirmation message. */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCreature(@PathVariable String id) {
        contentAccess.requireWritable(ContentAccess.Kind.CREATURE, id);
        return ResponseEntity.ok(creatureService.deleteCreature(id));
    }
}
