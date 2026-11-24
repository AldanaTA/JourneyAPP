package com.journey.api_rest_v1.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.items.WeaponProperty;

/**
 * Spring Data repository for the WeaponProperty reference table.
 */
@Repository
public interface WeaponPropertyRepository extends JpaRepository<WeaponProperty, UUID> {

    /** Finds a row by its normalized (lowercase, trimmed) name. */
    Optional<WeaponProperty> findByNameKey(String nameKey);
}
