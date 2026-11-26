package com.journey.api_rest_v1.dto.character;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * Request body for setting a character's races: one, or several for a mixed race (in display order).
 * language_race_id picks whose language set the character speaks; with one race it defaults to that race.
 * A single race gets all its racial traits automatically; a mixed race picks them with POST /traits.
 */
public record SetCharacterRacesRequest(
    @NotEmpty List<String> race_ids,
    String language_race_id
) {
}
