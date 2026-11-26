package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterAttribute;
import com.journey.api_rest_v1.domain.characters.CharacterAttributeId;

/** Data access for a character's attributes. */
@Repository
public interface CharacterAttributeRepository extends JpaRepository<CharacterAttribute, CharacterAttributeId> {

    List<CharacterAttribute> findById_CharacterId(UUID characterId);
}
