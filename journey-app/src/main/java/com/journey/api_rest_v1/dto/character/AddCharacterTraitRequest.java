package com.journey.api_rest_v1.dto.character;

import com.journey.api_rest_v1.domain.characters.CharacterTraitSource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for giving a character a trait.
 * - RACIAL: race_id is one of the character's races and the race lists the trait. Free.
 * - JOB: the character's job lists the trait; it costs its purchase cost in job points.
 * - PURCHASED: any trait; exp_spent defaults to its purchase cost (0 for a trait the storyteller grants).
 */
public record AddCharacterTraitRequest(
    @NotBlank String trait_id,
    @NotNull CharacterTraitSource source,
    String race_id,
    @Min(1) @Max(6) Integer lvl,
    @Min(0) Integer exp_spent
) {
}
