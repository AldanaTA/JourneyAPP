package com.journey.api_rest_v1.domain.characters;

import com.journey.api_rest_v1.domain.common.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** Converts {@link CharacterStatus} to and from the lowercase labels of the Postgres {@code character_status} enum. */
@Converter(autoApply = false)
public class CharacterStatusConverter extends LowercaseEnumConverter<CharacterStatus> {

    /** Creates the converter. */
    public CharacterStatusConverter() {
        super(CharacterStatus.class);
    }
}
