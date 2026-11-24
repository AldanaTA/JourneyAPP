package com.journey.api_rest_v1.domain.creatures;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts {@link AugmentKind} values to and from the lowercase labels stored in the Postgres {@code augment_kind} enum.
 */
@Converter(autoApply = false)
public class AugmentKindConverter implements AttributeConverter<AugmentKind, String> {

    /** Converts the Java enum to its lowercase database label. */
    @Override
    public String convertToDatabaseColumn(AugmentKind attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    /** Converts a lowercase database label back to the Java enum. */
    @Override
    public AugmentKind convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return AugmentKind.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
