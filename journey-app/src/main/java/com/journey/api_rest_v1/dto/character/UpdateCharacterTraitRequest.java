package com.journey.api_rest_v1.dto.character;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for changing a character's trait; only the fields that are set change.
 * The trait fields (trait_name ... lvl_up_effect) become the character's own version of the compendium trait.
 * reset lists fields to return to the compendium value (e.g. ["trait_effect"], or ["all"]).
 */
public record UpdateCharacterTraitRequest(
    @Min(1) @Max(6) Integer lvl,
    @Min(0) Integer exp_spent,
    String notes,
    String trait_name,
    @Min(0) Integer purchase_cost,
    @Min(0) Integer lvl_up_cost,
    String trait_desc,
    @Min(0) Integer trait_uses,
    @Min(0) Integer mp_cost,
    @Min(0) Integer ep_cost,
    @Min(0) Integer tp_cost,
    String trait_effect,
    String lvl_up_effect,
    List<String> reset
) {
}
