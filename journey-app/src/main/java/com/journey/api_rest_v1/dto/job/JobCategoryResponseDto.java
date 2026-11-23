package com.journey.api_rest_v1.dto.job;

import java.io.Serial;
import java.io.Serializable;

/**
 * A job category as returned by {@code GET /api/v1/jobs/categories}, used to populate filter options.
 * It is Serializable so it can be stored in the Redis cache.
 */
public record JobCategoryResponseDto(
    String id,
    String category,
    String description
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
