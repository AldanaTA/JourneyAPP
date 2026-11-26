package com.journey.api_rest_v1.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.users.AccessToken;

/** Data access for issued access tokens. */
@Repository
public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

    /**
     * True when the token was issued by this API, is not revoked or expired, and its user is not disabled.
     * Checked on every authenticated request (one indexed lookup).
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM access_tokens a
                JOIN users u ON u.id = a.user_id
                WHERE a.token_hash = :tokenHash
                  AND a.revoked_at IS NULL
                  AND a.expires_at > NOW()
                  AND u.disabled_at IS NULL
            )
            """, nativeQuery = true)
    boolean isActive(@Param("tokenHash") String tokenHash);

    /** Revokes the access tokens issued from every refresh token of a session family. */
    @Modifying
    @Query(value = """
            UPDATE access_tokens SET revoked_at = :now
            WHERE revoked_at IS NULL
              AND refresh_token_id IN (SELECT id FROM refresh_tokens WHERE family_id = :familyId)
            """, nativeQuery = true)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

    /** Revokes every access token of a user (password changed or reset). */
    @Modifying
    @Query(value = "UPDATE access_tokens SET revoked_at = :now WHERE user_id = :userId AND revoked_at IS NULL",
            nativeQuery = true)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
