package com.journey.api_rest_v1.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.items.WeaponType;

/**
 * Spring Data repository for the WeaponType reference table.
 */
@Repository
public interface WeaponTypeRepository extends JpaRepository<WeaponType, UUID> {

    /** Finds a row by its normalized (lowercase, trimmed) name. */
    Optional<WeaponType> findByNameKey(String nameKey);
}
