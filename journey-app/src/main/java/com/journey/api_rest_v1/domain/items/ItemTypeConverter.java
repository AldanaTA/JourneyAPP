package com.journey.api_rest_v1.domain.items;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts {@link ItemType} values to and from the lowercase labels stored in the Postgres {@code item_type} enum.
 */
@Converter(autoApply = false)
public class ItemTypeConverter implements AttributeConverter<ItemType, String> {

    /** Converts the Java enum (e.g. MAGIC_ITEM) to its database label (magic_item). */
    @Override
    public String convertToDatabaseColumn(ItemType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    /** Converts a database label (e.g. magic_item) back to the Java enum (MAGIC_ITEM). */
    @Override
    public ItemType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return ItemType.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
