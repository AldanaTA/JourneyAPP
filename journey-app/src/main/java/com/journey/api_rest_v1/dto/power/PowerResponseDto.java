package com.journey.api_rest_v1.dto.power;

import java.util.List;
import com.journey.api_rest_v1.domain.powers.PowerCategory;

public record PowerResponseDto(
    String id,
    String name,
    Integer lvl,
    String type,
    Integer tpCost,
    Integer hpCost,
    Integer mpCost,
    Integer epCost,
    String materialComponents,
    Boolean verbal,
    Boolean sight,
    Boolean somatic,
    Boolean isDistinct,
    Boolean concentration,
    String range,
    String area,
    String duration,
    String effect,
    String empowerEffect,
    String lvlUpEffect,
    List<PowerCategory> categories,
    String contentSourceName
) {}
