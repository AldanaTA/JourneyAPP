package com.journey.api_rest_v1.service;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.api.dto.power.CreatePowerRequest;
import java.util.List;

public interface PowerService {

    Power createPower(CreatePowerRequest request);
    
    List<Power> getAllPowers();

    Power getPowerById(String id);

    Power getPowerByName(String name);
}
