package com.journey.api_rest_v1.service;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.api.dto.power.CreatePowerRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PowerService {

    Page<Power>getAllPowers(Pageable pageable);

    Power createPower(CreatePowerRequest request);

    Power getPowerById(String id);

    Power getPowerByName(String name);

    Power updatePower(String id, CreatePowerRequest request);

    String deletePower(String id);

}
