package com.journey.api_rest_v1.service.campaign;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.journey.api_rest_v1.dto.campaign.ContentIssueDto;

/**
 * Finds what in a character a campaign does not allow. A campaign allows the core rules, its own content, and the
 * sources it lists in campaign_allowed_sources.
 */
@Component
public class CampaignContentCheck {

    /** The allowed sources, then one SELECT per kind of content. */
    private static final String ISSUES_SQL = """
            WITH allowed AS (%s),
            bad_races AS (
                SELECT cr.race_id FROM character_races cr JOIN races r ON r.id = cr.race_id
                WHERE cr.character_id = :character AND r.source_id NOT IN (SELECT id FROM allowed)
            ),
            bad_job AS (
                SELECT ch.job_id FROM characters ch JOIN jobs j ON j.id = ch.job_id
                WHERE ch.id = :character AND j.source_id NOT IN (SELECT id FROM allowed)
            )
            SELECT 'RACE' AS kind, r.id AS id, r.race_name AS name, s.source_name AS source, 'NOT_ALLOWED' AS reason,
                   0 AS exp_spent, 0 AS quantity, 1 AS ord
            FROM character_races cr JOIN races r ON r.id = cr.race_id JOIN content_sources s ON s.id = r.source_id
            WHERE cr.character_id = :character AND cr.race_id IN (SELECT race_id FROM bad_races)
            UNION ALL
            SELECT 'JOB', j.id, j.name, s.source_name, 'NOT_ALLOWED', 0, 0, 2
            FROM characters ch JOIN jobs j ON j.id = ch.job_id JOIN content_sources s ON s.id = j.source_id
            WHERE ch.id = :character AND ch.job_id IN (SELECT job_id FROM bad_job)
            UNION ALL
            SELECT 'SHEET', sh.id, sh.name, s.source_name, 'NOT_ALLOWED', 0, 0, 3
            FROM characters ch JOIN character_sheets sh ON sh.id = ch.character_sheet_id
            JOIN content_sources s ON s.id = sh.source_id
            WHERE ch.id = :character AND sh.source_id NOT IN (SELECT id FROM allowed)
            UNION ALL
            SELECT 'TRAIT', t.id, COALESCE(ct.trait_name, t.trait_name), s.source_name,
                   CASE WHEN t.source_id NOT IN (SELECT id FROM allowed) THEN 'NOT_ALLOWED'
                        WHEN CAST(ct.source AS TEXT) = 'racial' THEN 'RACE_REMOVED' ELSE 'JOB_REMOVED' END,
                   ct.exp_spent, 0, 4
            FROM character_traits ct JOIN traits t ON t.id = ct.trait_id JOIN content_sources s ON s.id = t.source_id
            WHERE ct.character_id = :character AND (
                t.source_id NOT IN (SELECT id FROM allowed)
                OR (CAST(ct.source AS TEXT) = 'racial' AND ct.race_id IN (SELECT race_id FROM bad_races))
                OR (CAST(ct.source AS TEXT) = 'job' AND ct.job_id IN (SELECT job_id FROM bad_job)))
            UNION ALL
            SELECT 'POWER', p.id, COALESCE(cp.name, p.name), s.source_name, 'NOT_ALLOWED', cp.exp_spent, 0, 5
            FROM character_powers cp JOIN powers p ON p.id = cp.power_id JOIN content_sources s ON s.id = p.source_id
            WHERE cp.character_id = :character AND p.source_id NOT IN (SELECT id FROM allowed)
            UNION ALL
            SELECT 'ITEM', ci.id, COALESCE(ci.custom_name, i.name), s.source_name, 'NOT_ALLOWED', 0, ci.quantity, 6
            FROM character_items ci JOIN items i ON i.id = ci.item_id JOIN content_sources s ON s.id = i.source_id
            WHERE ci.character_id = :character AND i.source_id NOT IN (SELECT id FROM allowed)
            UNION ALL
            SELECT 'SOURCE', s.id, s.source_name, s.source_name, 'NOT_ALLOWED', 0, 0, 7
            FROM character_content_sources ccs JOIN content_sources s ON s.id = ccs.source_id
            WHERE ccs.character_id = :character AND ccs.source_id NOT IN (SELECT id FROM allowed)
            ORDER BY ord, name
            """;

