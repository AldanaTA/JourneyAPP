package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.Size;

/** A campaign's rule book; on change, only the set fields change (a title is required to create one). */
public record SaveRuleBookRequest(
    @Size(max = 200) String title,
    @Size(max = 5000) String description
) {
}
