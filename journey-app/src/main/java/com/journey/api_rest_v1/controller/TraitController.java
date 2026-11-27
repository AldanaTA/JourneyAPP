package com.journey.api_rest_v1.controller;

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

import com.journey.api_rest_v1.dto.trait.CreateTraitRequest;
import com.journey.api_rest_v1.dto.trait.TraitFilterRequest;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;
import com.journey.api_rest_v1.service.TraitService;

import com.journey.api_rest_v1.service.campaign.ContentAccess;

import jakarta.validation.Valid;

/**
 * REST endpoints for compendium traits under {@code /api/v1/traits}.
 * Mirrors {@link PowerController}: CRUD, a paged list, and a POST filter endpoint.
 */
@RestController
@RequestMapping("/api/v1/traits")
public class TraitController {

    private final TraitService traitService;
    private final ContentAccess contentAccess;

    /** Wires the trait service. */
    public TraitController(TraitService traitService, ContentAccess contentAccess) {
        this.contentAccess = contentAccess;
        this.traitService = traitService;
    }

    /** Creates a trait and returns it with 201 Created. */
    @PostMapping()
    public ResponseEntity<TraitResponseDto> createTrait(@Valid @RequestBody CreateTraitRequest request) {
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(traitService.createTrait(request), HttpStatus.CREATED);
    }

    /** Returns a single trait by id. */
    @GetMapping("/{id}")
    public ResponseEntity<TraitResponseDto> getTrait(@PathVariable String id) {
        contentAccess.requireReadable(ContentAccess.Kind.TRAIT, id);
        return new ResponseEntity<>(traitService.getTraitById(id), HttpStatus.OK);
    }

    /** Returns a sorted page of all traits. */
    @GetMapping()
    public ResponseEntity<Iterable<TraitResponseDto>> getAllTraits(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size,
        @RequestParam(defaultValue = "traitName") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(traitService.getAllTraits(page, size, sortBy, sortDir), HttpStatus.OK);
    }

    /** Returns a page of traits matching the filters in the request body (name, categories, level, costs). */
    @PostMapping("/filter-by-category")
    public ResponseEntity<Iterable<TraitResponseDto>> filterTraits(@Valid @RequestBody TraitFilterRequest request) {
        return new ResponseEntity<>(traitService.filterTraits(request), HttpStatus.OK);
    }

    /** Replaces an existing trait and returns the updated version. */
    @PutMapping("/{id}")
    public ResponseEntity<TraitResponseDto> updateTrait(@PathVariable String id,
            @Valid @RequestBody CreateTraitRequest request) {
        contentAccess.requireWritable(ContentAccess.Kind.TRAIT, id);
        contentAccess.requireWritableSource(request.content_source_id());
        return new ResponseEntity<>(traitService.updateTrait(id, request), HttpStatus.OK);
    }

    /** Deletes a trait and returns a confirmation message. */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTrait(@PathVariable String id) {
        contentAccess.requireWritable(ContentAccess.Kind.TRAIT, id);
        return ResponseEntity.ok(traitService.deleteTrait(id));
    }
}
