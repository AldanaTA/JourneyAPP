package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterChildId;
import com.journey.api_rest_v1.domain.characters.CharacterRace;

/** Data access for a character's races. */
@Repository
public interface CharacterRaceRepository extends JpaRepository<CharacterRace, CharacterChildId> {

    List<CharacterRace> findById_CharacterIdOrderBySortOrderAsc(UUID characterId);

    /** Removes one race (the database also removes the racial traits it granted). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CharacterRace r WHERE r.id.characterId = :characterId AND r.id.childId = :raceId")
    int deleteRace(@Param("characterId") UUID characterId, @Param("raceId") UUID raceId);
}
