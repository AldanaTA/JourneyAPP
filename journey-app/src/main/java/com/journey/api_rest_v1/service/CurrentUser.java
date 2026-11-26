package com.journey.api_rest_v1.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** The signed-in user of the current request, read from the verified access JWT. */
@Component
public class CurrentUser {

    /** The signed-in user's id; 401 when the request is not signed in. */
    public UUID requireId() {
        return id().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
    }

    /** The signed-in user's id, if any. */
    public Optional<UUID> id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(UUID.fromString(jwt.getSubject()));
        }
        return Optional.empty();
    }

    /** True when the signed-in user is an admin (JWT scope {@code admin}). */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "SCOPE_admin".equals(authority.getAuthority()));
    }
}
