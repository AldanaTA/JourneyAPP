package com.journey.api_rest_v1.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.journey.api_rest_v1.domain.content_source.ContentSource;
import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategories;
import com.journey.api_rest_v1.domain.powers.PowerCategoriesId;
import com.journey.api_rest_v1.domain.powers.PowerCategory;
import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.dto.power.PowerFilterRequest;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;
import com.journey.api_rest_v1.mapper.PowerMapper;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.PowerCategoriesRepository;
import com.journey.api_rest_v1.repository.PowerRepository;
import com.journey.api_rest_v1.service.PowerService;
import com.journey.api_rest_v1.specification.PowerSpecifications;

@Service
public class PowerServiceImpl implements PowerService {

    private final PowerRepository powerRepository;
    private final PowerCategoriesRepository powerCategoriesRepository;
    private final ContentSourceRepository contentSourceRepository;
    private final PowerMapper powerMapper;

    public PowerServiceImpl(PowerRepository powerRepository,
                           PowerCategoriesRepository powerCategoriesRepository,
                           ContentSourceRepository contentSourceRepository,
                           PowerMapper powerMapper) {
        this.powerRepository = powerRepository;
        this.powerCategoriesRepository = powerCategoriesRepository;
        this.contentSourceRepository = contentSourceRepository;
        this.powerMapper = powerMapper;
    }

    @Override
    @Transactional
    public PowerResponseDto createPower(CreatePowerRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID contentSourceId = UUID.fromString(request.content_source_id());
        ContentSource contentSource = contentSourceRepository.findById(contentSourceId)
                .orElseThrow(() -> new RuntimeException("Content source not found with id: " + request.content_source_id()));

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
                contentSource,
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

        return powerMapper.CreateResponse(savedPower);
    }

    @Override
    @Transactional 
    public PowerResponseDto updatePower(String id, CreatePowerRequest request) {
       OffsetDateTime now = OffsetDateTime.now();
       UUID powerId = UUID.fromString(id);
       UUID contentSourceId = UUID.fromString(request.content_source_id());

        Power existingPower = powerRepository.findById(powerId)
                .orElseThrow(() -> new RuntimeException("Power not found with id: " + id));

        ContentSource contentSource = contentSourceRepository.findById(contentSourceId)
                .orElseThrow(() -> new RuntimeException("Content source not found with id: " + request.content_source_id()));

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
        existingPower.setSource(contentSource);
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

        return powerMapper.CreateResponse(powerRepository.save(existingPower));
    }

    @Override
    public String deletePower(String id) {
        UUID powerId = UUID.fromString(id);
        Power existingPower = powerRepository.findById(powerId)
                .orElseThrow(() -> new RuntimeException("Power not found with id: " + id));
        String name = existingPower.getName();
        // Delete associated categories first
        powerCategoriesRepository.deleteAll(existingPower.getCategories());

        // Then delete the power
        powerRepository.delete(existingPower);

        return "Power with id " + id + " (name: " + name + ") has been deleted.";
    }

    @Override
    public PowerResponseDto getPowerById(String id) {
        UUID powerId = UUID.fromString(id);
        return powerMapper.CreateResponse(powerRepository.findById(powerId)
                .orElseThrow(() -> new RuntimeException("Power not found with id: " + id)));
    }

    @Override
    public PowerResponseDto getPowerByName(String name) {
        return powerMapper.CreateResponse(powerRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Power not found with name: " + name)));
    }

    @Override
    public Page<PowerResponseDto> getAllPowers(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDir), sortBy));
        return powerRepository.findAll(pageable).map(powerMapper::CreateResponse);
    }

    @Override
    public Page<PowerResponseDto> getPowersByCategory(List<PowerCategory> categories, boolean matchAll, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        if (matchAll) {
            return powerRepository.findPowersWithAllCategories(categories, categories.size(), pageable)
                    .map(powerMapper::CreateResponse);
        } else {
            return powerRepository.findByCategories_CategoryIn(categories, pageable)
                    .map(powerMapper::CreateResponse);
        }
    }

    @Override
    public Page<PowerResponseDto> filterPowers(PowerFilterRequest request) {
        Specification<Power> specification = (root, query, cb) -> cb.conjunction();

        if (request.categories() != null && !request.categories().isEmpty()) {
            specification = specification.and(PowerSpecifications.withCategories(request.categories(), request.getMatchAll()));
        }

        if (request.tpCost() != null && request.tpCost() > 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("tpCost", request.getTpOperator(), request.tpCost()));
        }

        if (request.hpCost() != null && request.hpCost() > 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("hpCost", request.getHpOperator(), request.hpCost()));
        }

        if (request.mpCost() != null && request.mpCost() > 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("mpCost", request.getMpOperator(), request.mpCost()));
        }

        if (request.epCost() != null && request.epCost() > 0) {
            specification = specification.and(
                PowerSpecifications.withResourceFilter("epCost", request.getEpOperator(), request.epCost()));
        }

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), Sort.by(Sort.Direction.ASC, "name"));
        return powerRepository.findAll(specification, pageable).map(powerMapper::CreateResponse);
    }

}
