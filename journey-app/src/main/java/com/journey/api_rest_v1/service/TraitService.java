package com.journey.api_rest_v1.service;

import java.util.List;

import org.springframework.data.domain.Page;

import com.journey.api_rest_v1.domain.traits.TraitCategory;
import com.journey.api_rest_v1.dto.trait.CreateTraitRequest;
import com.journey.api_rest_v1.dto.trait.TraitFilterRequest;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;

/**
 * Business operations for compendium traits: CRUD, paging and filtering.
 */
public interface TraitService {

    /** Returns a sorted page of all traits. */
    Page<TraitResponseDto> getAllTraits(int page, int size, String sortBy, String sortDir);

    /** Returns a page of traits matching any (or all, when {@code matchAll}) of the given categories. */
    Page<TraitResponseDto> getTraitsByCategory(List<TraitCategory> categories, boolean matchAll, int page, int size);

    /** Returns a page of traits matching every filter set on the request, ranked by name when searching. */
    Page<TraitResponseDto> filterTraits(TraitFilterRequest request);

    /** Creates a new trait with its categories and returns it. */
    TraitResponseDto createTrait(CreateTraitRequest request);

    /** Returns one trait by its UUID string. */
    TraitResponseDto getTraitById(String id);

    /** Replaces an existing trait's fields and categories and returns the updated trait. */
    TraitResponseDto updateTrait(String id, CreateTraitRequest request);

    /** Deletes a trait (and its category links) and returns a confirmation message. */
    String deleteTrait(String id);
}
