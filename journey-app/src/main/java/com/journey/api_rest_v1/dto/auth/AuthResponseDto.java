package com.journey.api_rest_v1.dto.auth;

/**
 * Returned on sign-up, sign-in, and refresh. Send {@code accessToken} as {@code Authorization: Bearer <token>};
 * it expires after {@code expiresIn} seconds, then call refresh (the refresh token is in an HttpOnly cookie).
 */
public record AuthResponseDto(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserDto user
) {
}
