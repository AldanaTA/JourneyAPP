package com.journey.api_rest_v1.dto.character;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for giving a character EXP (negative to correct a mistake).
 */
public record CreateExpAwardRequest(
    @NotNull Integer amount,
    @Size(max = 500) String reason
) {
}
