package com.journey.api_rest_v1.domain.traits;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts {@link TraitCategory} values to and from the lowercase labels
 * stored in the Postgres {@code trait_category} enum.
 */
@Converter(autoApply = false)
public class TraitCategoryConverter implements AttributeConverter<TraitCategory, String> {

    /** Converts the Java enum (e.g. HEALING_RECOVERY) to its database label (healing_recovery). */
    @Override
    public String convertToDatabaseColumn(TraitCategory attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    /** Converts a database label (healing_recovery) back to the Java enum (HEALING_RECOVERY). */
    @Override
    public TraitCategory convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return TraitCategory.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
