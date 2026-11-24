package com.journey.api_rest_v1.domain.creatures;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts {@link AbilityTiming} values to and from the lowercase labels stored in the Postgres {@code ability_timing} enum.
 */
@Converter(autoApply = false)
public class AbilityTimingConverter implements AttributeConverter<AbilityTiming, String> {

    /** Converts the Java enum to its lowercase database label. */
    @Override
    public String convertToDatabaseColumn(AbilityTiming attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    /** Converts a lowercase database label back to the Java enum. */
    @Override
    public AbilityTiming convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return AbilityTiming.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
