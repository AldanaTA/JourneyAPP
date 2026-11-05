package com.journey.api_rest_v1.service;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategory;
import com.journey.api_rest_v1.dto.power.CreatePowerRequest;

import java.util.List;
import org.springframework.data.domain.Page;

public interface PowerService {

    Page<Power>getAllPowers(int page, int size, String sortBy, String sortDir);

    Page<Power> getPowersByCategory(List<PowerCategory> categories, boolean matchAll, int page, int size);
    Power createPower(CreatePowerRequest request);

    Power getPowerById(String id);

    Power getPowerByName(String name);

    Power updatePower(String id, CreatePowerRequest request);

    String deletePower(String id);

}
