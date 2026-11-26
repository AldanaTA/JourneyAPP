package com.journey.api_rest_v1.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.journey.api_rest_v1.domain.characters.CharacterSheet;

/** Data access for the starting character sheets. */
@Repository
public interface CharacterSheetRepository extends JpaRepository<CharacterSheet, UUID> {

    List<CharacterSheet> findAllByOrderBySortOrderAsc();
}
