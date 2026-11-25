package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.rules.Asset;

/** Data access for stored images. */
@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    /** Returns every asset shown by any section of the given chapter. */
    @Query(value = """
            SELECT a.*
            FROM assets a
            WHERE a.id IN (
                SELECT rsa.asset_id
                FROM rule_section_assets rsa
                JOIN rule_sections s ON s.id = rsa.section_id
                WHERE s.chapter_id = :chapterId
            )
            """, nativeQuery = true)
    List<Asset> findByChapterId(@Param("chapterId") UUID chapterId);
}
