package com.journey.api_rest_v1.dto.character;

import java.io.Serial;
import java.io.Serializable;

import com.journey.api_rest_v1.domain.characters.SkillCategory;

/**
 * A skill of the game.
 */
public record SkillDto(
    String id,
    String name,
    SkillCategory category,
    int baseRating,
    String weaponTypeId
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
