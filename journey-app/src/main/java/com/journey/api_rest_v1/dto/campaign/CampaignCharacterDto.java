package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

/**
 * A campaign version of a character. Opened and saved with the usual /characters/{id} endpoints, by its player or
 * by the campaign's storytellers.
 *
 * @param disallowedContent how many things in it the campaign no longer allows (after its sources changed);
 *                          they must be removed before the character can be saved
 */
public record CampaignCharacterDto(
    String id,
    String name,
    String status,
    String playerId,
    String playerName,
    String originCharacterId,
    String jobName,
    List<String> raceNames,
    int disallowedContent,
    String updatedAt
) {
}
