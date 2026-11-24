package com.journey.api_rest_v1.service;

import java.util.List;

import com.journey.api_rest_v1.dto.item.StatusConditionDto;

/**
 * Read access to the status conditions (core rules shared by creatures and other content).
 */
public interface StatusConditionService {

    /** Returns every status condition, sorted by name. */
    List<StatusConditionDto> getStatusConditions();
}
