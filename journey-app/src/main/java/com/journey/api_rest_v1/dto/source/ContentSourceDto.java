package com.journey.api_rest_v1.dto.source;

/** A content source the user can choose for a character (kind: core, book, or campaign). */
public record ContentSourceDto(
    String id,
    String name,
    String description,
    String kind
) {
}
