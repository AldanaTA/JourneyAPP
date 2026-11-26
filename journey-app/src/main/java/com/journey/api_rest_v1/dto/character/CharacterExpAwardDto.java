package com.journey.api_rest_v1.dto.character;

/**
 * One EXP award.
 */
public record CharacterExpAwardDto(
    String id,
    int amount,
    String reason,
    String awardedByUserId,
    String createdAt
) {
}
