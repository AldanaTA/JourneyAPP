package com.journey.api_rest_v1.domain.characters;

import com.journey.api_rest_v1.domain.common.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** Converts {@link SkillCategory} to and from the lowercase labels of the Postgres {@code skill_category} enum. */
@Converter(autoApply = false)
public class SkillCategoryConverter extends LowercaseEnumConverter<SkillCategory> {

    /** Creates the converter. */
    public SkillCategoryConverter() {
        super(SkillCategory.class);
    }
}
