package com.journey.api_rest_v1.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.items.DamageType;

/**
 * Spring Data repository for the DamageType reference table.
 */
@Repository
public interface DamageTypeRepository extends JpaRepository<DamageType, UUID> {

    /** Finds a row by its normalized (lowercase, trimmed) name. */
    Optional<DamageType> findByNameKey(String nameKey);
}
