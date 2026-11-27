package com.journey.api_rest_v1.dto.character;

import java.util.List;

import com.journey.api_rest_v1.domain.characters.CharacterTraitSource;

/**
 * A trait as it applies to the character: their edits, otherwise the compendium values.
 * customizedFields names the fields the character changed (empty when it matches the compendium).
 */
public record CharacterTraitDto(
    String traitId,
    CharacterTraitSource source,
    String raceId,
    String jobId,
    int lvl,
    int expSpent,
    int jobPointsSpent,
    String notes,
    /** Uses spent since the last rest. */
    int usesSpent,
    String traitName,
    Integer purchaseCost,
    Integer lvlUpCost,
    String traitDesc,
    Integer traitUses,
    Integer mpCost,
    Integer epCost,
    Integer tpCost,
    String traitEffect,
    String lvlUpEffect,
    List<String> customizedFields
) {
}
