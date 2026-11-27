package com.journey.api_rest_v1.dto.character;

import java.util.List;

import com.journey.api_rest_v1.domain.races.RaceSize;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a character (a draft). Everything but the name can be filled in later.
 * starting_exp and job_points default to 150 and 50.
 */
public record CreateCharacterRequest(
    @NotBlank @Size(max = 100) String name,
    String character_sheet_id,
    String job_id,
    RaceSize size,
    @Min(0) Integer starting_exp,
    @Min(0) Integer job_points,
    String description,
    String motive,
    String ideals,
    String strengths,
    String flaws,
    String learned_languages,
    String history,
    String important_characters,
    String notes,
    /** Content sources (besides the core rules) to build the character from. */
    List<String> source_ids,
    /** Create the character inside this campaign (the caller must be a member); its sources are the campaign's. */
    String campaign_id
) {
}
