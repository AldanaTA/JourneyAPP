package com.journey.api_rest_v1.dto.item;

import java.util.List;

import com.journey.api_rest_v1.domain.items.ItemRarity;
import com.journey.api_rest_v1.domain.items.ItemType;

/**
 * Request body for the item filter endpoint.
 * Every field is optional; null values are ignored so the client only sends what the user set.
 * {@code weaponTypes} and {@code damageTypes} are reference names (e.g. "Bow", "Fire") and match a
 * weapon's own stats or, when it has none, the stats it inherits from its base item.
 */
public record ItemFilterRequest(
    List<ItemType> itemTypes,
    List<ItemRarity> rarities,
    Boolean magicOnly,
    Boolean attunementOnly,
    List<String> weaponTypes,
    List<String> damageTypes,
    Integer page,
    Integer size,
    String name,
    /** Only content from these sources (the character\'s chosen books); empty = every source. */
    List<String> sourceIds
) {
    /** Returns true only when the client asked for magic items only. */
    public boolean getMagicOnly() {
        return magicOnly != null && magicOnly;
    }

    /** Returns true only when the client asked for items that require attunement only. */
    public boolean getAttunementOnly() {
        return attunementOnly != null && attunementOnly;
    }

    /** Returns the requested page, defaulting to the first page. */
    public int getPage() {
        return page != null && page >= 0 ? page : 0;
    }

    /** Returns the requested page size, defaulting to 5 like the other filters. */
    public int getSize() {
        return size != null && size > 0 ? size : 5;
    }
}
