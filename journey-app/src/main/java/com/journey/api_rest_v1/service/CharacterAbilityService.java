package com.journey.api_rest_v1.service;

import com.journey.api_rest_v1.dto.character.AddCharacterPowerRequest;
import com.journey.api_rest_v1.dto.character.AddCharacterTraitRequest;
import com.journey.api_rest_v1.dto.character.CharacterDto;
import com.journey.api_rest_v1.dto.character.UpdateCharacterPowerRequest;
import com.journey.api_rest_v1.dto.character.UpdateCharacterTraitRequest;

/**
 * A character's traits and powers: adding them under the creation rules, levelling them, and the character's own
 * edits of their text and costs (levelling is done by editing; nothing levels automatically).
 */
public interface CharacterAbilityService {

    CharacterDto addTrait(String characterId, AddCharacterTraitRequest request);

    CharacterDto updateTrait(String characterId, String traitId, UpdateCharacterTraitRequest request);

    /** Removes a trait; any points it cost become available again. */
    CharacterDto removeTrait(String characterId, String traitId);

    CharacterDto addPower(String characterId, AddCharacterPowerRequest request);

    CharacterDto updatePower(String characterId, String powerId, UpdateCharacterPowerRequest request);

    CharacterDto removePower(String characterId, String powerId);
}
