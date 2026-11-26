package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterInventorySlot;

/** Data access for a character's inventory slots. */
@Repository
public interface CharacterInventorySlotRepository extends JpaRepository<CharacterInventorySlot, UUID> {

    List<CharacterInventorySlot> findByCharacterIdOrderBySortOrderAscCreatedAtAsc(UUID characterId);

    Optional<CharacterInventorySlot> findByIdAndCharacterId(UUID id, UUID characterId);

    List<CharacterInventorySlot> findByCharacterIdAndGroupName(UUID characterId, String groupName);
}
