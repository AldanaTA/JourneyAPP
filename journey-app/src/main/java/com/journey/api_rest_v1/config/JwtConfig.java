package com.journey.api_rest_v1.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.journey.api_rest_v1.repository.AccessTokenRepository;
import com.journey.api_rest_v1.service.impl.TokenHashing;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Signs and verifies access JWTs with HS256 and a shared secret.
 *
 * <p>Verification checks the signature, expiry, and issuer, and then that the token's row in
 * {@code access_tokens} is still active, so sign-out and revoked sessions stop working immediately.</p>
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    /** Builds the signing key from {@code journey.auth.jwt-secret}. */
    public JwtConfig(AuthProperties properties) {
        this.key = new SecretKeySpec(secretBytes(properties.jwtSecret()), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(AuthProperties properties, AccessTokenRepository accessTokens) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> notRevoked = jwt -> accessTokens.isActive(TokenHashing.sha256(jwt.getTokenValue()))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The token was revoked", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), notRevoked));
        return decoder;
    }

    private static byte[] secretBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("journey.auth.jwt-secret (JWT_SECRET) is not set: using a random secret. Everyone is signed out "
                    + "on restart and other services cannot verify tokens. Set JWT_SECRET outside development.");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return random;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("journey.auth.jwt-secret (JWT_SECRET) must be at least "
                    + MIN_SECRET_BYTES + " bytes long");
        }
        return bytes;
    }
}
