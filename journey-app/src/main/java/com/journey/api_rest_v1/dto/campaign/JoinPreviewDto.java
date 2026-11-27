package com.journey.api_rest_v1.dto.campaign;

/** What a join link leads to, shown before joining. */
public record JoinPreviewDto(
    String campaignId,
    String name,
    String description,
    String ownerName,
    int playerCount,
    boolean joinEnabled,
    boolean alreadyMember
) {
}
