package com.journey.api_rest_v1.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.items.StatusCondition;

/**
 * Spring Data repository for the status condition reference table.
 */
@Repository
public interface StatusConditionRepository extends JpaRepository<StatusCondition, UUID> {

    /** Finds a condition by its normalized (lowercase, trimmed) name. */
    Optional<StatusCondition> findByNameKey(String nameKey);
}
