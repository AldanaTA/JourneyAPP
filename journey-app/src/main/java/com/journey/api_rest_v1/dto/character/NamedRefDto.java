package com.journey.api_rest_v1.dto.character;

/**
 * A reference to another record by id and display name.
 */
public record NamedRefDto(
    String id,
    String name
) {
}
