package com.journey.api_rest_v1.dto.item;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.journey.api_rest_v1.domain.items.ItemRarity;
import com.journey.api_rest_v1.domain.items.ItemType;

/**
 * Item payload returned to clients by the item endpoints, with inheritance already resolved:
 * {@code weapon}, {@code material} and {@code details} include what the item inherits from its base.
 * It is Serializable so it can be stored in the Redis cache.
 */
public record ItemResponseDto(
    String id,
    String name,
    ItemType itemType,
    ItemRarity rarity,
    boolean isMagic,
    String description,
    String effect,
    Integer costBronze,
    Integer stackLimit,
    Integer bundleQuantity,
    boolean requiresAttunement,
    Integer charges,
    Map<String, Object> details,
    Material material,
    BaseItem baseItem,
    Weapon weapon,
    String contentSourceName
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The metal an item is made of, with the bonuses it grants. */
    public record Material(String id, String name, String armorEffect, String weaponEffect) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** The item this one is based on. */
    public record BaseItem(String id, String name) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** Resolved weapon stats, with the rules text of the type, damage type, and properties. */
    public record Weapon(
        WeaponTypeDto weaponType,
        String damageDice,
        String damageBonus,
        DamageTypeDto damageType,
        Integer rangeFt,
        Integer mag,
        List<WeaponPropertyDto> properties
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }
}
