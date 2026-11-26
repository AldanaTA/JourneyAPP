package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterChildId;
import com.journey.api_rest_v1.domain.characters.CharacterSkill;

/** Data access for a character's skills. */
@Repository
public interface CharacterSkillRepository extends JpaRepository<CharacterSkill, CharacterChildId> {

    List<CharacterSkill> findById_CharacterId(UUID characterId);
}
