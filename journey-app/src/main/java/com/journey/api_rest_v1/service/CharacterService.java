package com.journey.api_rest_v1.service;

import java.util.List;

import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.CharacterExpAwardDto;
import com.journey.api_rest_v1.dto.character.CharacterReferenceDataDto;
import com.journey.api_rest_v1.dto.character.CharacterSummaryDto;
import com.journey.api_rest_v1.dto.character.CreateCharacterRequest;
import com.journey.api_rest_v1.dto.character.CreateExpAwardRequest;
import com.journey.api_rest_v1.dto.character.CreationCheckDto;
import com.journey.api_rest_v1.dto.character.SetCharacterJobRequest;
import com.journey.api_rest_v1.dto.character.SetCharacterRacesRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterAttributeRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterSkillRequest;

/**
 * The signed-in user's characters: creation, the sheet, races, job, attributes, skills, EXP, and finishing
 * character creation. Every change that spends points is refused when it would overspend EXP or job points.
 */
public interface CharacterService {

    /** The starting sheets and skills character creation chooses from. */
    CharacterReferenceDataDto getReferenceData();

    /** The signed-in user's characters, most recently changed first. */
    List<CharacterSummaryDto> listMine();

    /** Creates a draft character with every attribute and skill at its starting value. */
    CharacterDto create(CreateCharacterRequest request);

    CharacterDto get(String id);

    CharacterDto update(String id, UpdateCharacterRequest request);

    void delete(String id);

    /** Sets the races (one, or several for a mixed race); a single race gets all its racial traits. */
    CharacterDto setRaces(String id, SetCharacterRacesRequest request);

    /** Sets or clears the job; the previous job's free traits are removed. */
    CharacterDto setJob(String id, SetCharacterJobRequest request);

    CharacterDto updateAttribute(String id, String attribute, UpdateCharacterAttributeRequest request);

    CharacterDto updateSkill(String id, String skillId, UpdateCharacterSkillRequest request);

    List<CharacterExpAwardDto> listExpAwards(String id);

    CharacterDto addExpAward(String id, CreateExpAwardRequest request);

    /** What still breaks the character creation rules (empty when the character can be finished). */
    CreationCheckDto checkCreation(String id);

    /** Finishes character creation: the draft becomes active, if it meets every creation rule. */
    CharacterDto finalizeCreation(String id);
}
