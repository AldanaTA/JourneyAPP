package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** A campaign's character sheet (starting attributes); on change, only the set fields change. */
public record SaveSheetRequest(
    @Size(max = 100) String name,
    @Size(max = 2000) String description,
    @Min(0) @Max(1000) Integer base_body,
    @Min(0) @Max(1000) Integer base_strength,
    @Min(0) @Max(1000) Integer base_speed,
    @Min(0) @Max(1000) Integer base_magic
) {
}
