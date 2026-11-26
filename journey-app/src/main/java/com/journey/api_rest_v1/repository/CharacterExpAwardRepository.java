package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterExpAward;

/** Data access for EXP awards. */
@Repository
public interface CharacterExpAwardRepository extends JpaRepository<CharacterExpAward, UUID> {

    List<CharacterExpAward> findByCharacterIdOrderByCreatedAtAsc(UUID characterId);
}
