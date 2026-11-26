package com.journey.api_rest_v1.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for changing the signed-in user's password. */
public record ChangePasswordRequest(
    @NotBlank String current_password,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String new_password
) {
}
