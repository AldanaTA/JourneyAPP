package com.journey.api_rest_v1.service.impl;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.journey.api_rest_v1.api.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategories;
import com.journey.api_rest_v1.domain.powers.PowerCategoriesId;
import com.journey.api_rest_v1.repository.PowerCategoriesRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.service.PowerService;

@Service
public class PowerServiceImpl implements PowerService {

    private final PowerRepository powerRepository;
    private final PowerCategoriesRepository powerCategoriesRepository;

    public PowerServiceImpl(PowerRepository powerRepository, PowerCategoriesRepository powerCategoriesRepository) {
        this.powerRepository = powerRepository;
        this.powerCategoriesRepository = powerCategoriesRepository;
    }

    @Override
    @Transactional
    public Power createPower(CreatePowerRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        Power power = new Power(
                null,
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
                new java.util.ArrayList<>(),
                request.content_source(),
                now,
                now
        );

        Power savedPower = powerRepository.save(power);

        List<PowerCategories> powerCategories = request.categories() == null ? List.of()
                : request.categories().stream()
                        .map(category -> {
                            PowerCategories link = new PowerCategories(
                                    new PowerCategoriesId(savedPower.getId(), category),
                                    savedPower,
                                    category,
                                    now,
                                    now
                            );
                            savedPower.getCategories().add(link);
                            return link;
                        })
                        .toList();

        if (!powerCategories.isEmpty()) {
            powerCategoriesRepository.saveAll(powerCategories);
        }

        return savedPower;
    }

    @Override
    public Power getPowerById(String id) {
        return powerRepository.findById(id).orElse(null);
    }

    @Override
    public Power getPowerByName(String name) {
        return powerRepository.findByName(name).orElse(null);
    }

}
