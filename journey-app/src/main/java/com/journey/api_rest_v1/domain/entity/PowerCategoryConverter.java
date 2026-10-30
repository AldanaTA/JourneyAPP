package com.journey.api_rest_v1.domain.entity;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PowerCategoryConverter implements AttributeConverter<PowerCategory, String> {

    @Override
    public String convertToDatabaseColumn(PowerCategory attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public PowerCategory convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return PowerCategory.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
