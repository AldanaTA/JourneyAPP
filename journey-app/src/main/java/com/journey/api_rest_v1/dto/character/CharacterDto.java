package com.journey.api_rest_v1.dto.character;

import java.util.List;

import com.journey.api_rest_v1.domain.characters.CharacterStatus;
import com.journey.api_rest_v1.domain.races.RaceSize;

/**
 * A whole character sheet.
 */
public record CharacterDto(
    String id,
    String name,
    CharacterStatus status,
    String description,
    String motive,
    String ideals,
    String strengths,
    String flaws,
    String history,
    String importantCharacters,
    String notes,
    NamedRefDto characterSheet,
    NamedRefDto job,
    RaceSize size,
    List<CharacterRaceDto> races,
    NamedRefDto languageRace,
    String raceLanguages,
    String learnedLanguages,
    int startingExp,
    int jobPoints,
    CharacterCapacitiesDto capacities,
    CharacterStatsDto stats,
    CharacterBudgetDto budget,
    List<CharacterAttributeDto> attributes,
    List<CharacterSkillDto> skills,
    List<CharacterTraitDto> traits,
    List<CharacterPowerDto> powers,
    String createdAt,
    String updatedAt
) {
}
