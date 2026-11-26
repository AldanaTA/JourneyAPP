package com.journey.api_rest_v1.dto.character;

import java.util.List;

/**
 * A power as it applies to the character: their edits, otherwise the compendium values.
 */
public record CharacterPowerDto(
    String powerId,
    int lvl,
    int expSpent,
    String notes,
    String name,
    String type,
    Integer tpCost,
    Integer hpCost,
    Integer mpCost,
    Integer epCost,
    String materialComponents,
    Boolean verbal,
    Boolean sight,
    Boolean somatic,
    Boolean isDistinct,
    Boolean concentration,
    String range,
    String area,
    String duration,
    String effect,
    String empowerEffect,
    String lvlUpEffect,
    List<String> customizedFields
) {
}
