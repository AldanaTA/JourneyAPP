package com.journey.api_rest_v1.dto.creature;

import java.io.Serial;
import java.io.Serializable;

/** A creature type and the content source that defines it. */
public record CreatureTypeDto(String id, String name, String description, String contentSourceName)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
