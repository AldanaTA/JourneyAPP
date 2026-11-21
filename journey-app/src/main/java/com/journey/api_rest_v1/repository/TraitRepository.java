package com.journey.api_rest_v1.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.journey.api_rest_v1.domain.traits.Trait;
import com.journey.api_rest_v1.domain.traits.TraitCategory;

/**
 * Spring Data repository for traits, with Specification support for dynamic filtering.
 */
public interface TraitRepository extends JpaRepository<Trait, UUID>, JpaSpecificationExecutor<Trait> {

    /** Returns true when a trait already uses this normalized (lowercase, trimmed) name. */
    boolean existsByTraitNameKey(String traitNameKey);

    /** Returns true when a trait other than {@code id} already uses this normalized name. */
    boolean existsByTraitNameKeyAndIdNot(String traitNameKey, UUID id);

    /** Returns a distinct page of traits that have at least one of the given categories. */
    @Query(
        value = """
        SELECT DISTINCT t
        FROM Trait t
        JOIN t.categories tc
        WHERE tc.category IN :categories
        """,
        countQuery = """
        SELECT COUNT(DISTINCT t)
        FROM Trait t
        JOIN t.categories tc
        WHERE tc.category IN :categories
        """
    )
    Page<Trait> findTraitsWithAnyCategory(
        @Param("categories") Collection<TraitCategory> categories,
        Pageable pageable
    );

    /** Returns a page of traits that have every one of the given categories. */
    @Query(
        value = """
        SELECT t
        FROM Trait t
        WHERE t.id IN (
            SELECT t2.id
            FROM Trait t2
            JOIN t2.categories tc
            WHERE tc.category IN :categories
            GROUP BY t2.id
            HAVING COUNT(DISTINCT tc.category) = :categoryCount
        )
        """,
        countQuery = """
        SELECT COUNT(t)
        FROM Trait t
        WHERE t.id IN (
            SELECT t2.id
            FROM Trait t2
            JOIN t2.categories tc
            WHERE tc.category IN :categories
            GROUP BY t2.id
            HAVING COUNT(DISTINCT tc.category) = :categoryCount
        )
        """
    )
    Page<Trait> findTraitsWithAllCategories(
        @Param("categories") Collection<TraitCategory> categories,
        @Param("categoryCount") long categoryCount,
        Pageable pageable
    );
}
