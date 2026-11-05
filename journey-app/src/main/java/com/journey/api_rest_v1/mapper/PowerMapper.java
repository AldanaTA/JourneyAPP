package com.journey.api_rest_v1.mapper;

import com.journey.api_rest_v1.dto.power.CreatePowerRequest;
import com.journey.api_rest_v1.dto.power.PowerResponseDto;
import com.journey.api_rest_v1.domain.powers.Power;

public interface PowerMapper {
   
   CreatePowerRequest CreateRequest(CreatePowerRequest request); 
   PowerResponseDto CreateResponse(Power power);
}
