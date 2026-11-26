package com.journey.api_rest_v1.dto.character;

import jakarta.validation.constraints.Min;

/**
 * Request body for one attribute; only the fields that are set change.
 */
public record UpdateCharacterAttributeRequest(
    @Min(0) Integer exp_spent,
    @Min(0) Integer job_points_spent,
    Integer bonus,
    Integer modifier
) {
}
