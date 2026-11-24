package com.journey.api_rest_v1.dto.item;

import java.io.Serial;
import java.io.Serializable;

/** A weapon property and its rules text (e.g. Finesse). */
public record WeaponPropertyDto(String name, String description) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
