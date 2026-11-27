package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

import com.journey.api_rest_v1.dto.character.NamedRefDto;

/**
 * A campaign as its members see it. {@code joinCode} is only sent to storytellers (players cannot invite others);
 * the join link is {@code <app>/join/<joinCode>}.
 *
 * @param allowedSources the books characters may use besides the core rules and the campaign's own content
 */
public record CampaignDto(
    String id,
    String name,
    String description,
    String role,
    NamedRefDto owner,
    boolean joinEnabled,
    String joinCode,
    List<AllowedSourceDto> allowedSources,
    int playerCount,
    int characterCount,
    String createdAt,
    String updatedAt
) {
    public record AllowedSourceDto(String id, String name, String kind) {
    }
}
