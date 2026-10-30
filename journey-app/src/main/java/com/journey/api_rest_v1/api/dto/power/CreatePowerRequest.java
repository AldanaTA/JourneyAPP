package com.journey.api_rest_v1.api.dto.power;

import java.util.List;
import com.journey.api_rest_v1.domain.powers.PowerType;
import com.journey.api_rest_v1.domain.content_source.ContentSource;
import com.journey.api_rest_v1.domain.powers.PowerCategory;

public record CreatePowerRequest(  String name,
    Integer lvl,
    PowerType type,
    Integer tp_cost, Integer hp_cost, Integer mp_cost, Integer ep_cost,
    String material_components, Boolean verbal, Boolean sight, 
    Boolean somatic, Boolean is_distinct, Boolean concentration,
    String range, String area, String duration,
    String effect, String empower_effect, String lvl_up_effect,
    List<PowerCategory> categories, 
    ContentSource content_source

) {
  
}
