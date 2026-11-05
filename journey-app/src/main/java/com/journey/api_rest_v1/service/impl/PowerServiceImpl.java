package com.journey.api_rest_v1.service.impl;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategories;
import com.journey.api_rest_v1.domain.powers.PowerCategoriesId;
import com.journey.api_rest_v1.domain.powers.PowerCategory;
import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
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
    @Transactional 
    public Power updatePower(String id, CreatePowerRequest request) {
       OffsetDateTime now = OffsetDateTime.now();

        Power existingPower = powerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Power not found with id: " + id));

        existingPower.setName(request.name());
        existingPower.setLvl(request.lvl());
        existingPower.setType(request.type());
        existingPower.setTpCost(request.tp_cost());
        existingPower.setHpCost(request.hp_cost());
        existingPower.setMpCost(request.mp_cost());
        existingPower.setEpCost(request.ep_cost());
        existingPower.setMaterialComponents(request.material_components());
        existingPower.setVerbal(request.verbal());
        existingPower.setSight(request.sight());
        existingPower.setSomatic(request.somatic());
        existingPower.setIsDistinct(request.is_distinct());
        existingPower.setConcentration(request.concentration());
        existingPower.setRange(request.range());
        existingPower.setArea(request.area());
        existingPower.setDuration(request.duration());
        existingPower.setEffect(request.effect());
        existingPower.setEmpowerEffect(request.empower_effect());
        existingPower.setLvlUpEffect(request.lvl_up_effect());
        existingPower.setSource(request.content_source());
        existingPower.setUpdatedAt(now);

        // Update categories
        List<PowerCategories> updatedCategories = request.categories() == null ? List.of()
                : request.categories().stream()
                        .map(category -> {
                            PowerCategories link = new PowerCategories(
                                    new PowerCategoriesId(existingPower.getId(), category),
                                    existingPower,
                                    category,
                                    now,
                                    now
                            );
                            return link;
                        })
                        .toList();

        // Remove old categories and add new ones
        powerCategoriesRepository.deleteAll(existingPower.getCategories());
        if (!updatedCategories.isEmpty()) {
            powerCategoriesRepository.saveAll(updatedCategories);
            existingPower.getCategories().clear();
            existingPower.getCategories().addAll(updatedCategories);
        }

        return powerRepository.save(existingPower);
    }

    @Override
    public String deletePower(String id) {
        Power existingPower = powerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Power not found with id: " + id));
        String name = existingPower.getName();
        // Delete associated categories first
        powerCategoriesRepository.deleteAll(existingPower.getCategories());

        // Then delete the power
        powerRepository.delete(existingPower);

        return "Power with id " + id + " (name: " + name + ") has been deleted.";
    }

    @Override
    public Power getPowerById(String id) {
        return powerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Power not found with id: " + id));
    }

    @Override
    public Power getPowerByName(String name) {
        return powerRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Power not found with name: " + name));
    }
    @Override
    public Page<Power> getAllPowers(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return powerRepository.findAll(pageable);
    }

    @Override
    public Page<Power> getPowersByCategory(List<PowerCategory> categories, boolean matchAll, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (matchAll) {
            return powerRepository.findPowersWithAllCategories(categories, categories.size(), pageable);
        } else {
            return powerRepository.findByCategories_CategoriesIn(categories, pageable);
        }
    }




}
