package com.journey.api_rest_v1.dto.character;

import java.util.List;

import com.journey.api_rest_v1.domain.characters.CharacterStatus;

/**
 * A character in the signed-in user's list.
 */
public record CharacterSummaryDto(
    String id,
    String name,
    CharacterStatus status,
    String jobName,
    List<String> raceNames,
    String createdAt,
    String updatedAt
) {
}
