package com.journey.api_rest_v1.dto.character;

import jakarta.validation.constraints.Min;

/**
 * Request body for one skill; only the fields that are set change. creation_adjustment is the creation
 * swap (lowered by at most 20; the swaps must balance within combat and within life skills to finish creation).
 */
public record UpdateCharacterSkillRequest(
    @Min(-20) Integer creation_adjustment,
    @Min(0) Integer exp_spent,
    @Min(0) Integer job_points_spent,
    @Min(0) Integer failures,
    Integer permanent_modifier,
    Integer temporary_modifier
) {
}
