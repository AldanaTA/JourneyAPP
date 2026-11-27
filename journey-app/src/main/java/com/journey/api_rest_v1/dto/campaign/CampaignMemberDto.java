package com.journey.api_rest_v1.dto.campaign;

/** A member of a campaign. {@code role}: OWNER, STORYTELLER, or PLAYER. */
public record CampaignMemberDto(
    String userId,
    String displayName,
    String role,
    int characterCount,
    String joinedAt
) {
}
