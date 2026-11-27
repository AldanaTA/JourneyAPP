package com.journey.api_rest_v1.dto.campaign;

import jakarta.validation.constraints.NotBlank;

/**
 * Brings one of the player's characters into the campaign as a campaign version.
 *
 * @param accept_removals must be true when the campaign version would lose content the campaign does not allow
 *                        (see POST .../characters/import-check); otherwise the API answers 409 with that list
 */
public record ImportCharacterRequest(
    @NotBlank String character_id,
    Boolean accept_removals
) {
}
