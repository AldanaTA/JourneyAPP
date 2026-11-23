package com.journey.api_rest_v1.service;

import org.springframework.data.domain.Page;

import com.journey.api_rest_v1.dto.race.CreateRaceRequest;
import com.journey.api_rest_v1.dto.race.RaceFilterRequest;
import com.journey.api_rest_v1.dto.race.RaceResponseDto;

/**
 * Business operations for compendium races: CRUD, paging and filtering.
 */
public interface RaceService {

    /** Returns a sorted page of all races. */
    Page<RaceResponseDto> getAllRaces(int page, int size, String sortBy, String sortDir);

    /** Returns a page of races matching every filter set on the request, ranked by name when searching. */
    Page<RaceResponseDto> filterRaces(RaceFilterRequest request);

    /** Creates a new race linked to existing traits (tagging them RACIAL) and returns it. */
    RaceResponseDto createRace(CreateRaceRequest request);

    /** Returns one race by its UUID string. */
    RaceResponseDto getRaceById(String id);

    /** Replaces an existing race's fields and traits and returns the updated race. */
    RaceResponseDto updateRace(String id, CreateRaceRequest request);

    /** Deletes a race (and its trait links, not the traits themselves) and returns a confirmation message. */
    String deleteRace(String id);
}
