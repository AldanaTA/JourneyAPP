package com.journey.api_rest_v1.dto.job;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.journey.api_rest_v1.dto.trait.TraitResponseDto;

/**
 * Job payload returned to clients by the job endpoints.
 * {@code categories} lists the primary category first; {@code traits} are in the job's display order.
 * It is Serializable so it can be stored in the Redis cache.
 */
public record JobResponseDto(
    String id,
    String jobName,
    String jobDesc,
    String primaryCategory,
    List<String> categories,
    List<TraitResponseDto> traits,
    String contentSourceName
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
