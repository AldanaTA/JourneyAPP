package com.journey.api_rest_v1.dto.character;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for giving a character a power. exp_spent defaults to 10 (the cost of a power).
 */
public record AddCharacterPowerRequest(
    @NotBlank String power_id,
    @Min(1) Integer lvl,
    @Min(0) Integer exp_spent
) {
}
