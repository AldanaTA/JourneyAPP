package com.journey.api_rest_v1.mapper.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.domain.items.DamageType;
import com.journey.api_rest_v1.domain.items.Item;
import com.journey.api_rest_v1.domain.items.ItemWeapon;
import com.journey.api_rest_v1.domain.items.WeaponProperty;
import com.journey.api_rest_v1.domain.items.WeaponType;
import com.journey.api_rest_v1.dto.item.CreateItemRequest;
import com.journey.api_rest_v1.dto.item.DamageTypeDto;
import com.journey.api_rest_v1.dto.item.ItemResponseDto;
import com.journey.api_rest_v1.dto.item.WeaponPropertyDto;
import com.journey.api_rest_v1.dto.item.WeaponTypeDto;
import com.journey.api_rest_v1.mapper.ItemMapper;

/**
 * Default {@link ItemMapper} implementation that maps fields by hand and resolves inheritance:
 * weapon stats, weapon properties and material come from the nearest item in the base chain that
 * sets them, and details are merged from the root base down to the item itself.
 */
@Component
public class ItemMapperImpl implements ItemMapper {

    /** Converts an item entity into a response DTO with its inherited data resolved. */
    @Override
    public ItemResponseDto CreateResponse(Item item) {
        List<Item> chain = baseChain(item);

        ItemWeapon weapon = chain.stream().map(Item::getWeapon).filter(Objects::nonNull).findFirst().orElse(null);
        Set<WeaponProperty> properties = chain.stream()
                .map(Item::getWeaponProperties)
                .filter(props -> !props.isEmpty())
                .findFirst()
                .orElse(Collections.emptySet());
        Item material = chain.stream().map(Item::getMaterial).filter(Objects::nonNull).findFirst().orElse(null);

        // Root base first, so each derived item's keys override its base's.
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            details.putAll(chain.get(i).getDetails());
        }

        return new ItemResponseDto(
            item.getId() == null ? null : item.getId().toString(),
            item.getName(),
            item.getItemType(),
            item.getRarity(),
            item.isMagic(),
            item.getDescription(),
            item.getEffect(),
            item.getCostBronze(),
            item.getStackLimit(),
            item.getBundleQuantity(),
            item.isRequiresAttunement(),
            item.getCharges(),
            details,
            material == null ? null : toMaterial(material),
            item.getBaseItem() == null ? null
                    : new ItemResponseDto.BaseItem(item.getBaseItem().getId().toString(), item.getBaseItem().getName()),
            weapon == null ? null : toWeapon(weapon, properties),
            item.getSource() == null ? null : item.getSource().getSource_name()
        );
    }

    /** Returns the item followed by its base, its base's base, and so on (stops on a loop). */
    private static List<Item> baseChain(Item item) {
        List<Item> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Item current = item;
        while (current != null && (current.getId() == null || seen.add(current.getId()))) {
            chain.add(current);
            current = current.getBaseItem();
        }
        return chain;
    }

    /** Converts a metal item into the material summary, pulling its armor/weapon effects from details. */
    private static ItemResponseDto.Material toMaterial(Item material) {
        Map<String, Object> details = material.getDetails();
        return new ItemResponseDto.Material(
            material.getId().toString(),
            material.getName(),
            details.get("armor_effect") instanceof String text ? text : null,
            details.get("weapon_effect") instanceof String text ? text : null
        );
    }

    /** Converts weapon stats and properties into the response's weapon section. */
    private static ItemResponseDto.Weapon toWeapon(ItemWeapon weapon, Set<WeaponProperty> properties) {
        return new ItemResponseDto.Weapon(
            toWeaponType(weapon.getWeaponType()),
            weapon.getDamageDice(),
            weapon.getDamageBonus(),
            toDamageType(weapon.getDamageType()),
            weapon.getRangeFt(),
            weapon.getMag(),
            properties.stream()
                .sorted(Comparator.comparing(WeaponProperty::getName, String.CASE_INSENSITIVE_ORDER))
                .map(ItemMapperImpl::toWeaponProperty)
                .toList()
        );
    }

    /** Converts a weapon type entity into its DTO. */
    public static WeaponTypeDto toWeaponType(WeaponType type) {
        return new WeaponTypeDto(type.getName(), type.getEffectName(), type.getEffect());
    }

    /** Converts a weapon property entity into its DTO. */
    public static WeaponPropertyDto toWeaponProperty(WeaponProperty property) {
        return new WeaponPropertyDto(property.getName(), property.getDescription());
    }

    /** Converts a damage type entity into its DTO. */
    public static DamageTypeDto toDamageType(DamageType type) {
        return new DamageTypeDto(type.getName(), type.getEffect(), List.copyOf(type.getDefenses()));
    }

    /** Returns a field-by-field copy of the given create request. */
    @Override
    public CreateItemRequest CreateRequest(CreateItemRequest request) {
        return new CreateItemRequest(
            request.name(),
            request.item_type(),
            request.rarity(),
            request.is_magic(),
            request.description(),
            request.effect(),
            request.cost_bronze(),
            request.stack_limit(),
            request.bundle_quantity(),
            request.requires_attunement(),
            request.charges(),
            request.details(),
            request.base_item_id(),
            request.material_id(),
            request.weapon(),
            request.weapon_properties(),
            request.content_source_id()
        );
    }
}
