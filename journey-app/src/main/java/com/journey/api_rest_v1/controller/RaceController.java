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

import com.journey.api_rest_v1.dto.race.CreateRaceRequest;
import com.journey.api_rest_v1.dto.race.RaceFilterRequest;
import com.journey.api_rest_v1.dto.race.RaceResponseDto;
import com.journey.api_rest_v1.service.RaceService;

import jakarta.validation.Valid;

/**
 * REST endpoints for compendium races under {@code /api/v1/races}.
 * Mirrors {@link JobController}: CRUD, a paged list, and a POST filter endpoint.
 */
@RestController
@RequestMapping("/api/v1/races")
public class RaceController {

    private final RaceService raceService;

    /** Wires the race service. */
    public RaceController(RaceService raceService) {
        this.raceService = raceService;
    }

    /** Creates a race from existing traits and returns it with 201 Created. */
    @PostMapping()
    public ResponseEntity<RaceResponseDto> createRace(@Valid @RequestBody CreateRaceRequest request) {
        return new ResponseEntity<>(raceService.createRace(request), HttpStatus.CREATED);
    }

    /** Returns a single race by id, with its traits in display order. */
    @GetMapping("/{id}")
    public ResponseEntity<RaceResponseDto> getRace(@PathVariable String id) {
        return new ResponseEntity<>(raceService.getRaceById(id), HttpStatus.OK);
    }

    /** Returns a sorted page of all races. */
    @GetMapping()
    public ResponseEntity<Iterable<RaceResponseDto>> getAllRaces(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size,
        @RequestParam(defaultValue = "raceName") String sortBy,
        @RequestParam(defaultValue = "asc") String sortDir) {
        return new ResponseEntity<>(raceService.getAllRaces(page, size, sortBy, sortDir), HttpStatus.OK);
    }

    /** Returns a page of races matching the filters in the request body (name, sizes, movement, trait name). */
    @PostMapping("/filter-by-category")
    public ResponseEntity<Iterable<RaceResponseDto>> filterRaces(@Valid @RequestBody RaceFilterRequest request) {
        return new ResponseEntity<>(raceService.filterRaces(request), HttpStatus.OK);
    }

    /** Replaces an existing race and returns the updated version. */
    @PutMapping("/{id}")
    public ResponseEntity<RaceResponseDto> updateRace(@PathVariable String id,
            @Valid @RequestBody CreateRaceRequest request) {
        return new ResponseEntity<>(raceService.updateRace(id, request), HttpStatus.OK);
    }

    /** Deletes a race (its traits are kept) and returns a confirmation message. */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRace(@PathVariable String id) {
        return ResponseEntity.ok(raceService.deleteRace(id));
    }
}
