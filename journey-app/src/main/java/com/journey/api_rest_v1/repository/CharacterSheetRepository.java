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

    /** The official sheets (campaigns' own sheets are listed per campaign). */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT sh.* FROM character_sheets sh JOIN content_sources s ON s.id = sh.source_id
            WHERE CAST(s.kind AS TEXT) <> 'campaign' ORDER BY sh.sort_order, sh.name
            """, nativeQuery = true)
    List<CharacterSheet> findOfficial();
}
