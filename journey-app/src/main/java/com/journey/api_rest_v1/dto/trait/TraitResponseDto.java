package com.journey.api_rest_v1.dto.trait;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.journey.api_rest_v1.domain.traits.TraitCategory;

/**
 * Trait payload returned to clients by the trait endpoints.
 * It is Serializable so it can be stored in the Redis cache.
 */
public record TraitResponseDto(
    String id,
    String traitName,
    Integer lvl,
    Integer purchaseCost,
    Integer lvlUpCost,
    String traitDesc,
    Integer traitUses,
    Integer mpCost,
    Integer epCost,
    Integer tpCost,
    String traitEffect,
    String lvlUpEffect,
    List<TraitCategory> categories,
    String contentSourceName
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
