package com.journey.api_rest_v1.service;

import java.util.List;

import org.springframework.data.domain.Page;

import com.journey.api_rest_v1.dto.creature.CreateCreatureRequest;
import com.journey.api_rest_v1.dto.creature.CreatureFilterRequest;
import com.journey.api_rest_v1.dto.creature.CreatureResponseDto;
import com.journey.api_rest_v1.dto.creature.CreatureTypeDto;

/**
 * Business operations for compendium creatures: CRUD, paging, filtering, and creature types.
 */
public interface CreatureService {

    /** Returns a sorted page of all creatures. */
    Page<CreatureResponseDto> getAllCreatures(int page, int size, String sortBy, String sortDir);

    /** Returns a page of creatures matching every filter set on the request, ranked by name when searching. */
    Page<CreatureResponseDto> filterCreatures(CreatureFilterRequest request);

    /** Returns creature types, for one content source or (when null) all sources. */
    List<CreatureTypeDto> getCreatureTypes(String contentSourceId);

    /** Creates a creature with its augments, abilities, and attacks, and returns it. */
    CreatureResponseDto createCreature(CreateCreatureRequest request);

    /** Returns one creature by its UUID string. */
    CreatureResponseDto getCreatureById(String id);

    /** Replaces an existing creature, including its augments, abilities, and attacks. */
    CreatureResponseDto updateCreature(String id, CreateCreatureRequest request);

    /** Deletes a creature (and its augments, abilities, and attacks) and returns a confirmation message. */
    String deleteCreature(String id);
}
