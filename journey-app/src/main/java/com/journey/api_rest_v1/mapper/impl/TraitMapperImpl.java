package com.journey.api_rest_v1.mapper.impl;

import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.domain.traits.TraitCategories;
import com.journey.api_rest_v1.dto.trait.CreateTraitRequest;
import com.journey.api_rest_v1.dto.trait.TraitResponseDto;
import com.journey.api_rest_v1.mapper.TraitMapper;

/**
 * Default {@link TraitMapper} implementation that maps fields by hand.
 */
@Component
public class TraitMapperImpl implements TraitMapper {

    /** Converts a trait entity into a response DTO, flattening categories and the content source name. */
    @Override
    public TraitResponseDto CreateResponse(Trait trait) {
        return new TraitResponseDto(
            trait.getId() == null ? null : trait.getId().toString(),
            trait.getTraitName(),
            trait.getLvl(),
            trait.getPurchaseCost(),
            trait.getLvlUpCost(),
            trait.getTraitDesc(),
            trait.getTraitUses(),
            trait.getMpCost(),
            trait.getEpCost(),
            trait.getTpCost(),
            trait.getTraitEffect(),
            trait.getLvlUpEffect(),
            trait.getCategories().stream()
                .map(TraitCategories::getCategory)
                .toList(),
            trait.getSource() == null ? null : trait.getSource().getSource_name()
        );
    }

    /** Returns a field-by-field copy of the given create request. */
    @Override
    public CreateTraitRequest CreateRequest(CreateTraitRequest request) {
        return new CreateTraitRequest(
            request.trait_name(),
            request.lvl(),
            request.purchase_cost(),
            request.lvl_up_cost(),
            request.trait_desc(),
            request.trait_uses(),
            request.mp_cost(),
            request.ep_cost(),
            request.tp_cost(),
            request.trait_effect(),
            request.lvl_up_effect(),
            request.categories(),
            request.content_source_id()
        );
    }
}
