package com.journey.api_rest_v1.dto.character;

import com.journey.api_rest_v1.domain.characters.CharacterAttributeName;

/**
 * One attribute: its sheet base, what was spent on it, and the resulting value.
 */
public record CharacterAttributeDto(
    CharacterAttributeName attribute,
    int base,
    int expSpent,
    int jobPointsSpent,
    int bonus,
    int modifier,
    int value
) {
}
