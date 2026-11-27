package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
    @NotBlank @Size(max = 4000) String body
) {
}
