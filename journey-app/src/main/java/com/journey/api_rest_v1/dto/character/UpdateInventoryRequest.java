package com.journey.api_rest_v1.dto.character;

import jakarta.validation.constraints.Min;

/**
 * Request body for a character's money, base attunement limit (3 by the rules; extra attunements are attunement
 * slots), and stack-limit multiplier (2 for Supply Sergeant); only the fields that are set change.
 */
public record UpdateInventoryRequest(
    @Min(0) Integer platinum,
    @Min(0) Integer gold,
    @Min(0) Integer silver,
    @Min(0) Integer bronze,
    @Min(0) Integer attunement_limit,
    @Min(1) Integer stack_limit_multiplier,
    String notes
) {
}
