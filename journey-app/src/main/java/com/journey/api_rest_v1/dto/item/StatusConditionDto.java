package com.journey.api_rest_v1.dto.item;

import java.io.Serial;
import java.io.Serializable;

/** A status condition and its rules text. */
public record StatusConditionDto(String name, String description) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
