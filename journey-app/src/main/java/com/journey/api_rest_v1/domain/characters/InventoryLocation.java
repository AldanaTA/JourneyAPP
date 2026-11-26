package com.journey.api_rest_v1.domain.characters;

/** Where an item is carried: an equipped place, the components or quest sections, or a player slot. Mirrors the Postgres {@code inventory_location} enum. */
public enum InventoryLocation {
    ARMOR,
    LEFT_HAND,
    RIGHT_HAND,
    BOTH_HANDS,
    BAG,
    COMPONENTS,
    QUEST,
    SLOT
}
