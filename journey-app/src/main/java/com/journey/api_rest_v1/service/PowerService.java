package com.journey.api_rest_v1.service;

import com.journey.api_rest_v1.domain.powers.PowerCategory;
import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.dto.power.PowerFilterRequest;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;

import java.util.List;
import org.springframework.data.domain.Page;

public interface PowerService {

    Page<PowerResponseDto> getAllPowers(int page, int size, String sortBy, String sortDir);

    Page<PowerResponseDto> getPowersByCategory(List<PowerCategory> categories, boolean matchAll, int page, int size);

    Page<PowerResponseDto> filterPowers(PowerFilterRequest request);

    PowerResponseDto createPower(CreatePowerRequest request);

    PowerResponseDto getPowerById(String id);

    PowerResponseDto getPowerByName(String name);

    PowerResponseDto updatePower(String id, CreatePowerRequest request);

    String deletePower(String id);

}
