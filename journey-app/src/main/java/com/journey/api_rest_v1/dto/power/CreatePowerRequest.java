package com.journey.api_rest_v1.dto.power;

import java.util.List;
import com.journey.api_rest_v1.domain.powers.PowerType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.journey.api_rest_v1.domain.powers.PowerCategory;

public record CreatePowerRequest(  @NotBlank(message = "name" + ERROR_MESSAGE_NOT_BLANK) String name,
    @NotNull(message = "LVL" + ERROR_MESSAGE_NOT_BLANK)
    Integer lvl,
    @NotNull(message = "Type is required")
    PowerType type,
    Integer tp_cost, Integer hp_cost, Integer mp_cost, Integer ep_cost,
    String material_components, @NotNull(message = "Verbal" + ERROR_MESSAGE_NOT_BLANK) Boolean verbal, 
    @NotNull(message = "Sight" + ERROR_MESSAGE_NOT_BLANK) Boolean sight, 
    @NotNull(message = "Somatic" + ERROR_MESSAGE_NOT_BLANK) Boolean somatic, 
    @NotNull(message = "Is Distinct" + ERROR_MESSAGE_NOT_BLANK) Boolean is_distinct, 
    @NotNull(message = "Concentration" + ERROR_MESSAGE_NOT_BLANK) Boolean concentration,
    String range, String area, String duration,
    @NotBlank(message = "Effect" + ERROR_MESSAGE_NOT_BLANK) String effect, 
    @NotBlank(message = "Empower Effect" + ERROR_MESSAGE_NOT_BLANK) String empower_effect, 
    @NotBlank(message = "LVL Up Effect" + ERROR_MESSAGE_NOT_BLANK) String lvl_up_effect,
    List<PowerCategory> categories, 
    String content_source_id

) {
  private static final String ERROR_MESSAGE_NOT_BLANK = "Field must not be blank";
}
