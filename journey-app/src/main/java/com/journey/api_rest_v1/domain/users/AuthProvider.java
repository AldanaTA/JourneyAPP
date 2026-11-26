package com.journey.api_rest_v1.domain.users;

/** Ways a user can sign in. Mirrors the Postgres {@code auth_provider} enum. */
public enum AuthProvider {
    PASSWORD,
    GOOGLE,
    DISCORD
}
