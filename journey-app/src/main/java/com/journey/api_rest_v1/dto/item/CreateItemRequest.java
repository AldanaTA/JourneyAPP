package com.journey.api_rest_v1.dto.item;

import java.util.List;
import java.util.Map;

import com.journey.api_rest_v1.domain.items.ItemRarity;
import com.journey.api_rest_v1.domain.items.ItemType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body used to create or fully update an item.
 * Field names use snake_case to match the other create requests and the database columns.
 *
 * <p>Inheritance: set {@code base_item_id} to base this item on another one of the same item_type.
 * Then {@code weapon}, {@code weapon_properties} and {@code material_id} may be left null to inherit
 * the base's, and {@code details} is merged over the base's. A weapon with no base must have
 * {@code weapon} stats. {@code material_id} must be a metal item.</p>
 */
public record CreateItemRequest(
    @NotBlank(message = "Name" + CreateItemRequest.ERROR_MESSAGE_NOT_BLANK) String name,
    @NotNull(message = "Item type" + CreateItemRequest.ERROR_MESSAGE_NOT_BLANK) ItemType item_type,
    @NotNull(message = "Rarity" + CreateItemRequest.ERROR_MESSAGE_NOT_BLANK) ItemRarity rarity,
    Boolean is_magic,
    String description,
    String effect,
    @Min(0) Integer cost_bronze,
    @Min(1) Integer stack_limit,
    @Min(1) Integer bundle_quantity,
    Boolean requires_attunement,
    @Min(0) Integer charges,
    Map<String, Object> details,
    String base_item_id,
    String material_id,
    @Valid CreateItemWeaponRequest weapon,
    List<String> weapon_properties,
    @NotBlank(message = "Content source id" + CreateItemRequest.ERROR_MESSAGE_NOT_BLANK) String content_source_id
) {
    static final String ERROR_MESSAGE_NOT_BLANK = " field must not be blank";
}
