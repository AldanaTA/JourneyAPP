package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Makes a member a co-storyteller or a player. */
public record UpdateMemberRequest(
    @NotNull @Pattern(regexp = "STORYTELLER|PLAYER", message = "role must be STORYTELLER or PLAYER") String role
) {
}
