package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.journey.api_rest_v1.domain.creatures.Creature;

/**
 * Spring Data repository for creatures, with Specification support for dynamic filtering.
 */
public interface CreatureRepository extends JpaRepository<Creature, UUID>, JpaSpecificationExecutor<Creature> {

    /** Returns true when a creature already uses this normalized (lowercase, trimmed) name. */
    boolean existsByNameKey(String nameKey);

    /** Returns true when a creature other than {@code id} already uses this normalized name. */
    boolean existsByNameKeyAndIdNot(String nameKey, UUID id);
}
