package com.journey.api_rest_v1.dto.rules;

import java.io.Serial;
import java.io.Serializable;

/** Metadata for an image used in a chapter; the file is served by GET /api/v1/assets/{id}. */
public record RuleAssetDto(String id, String contentType, Integer width, Integer height, String altText)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
