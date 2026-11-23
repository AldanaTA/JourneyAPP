package com.journey.api_rest_v1.dto.race;

import java.util.List;

import com.journey.api_rest_v1.domain.races.RaceSize;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body used to create or fully update a race.
 * Field names use snake_case to match {@code CreateJobRequest} and the database columns.
 *
 * <p>{@code trait_ids} are existing trait UUIDs, in display order; every linked trait is tagged
 * {@code RACIAL}. {@code movement} defaults to 30 and {@code languages} to "Common" like the columns.</p>
 */
public record CreateRaceRequest(
    @NotBlank(message = "Race name" + CreateRaceRequest.ERROR_MESSAGE_NOT_BLANK) String race_name,
    @NotNull(message = "Race size" + CreateRaceRequest.ERROR_MESSAGE_NOT_BLANK) RaceSize race_size,
    @Min(0) Integer movement,
    String languages,
    String race_description,
    List<String> trait_ids,
    @NotBlank(message = "Content source id" + CreateRaceRequest.ERROR_MESSAGE_NOT_BLANK) String content_source_id
) {
    static final String ERROR_MESSAGE_NOT_BLANK = " field must not be blank";
}
