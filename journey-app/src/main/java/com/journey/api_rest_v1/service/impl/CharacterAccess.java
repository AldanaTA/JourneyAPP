package com.journey.api_rest_v1.service.impl;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.characters.PlayerCharacter;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository.BudgetView;
import com.journey.api_rest_v1.service.CurrentUser;

import jakarta.persistence.EntityManager;

/**
 * Shared by the character services: loads characters the signed-in user owns, and enforces the point budget.
 *
 * <p>A character that is missing or belongs to someone else is a 404 either way, so ids of other users'
 * characters cannot be discovered.</p>
 */
@Component
public class CharacterAccess {

    private final PlayerCharacterRepository characterRepository;
    private final CurrentUser currentUser;
    private final EntityManager entityManager;

    /** Wires the repository, the current user, and the entity manager (to flush before budget checks). */
    public CharacterAccess(PlayerCharacterRepository characterRepository, CurrentUser currentUser,
            EntityManager entityManager) {
        this.characterRepository = characterRepository;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
    }

    /**
     * A character the signed-in user may open and change: their own, or a campaign version in a campaign they
     * are a storyteller of (for level-ups and rulings). 404 otherwise.
     */
    public PlayerCharacter loadOwned(String id) {
        UUID characterId = parseUuid(id, "character");
        UUID userId = currentUser.requireId();
        return characterRepository.findById(characterId)
                .filter(character -> character.getUserId().equals(userId)
                        || (character.getCampaignId() != null
                            && characterRepository.isCampaignStoryteller(character.getCampaignId(), userId)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }

    /**
     * Refuses (400) a change that leaves the character with negative EXP or job points. Call after the change,
     * inside its transaction: the error rolls the change back.
     */
    public void requireWithinBudget(UUID characterId) {
        entityManager.flush();
        BudgetView budget = characterRepository.findBudget(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        if (budget.getExpRemaining() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough EXP: this needs "
                    + (-budget.getExpRemaining()) + " more than the character has (" + budget.getTotalExp() + " in total)");
        }
        if (budget.getJobPointsRemaining() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough job points: this needs "
                    + (-budget.getJobPointsRemaining()) + " more than the job's " + budget.getJobPoints());
        }
    }

    /** Parses a UUID path or body value; 400 with the given name when it is malformed. */
    public static UUID parseUuid(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing " + what + " id");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + what + " id: " + value);
        }
    }

    /** Parses an optional UUID (null or blank gives null). */
    public static UUID parseOptionalUuid(String value, String what) {
        return value == null || value.isBlank() ? null : parseUuid(value, what);
    }
}
