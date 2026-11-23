package com.journey.api_rest_v1.mapper.impl;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.domain.races.Race;
import com.journey.api_rest_v1.domain.races.RaceTraits;
import com.journey.api_rest_v1.dto.race.CreateRaceRequest;
import com.journey.api_rest_v1.dto.race.RaceResponseDto;
import com.journey.api_rest_v1.mapper.RaceMapper;
import com.journey.api_rest_v1.mapper.TraitMapper;

/**
 * Default {@link RaceMapper} implementation that maps fields by hand.
 * Traits are converted with {@link TraitMapper} so a race embeds the same trait payload as the trait endpoints.
 */
@Component
public class RaceMapperImpl implements RaceMapper {

    private final TraitMapper traitMapper;

    /** Wires the trait mapper used for the race's embedded traits. */
    public RaceMapperImpl(TraitMapper traitMapper) {
        this.traitMapper = traitMapper;
    }

    /** Converts a race entity into a response DTO, ordering traits by sort order. */
    @Override
    public RaceResponseDto CreateResponse(Race race) {
        return new RaceResponseDto(
            race.getId() == null ? null : race.getId().toString(),
            race.getRaceName(),
            race.getRaceSize(),
            race.getMovement(),
            race.getLanguages(),
            race.getRaceDescription(),
            race.getTraits().stream()
                .sorted(Comparator.comparing(RaceTraits::getSortOrder))
                .map(link -> traitMapper.CreateResponse(link.getTrait()))
                .toList(),
            race.getSource() == null ? null : race.getSource().getSource_name()
        );
    }

    /** Returns a field-by-field copy of the given create request. */
    @Override
    public CreateRaceRequest CreateRequest(CreateRaceRequest request) {
        return new CreateRaceRequest(
            request.race_name(),
            request.race_size(),
            request.movement(),
            request.languages(),
            request.race_description(),
            request.trait_ids(),
            request.content_source_id()
        );
    }
}
