package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterChildId;
import com.journey.api_rest_v1.domain.characters.CharacterPower;

/** Data access for a character's powers. */
@Repository
public interface CharacterPowerRepository extends JpaRepository<CharacterPower, CharacterChildId> {

    List<CharacterPower> findById_CharacterIdOrderByCreatedAtAsc(UUID characterId);
}
