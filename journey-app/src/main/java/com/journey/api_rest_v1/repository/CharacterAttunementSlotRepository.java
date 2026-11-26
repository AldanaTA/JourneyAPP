package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterAttunementSlot;

/** Data access for a character's extra attunements. */
@Repository
public interface CharacterAttunementSlotRepository extends JpaRepository<CharacterAttunementSlot, UUID> {

    List<CharacterAttunementSlot> findByCharacterIdOrderBySortOrderAscCreatedAtAsc(UUID characterId);

    Optional<CharacterAttunementSlot> findByIdAndCharacterId(UUID id, UUID characterId);

    List<CharacterAttunementSlot> findByCharacterIdAndGroupName(UUID characterId, String groupName);

    long countByCharacterId(UUID characterId);
}
