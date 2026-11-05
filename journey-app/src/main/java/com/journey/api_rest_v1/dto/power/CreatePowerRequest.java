package com.journey.api_rest_v1.dto.power;

import java.util.List;
import com.journey.api_rest_v1.domain.powers.PowerType;

import jakarta.validation.constraints.NotBlank;

import com.journey.api_rest_v1.domain.powers.PowerCategory;

public record CreatePowerRequest(  @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) String name,
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK)
    Integer lvl,
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK)
    PowerType type,
    Integer tp_cost, Integer hp_cost, Integer mp_cost, Integer ep_cost,
    String material_components, @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) Boolean verbal, 
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) Boolean sight, 
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) Boolean somatic, 
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) Boolean is_distinct, 
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) Boolean concentration,
    String range, String area, String duration,
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) String effect, 
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) String empower_effect, 
    @NotBlank(message = ERROR_MESSAGE_NOT_BLANK) String lvl_up_effect,
    List<PowerCategory> categories, 
    String content_source_id

) {
  private static final String ERROR_MESSAGE_NOT_BLANK = "Field must not be blank";
}
