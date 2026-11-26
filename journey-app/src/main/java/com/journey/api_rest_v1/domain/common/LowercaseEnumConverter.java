package com.journey.api_rest_v1.domain.common;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;

/**
 * Base converter for Java enums stored in Postgres enums with lowercase labels
 * (e.g. {@code LEFT_HAND} <-> {@code left_hand}). Subclasses only name the enum type.
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    /** Creates a converter for the given enum type. */
    protected LowercaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    /** Converts the Java enum to its lowercase database label. */
    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    /** Converts a lowercase database label back to the Java enum. */
    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, dbData.toUpperCase(Locale.ROOT));
    }
}