    /** A campaign allows the core rules, its listed sources, and its own content. */
    private static final String CAMPAIGN_ALLOWED = """
            SELECT id FROM content_sources WHERE CAST(kind AS TEXT) = 'core'
            UNION SELECT source_id FROM campaign_allowed_sources WHERE campaign_id = :campaign
            UNION SELECT CAST(:campaign AS uuid)
            """;

    /**
     * What a user owns, for exporting a character to their own list: the core rules, public books (e.g. Calthora),
     * books they have an entitlement to, and campaigns they own.
     */
    private static final String OWNED = """
            SELECT id FROM content_sources
            WHERE CAST(kind AS TEXT) = 'core' OR CAST(visibility AS TEXT) = 'public' OR owner_user_id = :user
            UNION SELECT source_id FROM user_entitlements WHERE user_id = :user
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CampaignContentCheck(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Everything in the character that the campaign does not allow (empty when it all fits). */
    public List<ContentIssueDto> issues(UUID characterId, UUID campaignId) {
        return query(CAMPAIGN_ALLOWED, new MapSqlParameterSource().addValue("character", characterId)
                .addValue("campaign", campaignId));
    }

    /** Everything in the character from sources the user does not own (see {@link #OWNED}). */
    public List<ContentIssueDto> issuesNotOwned(UUID characterId, UUID userId) {
        return query(OWNED, new MapSqlParameterSource().addValue("character", characterId).addValue("user", userId));
    }

    private List<ContentIssueDto> query(String allowed, MapSqlParameterSource params) {
        return jdbc.query(ISSUES_SQL.formatted(allowed), params,
                (rs, i) -> new ContentIssueDto(rs.getString("kind"), rs.getString("id"), rs.getString("name"),
                        rs.getString("source"), rs.getString("reason"), rs.getInt("exp_spent"), rs.getInt("quantity")));
    }

    /**
     * Removes the issues from a copy (a campaign version or an export; never from an original). A removed race takes its racial traits
     * with it; a removed job its job traits. When a race, job, or sheet goes, the character becomes a draft so the
     * player can choose again.
     */
    public void remove(UUID characterId, List<ContentIssueDto> issues) {
        MapSqlParameterSource p = new MapSqlParameterSource("character", characterId);
        boolean unfinished = issues.stream().anyMatch(i -> List.of("RACE", "JOB", "SHEET").contains(i.kind()));
        if (unfinished) {
            jdbc.update("UPDATE characters SET status = 'draft' WHERE id = :character", p);
        }
        for (ContentIssueDto issue : issues) {
            MapSqlParameterSource q = new MapSqlParameterSource("character", characterId)
                    .addValue("id", UUID.fromString(issue.id()));
            switch (issue.kind()) {
                case "TRAIT" -> jdbc.update("DELETE FROM character_traits WHERE character_id = :character AND trait_id = :id", q);
                case "POWER" -> jdbc.update("DELETE FROM character_powers WHERE character_id = :character AND power_id = :id", q);
                case "ITEM" -> jdbc.update("DELETE FROM character_items WHERE character_id = :character AND id = :id", q);
                case "SOURCE" -> jdbc.update(
                        "DELETE FROM character_content_sources WHERE character_id = :character AND source_id = :id", q);
                case "SHEET" -> jdbc.update("UPDATE characters SET character_sheet_id = NULL WHERE id = :character", q);
                case "JOB" -> {
                    jdbc.update("DELETE FROM character_traits WHERE character_id = :character AND CAST(source AS TEXT) = 'job'", q);
                    jdbc.update("UPDATE characters SET job_id = NULL WHERE id = :character", q);
                }
                // Removing the race also removes its racial traits and clears the language set if it was that race's.
                case "RACE" -> jdbc.update("DELETE FROM character_races WHERE character_id = :character AND race_id = :id", q);
                default -> throw new IllegalStateException(issue.kind());
            }
        }
    }
}
