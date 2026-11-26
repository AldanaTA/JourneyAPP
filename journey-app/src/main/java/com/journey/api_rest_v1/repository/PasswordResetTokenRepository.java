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

import com.journey.api_rest_v1.domain.users.PasswordResetToken;

import jakarta.persistence.LockModeType;

/** Data access for password reset tokens. */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Finds a token by the hash of its raw value, locking it so it cannot be used twice at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** How many reset emails a user asked for since the given time (to limit requests). */
    long countByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime since);

    /** Makes every unused token of a user unusable (a new one was requested, or the password changed). */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
