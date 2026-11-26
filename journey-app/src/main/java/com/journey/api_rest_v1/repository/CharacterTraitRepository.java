package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterChildId;
import com.journey.api_rest_v1.domain.characters.CharacterTrait;
import com.journey.api_rest_v1.domain.characters.CharacterTraitSource;

/** Data access for a character's traits. */
@Repository
public interface CharacterTraitRepository extends JpaRepository<CharacterTrait, CharacterChildId> {

    List<CharacterTrait> findById_CharacterIdOrderByCreatedAtAsc(UUID characterId);

    /** Removes every trait of one source, e.g. the job traits before changing jobs. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CharacterTrait t WHERE t.id.characterId = :characterId AND t.source = :source")
    int deleteBySource(@Param("characterId") UUID characterId, @Param("source") CharacterTraitSource source);
}
