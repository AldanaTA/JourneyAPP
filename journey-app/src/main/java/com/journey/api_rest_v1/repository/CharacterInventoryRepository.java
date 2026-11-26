package com.journey.api_rest_v1.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterInventory;

/** Data access for a character's money and attunement limit. */
@Repository
public interface CharacterInventoryRepository extends JpaRepository<CharacterInventory, UUID> {
}
