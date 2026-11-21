package com.journey.api_rest_v1.dto.trait;

import java.util.List;

import com.journey.api_rest_v1.domain.traits.TraitCategory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body used to create or fully update a trait.
 * Field names use snake_case to match {@code CreatePowerRequest} and the database columns.
 */
public record CreateTraitRequest(
    @NotBlank(message = "Trait name" + CreateTraitRequest.ERROR_MESSAGE_NOT_BLANK) String trait_name,
    @NotNull(message = "LVL" + CreateTraitRequest.ERROR_MESSAGE_NOT_BLANK) @Min(1) Integer lvl,
    @NotNull(message = "Purchase cost" + CreateTraitRequest.ERROR_MESSAGE_NOT_BLANK) @Min(0) Integer purchase_cost,
    @NotNull(message = "LVL up cost" + CreateTraitRequest.ERROR_MESSAGE_NOT_BLANK) @Min(0) Integer lvl_up_cost,
    @NotBlank(message = "Trait description" + CreateTraitRequest.ERROR_MESSAGE_NOT_BLANK) String trait_desc,
    @Min(0) Integer trait_uses,
    @Min(0) Integer mp_cost,
    @Min(0) Integer ep_cost,
    @Min(0) Integer tp_cost,
    @NotBlank(message = "Trait effect" + CreateTraitRequest.ERROR_MESSAGE_NOT_BLANK) String trait_effect,
    String lvl_up_effect,
    List<TraitCategory> categories,
    @NotBlank(message = "Content source id" + CreateTraitRequest.ERROR_MESSAGE_NOT_BLANK) String content_source_id
) {
    static final String ERROR_MESSAGE_NOT_BLANK = " field must not be blank";
}
