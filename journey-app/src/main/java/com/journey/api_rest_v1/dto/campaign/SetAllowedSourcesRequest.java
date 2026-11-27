package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** The sources characters may use besides the core rules and the campaign's own content (replaces the list). */
public record SetAllowedSourcesRequest(
    @NotNull List<String> source_ids
) {
}
