package com.journey.api_rest_v1.dto.character;

import com.journey.api_rest_v1.domain.characters.CharacterStatus;
import com.journey.api_rest_v1.domain.races.RaceSize;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body for changing a character; only the fields that are set change.
 * status can switch an active character to RETIRED and back (a draft becomes ACTIVE with POST /finalize).
 * The job and races have their own endpoints, because they change the character's traits.
 */
public record UpdateCharacterRequest(
    @Size(max = 100) String name,
    String description,
    String motive,
    String ideals,
    String strengths,
    String flaws,
    String learned_languages,
    String history,
    String important_characters,
    String notes,
    String character_sheet_id,
    RaceSize size,
    @Min(0) Integer starting_exp,
    @Min(0) Integer job_points,
    CharacterStatus status,
    Integer current_hp,
    Integer current_wp,
    Integer current_ep,
    Integer current_mp,
    @Min(0) Integer temp_hp,
    Integer hp_bonus,
    Integer wp_bonus,
    Integer ep_bonus,
    Integer mp_bonus,
    Integer physical_damage_bonus,
    Integer magic_damage_bonus,
    Integer priority_bonus,
    Integer movement_bonus
) {
}
