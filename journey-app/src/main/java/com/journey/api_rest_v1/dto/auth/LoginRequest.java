package com.journey.api_rest_v1.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Request body for signing in; {@code login} is the username or the account email. */
public record LoginRequest(
    @NotBlank String login,
    @NotBlank String password
) {
}
