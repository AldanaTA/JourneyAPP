package com.journey.api_rest_v1.service;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.api.dto.power.CreatePowerRequest;

public interface PowerService {

    Power createPower(CreatePowerRequest request);
    
    Power getPowerById(String id);

    Power getPowerByName(String name);
}
