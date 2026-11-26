package com.journey.api_rest_v1.domain.characters;

import com.journey.api_rest_v1.domain.common.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** Converts {@link CharacterTraitSource} to and from the lowercase labels of the Postgres {@code character_trait_source} enum. */
@Converter(autoApply = false)
public class CharacterTraitSourceConverter extends LowercaseEnumConverter<CharacterTraitSource> {

    /** Creates the converter. */
    public CharacterTraitSourceConverter() {
        super(CharacterTraitSource.class);
    }
}
