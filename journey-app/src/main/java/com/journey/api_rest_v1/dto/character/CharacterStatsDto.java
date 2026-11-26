package com.journey.api_rest_v1.dto.character;

/**
 * Attribute values and derived stats, computed with the character sheet formulas.
 */
public record CharacterStatsDto(
    int body,
    int strength,
    int speed,
    int magic,
    int maxHp,
    int maxWp,
    int maxEp,
    int maxMp,
    int physicalDamage,
    int magicDamage,
    int priority,
    int movementFt
) {
}
