package com.journey.api_rest_v1.dto.character;

/**
 * Current capacities and the bonuses added to maximums and derived stats.
 */
public record CharacterCapacitiesDto(
    Integer currentHp,
    Integer currentWp,
    Integer currentEp,
    Integer currentMp,
    int tempHp,
    int hpBonus,
    int wpBonus,
    int epBonus,
    int mpBonus,
    int physicalDamageBonus,
    int magicDamageBonus,
    int priorityBonus,
    int movementBonus
) {
}
