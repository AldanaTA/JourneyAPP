package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.NotBlank;

/** Hands the campaign to another member; the old owner stays on as a co-storyteller. */
public record TransferOwnershipRequest(
    @NotBlank String user_id
) {
}
