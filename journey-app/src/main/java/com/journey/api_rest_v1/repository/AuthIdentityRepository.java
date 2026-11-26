package com.journey.api_rest_v1.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.users.AuthIdentity;
import com.journey.api_rest_v1.domain.users.AuthProvider;

/** Data access for sign-in identities. */
@Repository
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    /** Finds the identity for a provider's subject, e.g. (PASSWORD, "admin"). */
    Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

    /** Finds a user's identity for one provider (a user has at most one password identity). */
    Optional<AuthIdentity> findByUser_IdAndProvider(UUID userId, AuthProvider provider);
}
