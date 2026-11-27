package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.NotBlank;

public record CheckImportRequest(
    @NotBlank String character_id
) {
}
