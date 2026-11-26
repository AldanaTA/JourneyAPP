package com.journey.api_rest_v1.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.journey.api_rest_v1.domain.users.RefreshToken;

/** Data access for refresh tokens (sign-in sessions). */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Finds a refresh token by the SHA-256 hash of its raw value. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Same as {@link #findByTokenHash}, locking the row so two refreshes of one token cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Revokes every still-active token of a session family (sign-out, or reuse of a rotated token). */
    @Modifying
    @Query("""
            UPDATE RefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason
            WHERE t.familyId = :familyId AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now, @Param("reason") String reason);

    /** Revokes every active session of a user (password changed or reset). */
    @Modifying
    @Query("""
            UPDATE RefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now, @Param("reason") String reason);
}
