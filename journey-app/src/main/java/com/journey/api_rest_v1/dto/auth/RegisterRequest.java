package com.journey.api_rest_v1.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for signing up with a username, email, and password.
 * {@code username} is what the user signs in with (case-insensitive); {@code display_name} defaults to it.
 * {@code email} is required (it receives password reset links) and can also be used to sign in.
 * Passwords are 8-72 bytes (bcrypt ignores anything past 72 bytes, so longer passwords are refused).
 */
public record RegisterRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,32}",
            message = "Username must be 3-32 letters, digits, '.', '_' or '-'") String username,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
    @Size(max = 64) String display_name,
    @NotBlank(message = "Email is required") @Email String email
) {
}
