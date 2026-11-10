package com.journey.api_rest_v1.mapper.impl;

import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategories;
import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;
import com.journey.api_rest_v1.mapper.PowerMapper;

@Component
public class PowerMapperImpl implements PowerMapper {

    public PowerResponseDto CreateResponse(Power power) {
        return new PowerResponseDto(
            power.getId() == null ? null : power.getId().toString(),
            power.getName(),
            power.getLvl(),
            power.getType().name(),
            power.getTpCost(),
            power.getHpCost(),
            power.getMpCost(),
            power.getEpCost(),
            power.getMaterialComponents(),
            power.getVerbal(),
            power.getSight(),
            power.getSomatic(),
            power.getIsDistinct(),
            power.getConcentration(),
            power.getRange(),
            power.getArea(),
            power.getDuration(),
            power.getEffect(),
            power.getEmpowerEffect(),
            power.getLvlUpEffect(),
            power.getCategories().stream()
                .map(PowerCategories::getCategory)
                .toList(),
            power.getSource() == null ? null : power.getSource().getSource_name()
        );
    }

    @Override
    public CreatePowerRequest CreateRequest(CreatePowerRequest request) {
        return new CreatePowerRequest(
            request.name(),
            request.lvl(),
            request.type(),
            request.tp_cost(),
            request.hp_cost(),
            request.mp_cost(),
            request.ep_cost(),
            request.material_components(),
            request.verbal(),
            request.sight(),
            request.somatic(),
            request.is_distinct(),
            request.concentration(),
            request.range(),
            request.area(),
            request.duration(),
            request.effect(),
            request.empower_effect(),
            request.lvl_up_effect(),
            request.categories(),
            request.content_source_id()
        );
    }
}
