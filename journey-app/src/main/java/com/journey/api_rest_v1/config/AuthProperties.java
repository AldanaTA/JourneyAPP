package com.journey.api_rest_v1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sign-in settings under {@code journey.auth}.
 *
 * @param jwtSecret           HMAC secret for signing access JWTs (at least 32 bytes). Every service that verifies
 *                            the tokens (e.g. agent-services) must use the same value. Blank = a random secret per
 *                            start, which signs everyone out on restart (development only).
 * @param issuer              The JWT {@code iss} claim; tokens with any other issuer are rejected.
 * @param accessTokenMinutes  How long an access JWT is valid.
 * @param refreshTokenDays    How long a sign-in session lasts without being used.
 * @param cookieSecure        Send the refresh cookie only over HTTPS. Turn on anywhere that is not localhost.
 */
@ConfigurationProperties(prefix = "journey.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        int accessTokenMinutes,
        int refreshTokenDays,
        boolean cookieSecure) {
}
