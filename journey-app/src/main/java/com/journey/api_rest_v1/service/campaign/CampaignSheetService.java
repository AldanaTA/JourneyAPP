package com.journey.api_rest_v1.service.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.dto.campaign.SaveSheetRequest;
import com.journey.api_rest_v1.dto.character.CharacterSheetDto;

/** A campaign's own character sheets (starting attributes): storytellers write, members list. */
@Service
public class CampaignSheetService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CampaignService campaigns;

    public CampaignSheetService(NamedParameterJdbcTemplate jdbc, CampaignService campaigns) {
        this.jdbc = jdbc;
        this.campaigns = campaigns;
    }

    @Transactional(readOnly = true)
    public List<CharacterSheetDto> list(UUID campaignId) {
        campaigns.requireMember(campaignId);
        return jdbc.query("""
                SELECT id, name, description, base_body, base_strength, base_speed, base_magic FROM character_sheets
                WHERE source_id = :campaign ORDER BY sort_order, name
                """, new MapSqlParameterSource("campaign", campaignId),
                (rs, i) -> new CharacterSheetDto(rs.getString("id"), rs.getString("name"), rs.getString("description"),
                        rs.getInt("base_body"), rs.getInt("base_strength"), rs.getInt("base_speed"), rs.getInt("base_magic")));
    }

    @Transactional
    public List<CharacterSheetDto> create(UUID campaignId, SaveSheetRequest r) {
        campaigns.requireStoryteller(campaignId);
        if (r.name() == null || r.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A sheet needs a name");
        if (r.base_body() == null || r.base_strength() == null || r.base_speed() == null || r.base_magic() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give all four starting attributes");
        }
        try {
            jdbc.update("""
                    INSERT INTO character_sheets (source_id, name, description, base_body, base_strength, base_speed, base_magic, sort_order)
                    VALUES (:campaign, :name, :description, :body, :strength, :speed, :magic,
                            (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM character_sheets WHERE source_id = :campaign))
                    """, params(campaignId, r));
        } catch (DataIntegrityViolationException ex) {
            throw refused(ex, r);
        }
        return list(campaignId);
    }

    @Transactional
    public List<CharacterSheetDto> update(UUID campaignId, UUID sheetId, SaveSheetRequest r) {
        campaigns.requireStoryteller(campaignId);
        if (r.name() != null && r.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A sheet needs a name");
        int updated;
        try {
            updated = jdbc.update("""
                    UPDATE character_sheets SET name = COALESCE(:name, name), description = COALESCE(:description, description),
                           base_body = COALESCE(:body, base_body), base_strength = COALESCE(:strength, base_strength),
                           base_speed = COALESCE(:speed, base_speed), base_magic = COALESCE(:magic, base_magic), updated_at = NOW()
                    WHERE id = :sheet AND source_id = :campaign
                    """, params(campaignId, r).addValue("sheet", sheetId));
        } catch (DataIntegrityViolationException ex) {
            throw refused(ex, r);
        }
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found in this campaign");
        return list(campaignId);
    }

    /** Refused (409) while a character uses the sheet. */
    @Transactional
    public List<CharacterSheetDto> delete(UUID campaignId, UUID sheetId) {
        campaigns.requireStoryteller(campaignId);
        int deleted;
        try {
            deleted = jdbc.update("DELETE FROM character_sheets WHERE id = :sheet AND source_id = :campaign",
                    new MapSqlParameterSource("sheet", sheetId).addValue("campaign", campaignId));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Characters use this sheet; give them another sheet first");
        }
        if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sheet not found in this campaign");
        return list(campaignId);
    }

    private static ResponseStatusException refused(DataIntegrityViolationException ex, SaveSheetRequest r) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("uq_character_sheets_name")) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "The campaign already has a sheet called " + r.name().trim());
        }
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "That sheet is not valid");
    }

    private static MapSqlParameterSource params(UUID campaignId, SaveSheetRequest r) {
        return new MapSqlParameterSource("campaign", campaignId)
                .addValue("name", r.name() == null ? null : r.name().trim())
                .addValue("description", r.description())
                .addValue("body", r.base_body()).addValue("strength", r.base_strength())
                .addValue("speed", r.base_speed()).addValue("magic", r.base_magic());
    }
}
