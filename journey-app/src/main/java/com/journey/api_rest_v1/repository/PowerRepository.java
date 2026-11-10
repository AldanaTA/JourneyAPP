package com.journey.api_rest_v1.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.journey.api_rest_v1.domain.powers.Power;
import com.journey.api_rest_v1.domain.powers.PowerCategory;

public interface PowerRepository extends JpaRepository<Power, UUID> {
    Optional<Power> findByName(String name);

    Page<Power> findByCategories_CategoryIn(Collection<PowerCategory> categories, Pageable pageable);

    @Query("""
    SELECT DISTINCT p
    FROM Power p
    JOIN p.categories pc
    WHERE pc.category IN :categories
    GROUP BY p
    HAVING COUNT(DISTINCT pc.category) = :categoryCount
    """)
    Page<Power> findPowersWithAllCategories(
        @Param("categories") Collection<PowerCategory> categories,
        @Param("categoryCount") long categoryCount,
        Pageable pageable
    );
}
