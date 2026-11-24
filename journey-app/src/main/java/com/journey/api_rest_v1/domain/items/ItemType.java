package com.journey.api_rest_v1.domain.items;

/**
 * What an item is (its form), mirroring the Postgres {@code item_type} enum.
 * Whether an item is magic is the separate {@code is_magic} flag, because a magic item can take any form.
 * Values are stored lowercase in the database via {@link ItemTypeConverter}.
 */
public enum ItemType {
    WEAPON,
    ARMOR,
    SHIELD,
    AMMUNITION,
    POTION,
    ACCESSORY,
    MAGIC_ITEM,
    BAG,
    MED_KIT,
    SKILL_BOOK,
    TOOL,
    ALCHEMY_INGREDIENT,
    COOKING_INGREDIENT,
    GEM,
    METAL,
    MISCELLANEOUS
}
