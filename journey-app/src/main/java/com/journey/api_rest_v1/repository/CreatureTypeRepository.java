package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.creatures.CreatureType;

/**
 * Spring Data repository for creature types (defined per content source).
 */
@Repository
public interface CreatureTypeRepository extends JpaRepository<CreatureType, UUID> {

    /** Finds a source's type by its normalized (lowercase, trimmed) name. */
    Optional<CreatureType> findBySource_IdAndNameKey(UUID sourceId, String nameKey);

    /** Returns every type defined by a source. */
    List<CreatureType> findAllBySource_Id(UUID sourceId, Sort sort);
}
