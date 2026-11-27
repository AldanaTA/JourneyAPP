package com.journey.api_rest_v1.dto.campaign;

/** What a join link leads to, shown before joining. */
public record JoinPreviewDto(
    String campaignId,
    String name,
    String description,
    String ownerName,
    int playerCount,
    boolean joinEnabled,
    boolean alreadyMember,
    /** What this code joins as: PLAYER or STORYTELLER. */
    String joinsAs,
    /** The caller's role now, if already a member. */
    String currentRole
) {
}
