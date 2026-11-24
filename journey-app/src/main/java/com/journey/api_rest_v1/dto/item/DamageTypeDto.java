package com.journey.api_rest_v1.dto.item;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** A damage type, its effect, and the skills that defend against it. */
public record DamageTypeDto(String name, String effect, List<String> defenses) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
