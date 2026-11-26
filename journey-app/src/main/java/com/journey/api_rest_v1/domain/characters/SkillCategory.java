package com.journey.api_rest_v1.domain.characters;

/** Whether a skill is a combat or a life skill; creation swaps stay within one category. Mirrors the Postgres {@code skill_category} enum. */
public enum SkillCategory {
    COMBAT,
    LIFE
}
