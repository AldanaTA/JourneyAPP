package com.journey.api_rest_v1.dto.auth;

/** The signed-in user as returned by the auth endpoints. */
public record UserDto(
    String id,
    String username,
    String displayName,
    String email,
    boolean admin
) {
}
