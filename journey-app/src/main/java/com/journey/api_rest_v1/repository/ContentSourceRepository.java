package com.journey.api_rest_v1.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.journey.api_rest_v1.domain.content_source.ContentSource;

public interface ContentSourceRepository extends JpaRepository<ContentSource, UUID> {

    /** A content source as listed to a user. */
    interface AvailableSourceView {
        UUID getId();

        String getName();

        String getDescription();

        String getKind();
    }

    /**
     * The sources a user may read: public ones, books they own, and sources they are a member or owner of
     * (every source for an admin). Core first, then books, then campaigns, each by name.
     */
    @Query(value = """
            SELECT s.id AS id, s.source_name AS name, s.source_desc AS description, CAST(s.kind AS TEXT) AS kind
            FROM content_sources s
            WHERE :admin
               OR CAST(s.visibility AS TEXT) = 'public'
               OR s.owner_user_id = :userId
               OR EXISTS (SELECT 1 FROM user_entitlements e WHERE e.source_id = s.id AND e.user_id = :userId)
               OR EXISTS (SELECT 1 FROM source_members m WHERE m.source_id = s.id AND m.user_id = :userId)
            ORDER BY CASE CAST(s.kind AS TEXT) WHEN 'core' THEN 0 WHEN 'book' THEN 1 ELSE 2 END, s.source_name
            """, nativeQuery = true)
    List<AvailableSourceView> findAvailable(@Param("userId") UUID userId, @Param("admin") boolean admin);

    /** How many of these sources the user may read (to check a character's chosen sources). */
    @Query(value = """
            SELECT COUNT(*) FROM content_sources s
            WHERE s.id IN (:ids)
              AND (:admin
               OR CAST(s.visibility AS TEXT) = 'public'
               OR s.owner_user_id = :userId
               OR EXISTS (SELECT 1 FROM user_entitlements e WHERE e.source_id = s.id AND e.user_id = :userId)
               OR EXISTS (SELECT 1 FROM source_members m WHERE m.source_id = s.id AND m.user_id = :userId))
            """, nativeQuery = true)
    long countAvailable(@Param("ids") Collection<UUID> ids, @Param("userId") UUID userId, @Param("admin") boolean admin);
}
