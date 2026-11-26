package com.journey.api_rest_v1.domain.users;

import com.journey.api_rest_v1.domain.common.LowercaseEnumConverter;

import jakarta.persistence.Converter;

/** Converts {@link AuthProvider} to and from the lowercase labels of the Postgres {@code auth_provider} enum. */
@Converter(autoApply = false)
public class AuthProviderConverter extends LowercaseEnumConverter<AuthProvider> {

    /** Creates the converter. */
    public AuthProviderConverter() {
        super(AuthProvider.class);
    }
}
