package com.journey.api_rest_v1.dto.character;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * What character creation chooses from: the starting sheets and the skills.
 */
public record CharacterReferenceDataDto(
    List<CharacterSheetDto> sheets,
    List<SkillDto> skills
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
