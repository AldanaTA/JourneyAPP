package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.races.RaceTraits;
import com.journey.api_rest_v1.domain.races.RaceTraitsId;

/**
 * Spring Data repository for race/trait link rows.
 */
@Repository
public interface RaceTraitsRepository extends JpaRepository<RaceTraits, RaceTraitsId> {

    /** Returns true when the trait is linked to any race other than {@code raceId}. */
    boolean existsByIdTraitIdAndIdRaceIdNot(UUID traitId, UUID raceId);
}
