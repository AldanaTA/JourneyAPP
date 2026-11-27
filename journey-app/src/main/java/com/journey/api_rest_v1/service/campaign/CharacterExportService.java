package com.journey.api_rest_v1.service.campaign;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.characters.PlayerCharacter;
import com.journey.api_rest_v1.dto.campaign.ContentIssueDto;
import com.journey.api_rest_v1.dto.campaign.ImportCheckDto;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository;
import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

/**
 * Exports a character to the player's own characters: a deep copy outside any campaign, without the content that
 * comes from sources the player does not own (a campaign's homebrew they only play in, books they have not got).
 */
@Service
public class CharacterExportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final PlayerCharacterRepository characterRepository;
    private final CampaignContentCheck contentCheck;
    private final CurrentUser currentUser;

    public CharacterExportService(NamedParameterJdbcTemplate jdbc, PlayerCharacterRepository characterRepository,
            CampaignContentCheck contentCheck, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.characterRepository = characterRepository;
        this.contentCheck = contentCheck;
        this.currentUser = currentUser;
    }

    /** What the exported copy would lose; nothing changes. */
    @Transactional(readOnly = true)
    public ImportCheckDto check(String characterId) {
        PlayerCharacter c = ownCharacter(characterId);
        List<ContentIssueDto> removals = contentCheck.issuesNotOwned(c.getId(), currentUser.requireId());
        int refunded = removals.stream().mapToInt(ContentIssueDto::expSpent).sum();
        boolean draft = removals.stream().anyMatch(i -> List.of("RACE", "JOB", "SHEET").contains(i.kind()));
        String message = removals.isEmpty() ? "Everything on this character is yours to keep"
                : removals.size() + " thing" + (removals.size() == 1 ? "" : "s") + " on this character "
                        + (removals.size() == 1 ? "comes" : "come") + " from sources you do not own and will be left out of the copy"
                        + " (this character keeps everything)";
        return new ImportCheckDto(c.getId().toString(), c.getName(), true, message, removals, refunded, draft);
    }

    /** Makes the copy in the player's own list and returns its id and name. */
    @Transactional
    public Map<String, String> export(String characterId) {
        PlayerCharacter c = ownCharacter(characterId);
        UUID copy = jdbc.queryForObject("SELECT copy_character(:character, NULL)",
                new MapSqlParameterSource("character", c.getId()), UUID.class);
        contentCheck.remove(copy, contentCheck.issuesNotOwned(copy, currentUser.requireId()));
        return Map.of("id", copy.toString(), "name", c.getName());
    }

    /** Only the character's own player exports it (a storyteller editing it cannot). */
    private PlayerCharacter ownCharacter(String characterId) {
        return characterRepository.findById(CharacterAccess.parseUuid(characterId, "character"))
                .filter(ch -> ch.getUserId().equals(currentUser.requireId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }
}
