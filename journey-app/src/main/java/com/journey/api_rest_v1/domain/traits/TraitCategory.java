package com.journey.api_rest_v1.domain.traits;

/**
 * All categories a trait can be tagged with, mirroring the Postgres {@code trait_category} enum.
 * Values are stored lowercase in the database via {@link TraitCategoryConverter}.
 */
public enum TraitCategory {
    DAMAGE,
    DEFENSE,
    HEALING_RECOVERY,
    BUFF,
    DEBUFF,
    CONTROL,
    MOBILITY,
    WEAPON,
    MAGIC,
    SUMMONING_COMPANIONS,
    TRANSFORMATION,
    RESOURCE_ECONOMY,
    SKILL,
    UTILITY_EXPLORATION,
    CRAFTING_ITEMS,
    STEALTH_DECEPTION,
    SOCIAL_ROLEPLAY,
    STATUS_CONDITIONS,
    ENVIRONMENTAL,
    PASSIVE,
    DECLARED,
    TRIGGERED,
    OTHER
}
