package com.journey.api_rest_v1.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for "forgot password": the account's email. */
public record ForgotPasswordRequest(
    @NotBlank @Email String email
) {
}
