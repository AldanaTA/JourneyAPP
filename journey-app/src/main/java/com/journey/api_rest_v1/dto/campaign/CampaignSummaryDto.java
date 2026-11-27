package com.journey.api_rest_v1.dto.campaign;

/** A campaign in the user's list. {@code role}: OWNER, STORYTELLER (co-storyteller), or PLAYER. */
public record CampaignSummaryDto(
    String id,
    String name,
    String description,
    String role,
    String ownerName,
    int playerCount,
    int characterCount,
    boolean joinEnabled,
    String updatedAt
) {
}
