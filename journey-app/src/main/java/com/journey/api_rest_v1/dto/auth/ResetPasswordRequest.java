package com.journey.api_rest_v1.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for setting a new password with the token from a reset email. */
public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String new_password
) {
}
