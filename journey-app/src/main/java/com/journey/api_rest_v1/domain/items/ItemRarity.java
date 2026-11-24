package com.journey.api_rest_v1.domain.items;

/**
 * Item rarity, mirroring the Postgres {@code item_rarity} enum (lowest to highest).
 * Values are stored lowercase in the database via {@link ItemRarityConverter}.
 */
public enum ItemRarity {
    COMMON,
    UNCOMMON,
    RARE,
    VERY_RARE
}
