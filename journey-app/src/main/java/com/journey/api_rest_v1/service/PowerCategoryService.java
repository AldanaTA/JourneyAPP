package com.journey.api_rest_v1.service;

import org.springframework.stereotype.Service;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategory;

@Service
public interface PowerCategoryService {

    Power CreatePowerCategory(PowerCategory powerCategory);
}
