package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.Size;

/** Only the fields that are set change. */
public record UpdateCampaignRequest(
    @Size(max = 100) String name,
    @Size(max = 5000) String description,
    Boolean join_enabled
) {
}
