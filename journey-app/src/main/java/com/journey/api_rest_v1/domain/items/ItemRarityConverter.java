package com.journey.api_rest_v1.domain.items;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts {@link ItemRarity} values to and from the lowercase labels stored in the Postgres {@code item_rarity} enum.
 */
@Converter(autoApply = false)
public class ItemRarityConverter implements AttributeConverter<ItemRarity, String> {

    /** Converts the Java enum (e.g. VERY_RARE) to its database label (very_rare). */
    @Override
    public String convertToDatabaseColumn(ItemRarity attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    /** Converts a database label (e.g. very_rare) back to the Java enum (VERY_RARE). */
    @Override
    public ItemRarity convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return ItemRarity.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
