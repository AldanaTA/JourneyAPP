package com.journey.api_rest_v1.dto.item;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Weapon stats inside {@link CreateItemRequest}. Weapon type and damage type are names from the
 * reference tables (e.g. "Martial", "Slashing"), matched case-insensitively.
 */
public record CreateItemWeaponRequest(
    @NotBlank(message = "Weapon type" + CreateItemRequest.ERROR_MESSAGE_NOT_BLANK) String weapon_type,
    @NotBlank(message = "Damage dice" + CreateItemRequest.ERROR_MESSAGE_NOT_BLANK)
    @Pattern(regexp = "(?i)\\d+d\\d+", message = "Damage dice must look like 2d6") String damage_dice,
    String damage_bonus,
    @NotBlank(message = "Damage type" + CreateItemRequest.ERROR_MESSAGE_NOT_BLANK) String damage_type,
    @Min(0) Integer range_ft,
    @Min(1) Integer mag
) {
}
