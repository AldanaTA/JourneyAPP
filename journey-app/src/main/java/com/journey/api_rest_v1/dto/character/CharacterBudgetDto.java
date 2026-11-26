package com.journey.api_rest_v1.dto.character;

/**
 * Where the character's EXP and job points went, and what is left.
 */
public record CharacterBudgetDto(
    int totalExp,
    int attributeExp,
    int skillExp,
    int traitExp,
    int powerExp,
    int expRemaining,
    int jobPoints,
    int jobTraitPoints,
    int jobPointsOnStats,
    int jobPointsRemaining,
    int raceCount,
    int racialTraitCost,
    int combatSkillAdjustment,
    int lifeSkillAdjustment
) {
}
