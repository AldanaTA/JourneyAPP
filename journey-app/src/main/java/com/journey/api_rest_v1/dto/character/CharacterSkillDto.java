package com.journey.api_rest_v1.dto.character;

import com.journey.api_rest_v1.domain.characters.SkillCategory;

/**
 * One skill of the character with its final rating (never below 5).
 */
public record CharacterSkillDto(
    String skillId,
    String name,
    SkillCategory category,
    int rating,
    int baseRating,
    int creationAdjustment,
    int expSpent,
    int jobPointsSpent,
    int failures,
    int permanentModifier,
    int temporaryModifier
) {
}
