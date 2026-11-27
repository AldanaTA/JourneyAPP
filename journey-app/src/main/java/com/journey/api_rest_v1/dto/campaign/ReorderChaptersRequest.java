package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** Every chapter of the book, in the new reading order. */
public record ReorderChaptersRequest(
    @NotNull List<String> chapter_ids
) {
}
