package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCampaignRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 5000) String description
) {
}
