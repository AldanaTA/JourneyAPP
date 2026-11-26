package com.journey.api_rest_v1.dto.character;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for changing a character's power; only the fields that are set change.
 * The power fields become the character's own version of the compendium power (set a cost to 0 to remove it).
 * reset lists fields to return to the compendium value (or ["all"]).
 */
public record UpdateCharacterPowerRequest(
    @Min(1) Integer lvl,
    @Min(0) Integer exp_spent,
    String notes,
    String name,
    @Pattern(regexp = "Destruction|Support|Sabotage|Utility") String type,
    @Min(0) Integer tp_cost,
    @Min(0) Integer hp_cost,
    @Min(0) Integer mp_cost,
    @Min(0) Integer ep_cost,
    String material_components,
    Boolean verbal,
    Boolean sight,
    Boolean somatic,
    Boolean is_distinct,
    Boolean concentration,
    String range,
    String area,
    String duration,
    String effect,
    String empower_effect,
    String lvl_up_effect,
    List<String> reset
) {
}
