package com.journey.api_rest_v1.service.impl;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.journey.api_rest_v1.dto.item.StatusConditionDto;
import com.journey.api_rest_v1.repository.StatusConditionRepository;
import com.journey.api_rest_v1.service.StatusConditionService;

/**
 * Default {@link StatusConditionService}: status conditions are seeded by the schema, so the list is cached.
 */
@Service
public class StatusConditionServiceImpl implements StatusConditionService {

    private final StatusConditionRepository statusConditionRepository;

    /** Wires the status condition repository. */
    public StatusConditionServiceImpl(StatusConditionRepository statusConditionRepository) {
        this.statusConditionRepository = statusConditionRepository;
    }

    /** Returns every status condition, sorted by name, served from the cache when possible. */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "statusConditions")
    public List<StatusConditionDto> getStatusConditions() {
        return statusConditionRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(condition -> new StatusConditionDto(condition.getName(), condition.getDescription()))
                .toList();
    }
}
