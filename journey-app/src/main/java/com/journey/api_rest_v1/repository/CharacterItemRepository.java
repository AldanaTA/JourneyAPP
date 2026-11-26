package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterItem;

/** Data access for the items a character carries. */
@Repository
public interface CharacterItemRepository extends JpaRepository<CharacterItem, UUID> {

    List<CharacterItem> findByCharacterIdOrderBySortOrderAscCreatedAtAsc(UUID characterId);

    Optional<CharacterItem> findByIdAndCharacterId(UUID id, UUID characterId);

    boolean existsBySlotId(UUID slotId);

    long countByCharacterIdAndAttunedTrue(UUID characterId);
}
