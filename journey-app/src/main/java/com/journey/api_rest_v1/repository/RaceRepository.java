package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.journey.api_rest_v1.domain.races.Race;

/**
 * Spring Data repository for races, with Specification support for dynamic filtering.
 */
public interface RaceRepository extends JpaRepository<Race, UUID>, JpaSpecificationExecutor<Race> {

    /** Returns true when a race already uses this normalized (lowercase, trimmed) name. */
    boolean existsByNameKey(String nameKey);

    /** Returns true when a race other than {@code id} already uses this normalized name. */
    boolean existsByNameKeyAndIdNot(String nameKey, UUID id);
}
