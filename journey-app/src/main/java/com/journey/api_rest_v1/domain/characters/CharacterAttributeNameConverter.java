package com.journey.api_rest_v1.domain.characters;

import com.journey.api_rest_v1.domain.common.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** Converts {@link CharacterAttributeName} to and from the lowercase labels of the Postgres {@code character_attribute} enum. */
@Converter(autoApply = false)
public class CharacterAttributeNameConverter extends LowercaseEnumConverter<CharacterAttributeName> {

    /** Creates the converter. */
    public CharacterAttributeNameConverter() {
        super(CharacterAttributeName.class);
    }
}
