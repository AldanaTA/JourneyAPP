package com.journey.api_rest_v1.dto.character;

import java.io.Serial;
import java.io.Serializable;

/**
 * A starting character sheet and its attributes.
 */
public record CharacterSheetDto(
    String id,
    String name,
    String description,
    int baseBody,
    int baseStrength,
    int baseSpeed,
    int baseMagic
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
