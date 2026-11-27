package com.journey.api_rest_v1.service.campaign;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.characters.PlayerCharacter;
import com.journey.api_rest_v1.dto.campaign.CampaignCharacterDto;
import com.journey.api_rest_v1.dto.campaign.ChatMessageDto;
import com.journey.api_rest_v1.dto.campaign.CampaignDto;
import com.journey.api_rest_v1.dto.campaign.CampaignMemberDto;
import com.journey.api_rest_v1.dto.campaign.CampaignSummaryDto;
import com.journey.api_rest_v1.dto.campaign.ContentIssueDto;
import com.journey.api_rest_v1.dto.campaign.CreateCampaignRequest;
import com.journey.api_rest_v1.dto.campaign.ImportCheckDto;
import com.journey.api_rest_v1.dto.campaign.JoinPreviewDto;
import com.journey.api_rest_v1.dto.campaign.UpdateCampaignRequest;
import com.journey.api_rest_v1.dto.character.NamedRefDto;
import com.journey.api_rest_v1.repository.ContentSourceRepository;
import com.journey.api_rest_v1.repository.PlayerCharacterRepository;
import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

import jakarta.persistence.EntityManager;

/**
 * Campaigns: creating and running them, the join link, members, allowed sources, and campaign versions of
 * characters. A campaign is a content source of kind 'campaign' (see 12-campaigns.sql); its members are the
 * source's members: owner, editor (co-storyteller), viewer (player).
 *
 * <p>Campaigns a user is not a member of answer 404, so ids and codes reveal nothing.</p>
 */
@Service
public class CampaignService {

    public enum Role {
        OWNER, STORYTELLER, PLAYER;

        boolean isStoryteller() {
            return this != PLAYER;
        }

        static Role fromDb(String role) {
            return switch (role) {
                case "owner" -> OWNER;
                case "editor" -> STORYTELLER;
                default -> PLAYER;
            };
        }
    }

    /** The caller's role in this campaign (as a query, so it works inside other queries). */
    private static final String ROLE_SQL = """
            CASE WHEN s.owner_user_id = :user THEN 'owner'
                 ELSE (SELECT CAST(m.role AS TEXT) FROM source_members m WHERE m.source_id = s.id AND m.user_id = :user)
            END""";

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final ContentSourceRepository sourceRepository;
    private final PlayerCharacterRepository characterRepository;
    private final CampaignContentCheck contentCheck;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final CacheManager cacheManager;

    public CampaignService(NamedParameterJdbcTemplate jdbc, CurrentUser currentUser, ContentSourceRepository sourceRepository,
            PlayerCharacterRepository characterRepository, CampaignContentCheck contentCheck, EntityManager entityManager,
            ApplicationEventPublisher events, CacheManager cacheManager) {
        this.events = events;
        this.cacheManager = cacheManager;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.sourceRepository = sourceRepository;
        this.characterRepository = characterRepository;
        this.contentCheck = contentCheck;
        this.entityManager = entityManager;
    }

    // ----------------------------
    // Access
    // ----------------------------

    /** The caller's role; 404 when the campaign does not exist or the caller is not a member. */
    public Role requireMember(UUID campaignId) {
        List<String> roles = jdbc.queryForList("SELECT " + ROLE_SQL + " FROM campaigns c JOIN content_sources s ON s.id = c.id"
                + " WHERE c.id = :campaign", params(campaignId), String.class);
        if (roles.isEmpty() || roles.get(0) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found");
        }
        return Role.fromDb(roles.get(0));
    }

    /** 403 unless the caller is the owner or a co-storyteller. */
    public Role requireStoryteller(UUID campaignId) {
        Role role = requireMember(campaignId);
        if (!role.isStoryteller()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the campaign's storytellers can do that");
        }
        return role;
    }

    public void requireOwner(UUID campaignId) {
        if (requireMember(campaignId) != Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the campaign's owner can do that");
        }
    }

    // ----------------------------
    // Campaigns
    // ----------------------------

    @Transactional(readOnly = true)
    public List<CampaignSummaryDto> listMine() {
        return jdbc.query("""
                SELECT o.campaign_id, o.name, o.description, o.player_count, o.character_count, o.join_enabled,
                       o.updated_at, ow.display_name AS owner_name, r.role
                FROM campaign_overview o
                JOIN content_sources s ON s.id = o.campaign_id
                JOIN users ow ON ow.id = o.owner_user_id
                CROSS JOIN LATERAL (SELECT %s AS role) r
                WHERE r.role IS NOT NULL
                ORDER BY o.updated_at DESC
                """.formatted(ROLE_SQL), params(null),
                (rs, i) -> new CampaignSummaryDto(rs.getString("campaign_id"), rs.getString("name"),
                        rs.getString("description"), Role.fromDb(rs.getString("role")).name(), rs.getString("owner_name"),
                        rs.getInt("player_count"), rs.getInt("character_count"), rs.getBoolean("join_enabled"),
                        String.valueOf(rs.getObject("updated_at"))));
    }

    @Transactional
    public CampaignDto create(CreateCampaignRequest r) {
        UUID user = currentUser.requireId();
        UUID id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO content_sources (source_name, source_desc, kind, visibility, owner_user_id)
                    VALUES (:name, :description, 'campaign', 'members', :user) RETURNING id
                    """, params(null).addValue("name", r.name().trim())
                    .addValue("description", r.description() == null ? "" : r.description()), UUID.class);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a campaign called " + r.name().trim());
        }
        jdbc.update("INSERT INTO campaigns (id) VALUES (:campaign)", params(id));
        jdbc.update("INSERT INTO source_members (source_id, user_id, role) VALUES (:campaign, :user, 'owner')", params(id));
        // A creature must use a creature type of its own source: start the campaign with the base setting's types.
        jdbc.update("""
                INSERT INTO creature_types (source_id, name, description)
                SELECT :campaign, t.name, t.description FROM creature_types t
                JOIN content_sources s ON s.id = t.source_id
                WHERE CAST(s.kind AS TEXT) = 'book' AND s.source_name = 'Calthora'
                ON CONFLICT (source_id, name_key) DO NOTHING
                """, params(id));
        return get(id);
    }

    @Transactional(readOnly = true)
    public CampaignDto get(UUID campaignId) {
        Role role = requireMember(campaignId);
        List<CampaignDto.AllowedSourceDto> allowed = jdbc.query("""
                SELECT s.id, s.source_name, CAST(s.kind AS TEXT) AS kind FROM campaign_allowed_sources a
                JOIN content_sources s ON s.id = a.source_id WHERE a.campaign_id = :campaign ORDER BY s.source_name
                """, params(campaignId),
                (rs, i) -> new CampaignDto.AllowedSourceDto(rs.getString("id"), rs.getString("source_name"), rs.getString("kind")));
        return jdbc.queryForObject("""
                SELECT o.*, c.join_code, ow.display_name AS owner_name FROM campaign_overview o
                JOIN campaigns c ON c.id = o.campaign_id JOIN users ow ON ow.id = o.owner_user_id
                WHERE o.campaign_id = :campaign
                """, params(campaignId),
                (rs, i) -> new CampaignDto(rs.getString("campaign_id"), rs.getString("name"), rs.getString("description"),
                        role.name(), new NamedRefDto(rs.getString("owner_user_id"), rs.getString("owner_name")),
                        rs.getBoolean("join_enabled"), role.isStoryteller() ? rs.getString("join_code") : null, allowed,
                        rs.getInt("player_count"), rs.getInt("character_count"), String.valueOf(rs.getObject("created_at")),
                        String.valueOf(rs.getObject("updated_at"))));
    }

    @Transactional
    public CampaignDto update(UUID campaignId, UpdateCampaignRequest r) {
        requireStoryteller(campaignId);
        if (r.name() != null) {
            if (r.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name must not be blank");
            try {
                jdbc.update("UPDATE content_sources SET source_name = :name, updated_at = NOW() WHERE id = :campaign",
                        params(campaignId).addValue("name", r.name().trim()));
            } catch (DuplicateKeyException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The owner already has a campaign called " + r.name().trim());
            }
        }
        if (r.description() != null) {
            jdbc.update("UPDATE content_sources SET source_desc = :description, updated_at = NOW() WHERE id = :campaign",
                    params(campaignId).addValue("description", r.description()));
        }
        if (r.join_enabled() != null) {
            jdbc.update("UPDATE campaigns SET join_enabled = :enabled, updated_at = NOW() WHERE id = :campaign",
                    params(campaignId).addValue("enabled", r.join_enabled()));
        }
        return get(campaignId);
    }

    /**
     * Deletes the campaign with its content, rules, notes, and character versions. Refused (409) while a character
     * outside the campaign uses the campaign's content, naming those characters.
     */
    @Transactional
    public void delete(UUID campaignId) {
        requireOwner(campaignId);
        List<String> blocking = jdbc.query("""
                SELECT DISTINCT ch.name || ' (' || u.display_name || ')' AS label
                FROM characters ch JOIN users u ON u.id = ch.user_id
                WHERE ch.campaign_id IS DISTINCT FROM :campaign AND (
                    EXISTS (SELECT 1 FROM character_traits ct JOIN traits t ON t.id = ct.trait_id
                            WHERE ct.character_id = ch.id AND t.source_id = :campaign)
                 OR EXISTS (SELECT 1 FROM character_powers cp JOIN powers p ON p.id = cp.power_id
                            WHERE cp.character_id = ch.id AND p.source_id = :campaign)
                 OR EXISTS (SELECT 1 FROM character_items ci JOIN items i ON i.id = ci.item_id
                            WHERE ci.character_id = ch.id AND i.source_id = :campaign)
                 OR EXISTS (SELECT 1 FROM character_races cr JOIN races r ON r.id = cr.race_id
                            WHERE cr.character_id = ch.id AND r.source_id = :campaign)
                 OR EXISTS (SELECT 1 FROM jobs j WHERE j.id = ch.job_id AND j.source_id = :campaign))
                ORDER BY label
                """, params(campaignId), (rs, i) -> rs.getString("label"));
        if (!blocking.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "These characters outside the campaign use its content: "
                    + String.join(", ", blocking) + ". Remove that content from them first.");
        }
        // The campaign's own character versions may use its content: check that only once everything is deleted.
        entityManager.flush();
        jdbc.getJdbcTemplate().execute("SET CONSTRAINTS ALL DEFERRED");
        jdbc.update("DELETE FROM content_sources WHERE id = :campaign", params(campaignId));
        jdbc.getJdbcTemplate().execute("SET CONSTRAINTS ALL IMMEDIATE");
        // The campaign's content may be cached (by id); it is gone now.
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
    }

    // ----------------------------
    // Join link
    // ----------------------------

    /** A new join code; links with the old one stop working. */
    @Transactional
    public CampaignDto resetJoinCode(UUID campaignId) {
        requireStoryteller(campaignId);
        jdbc.update("""
                UPDATE campaigns SET join_code = translate(encode(gen_random_bytes(12), 'base64'), '+/', '-_'),
                       updated_at = NOW() WHERE id = :campaign
                """, params(campaignId));
        return get(campaignId);
    }

    @Transactional(readOnly = true)
    public JoinPreviewDto previewJoin(String code) {
        try {
            return jdbc.queryForObject("""
                    SELECT o.campaign_id, o.name, o.description, ow.display_name AS owner_name, o.player_count,
                           o.join_enabled, (SELECT %s) IS NOT NULL AS member
                    FROM campaigns c JOIN content_sources s ON s.id = c.id
                    JOIN campaign_overview o ON o.campaign_id = c.id JOIN users ow ON ow.id = o.owner_user_id
                    WHERE c.join_code = :code
                    """.formatted(ROLE_SQL), params(null).addValue("code", code),
                    (rs, i) -> new JoinPreviewDto(rs.getString("campaign_id"), rs.getString("name"),
                            rs.getString("description"), rs.getString("owner_name"), rs.getInt("player_count"),
                            rs.getBoolean("join_enabled"), rs.getBoolean("member")));
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This join link is not valid (it may have been reset)");
        }
    }

    /** Joins as a player. Joining again when already a member just returns the campaign. */
    @Transactional
    public CampaignDto join(String code) {
        JoinPreviewDto preview = previewJoin(code);
        UUID campaignId = UUID.fromString(preview.campaignId());
        if (!preview.alreadyMember()) {
            if (!preview.joinEnabled()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This campaign is not taking new players right now");
            }
            jdbc.update("""
                    INSERT INTO source_members (source_id, user_id, role) VALUES (:campaign, :user, 'viewer')
                    ON CONFLICT (source_id, user_id) DO NOTHING
                    """, params(campaignId));
            postSystem(campaignId, displayName(currentUser.requireId()) + " joined the campaign");
        }
        return get(campaignId);
    }

    // ----------------------------
    // Members
    // ----------------------------

    @Transactional(readOnly = true)
    public List<CampaignMemberDto> members(UUID campaignId) {
        requireMember(campaignId);
        return jdbc.query("""
                SELECT m.user_id, u.display_name, CAST(m.role AS TEXT) AS role, m.created_at,
                       (SELECT COUNT(*) FROM characters ch WHERE ch.campaign_id = :campaign AND ch.user_id = m.user_id) AS chars
                FROM source_members m JOIN users u ON u.id = m.user_id
                WHERE m.source_id = :campaign
                ORDER BY CASE CAST(m.role AS TEXT) WHEN 'owner' THEN 0 WHEN 'editor' THEN 1 ELSE 2 END, u.display_name
                """, params(campaignId),
                (rs, i) -> new CampaignMemberDto(rs.getString("user_id"), rs.getString("display_name"),
                        Role.fromDb(rs.getString("role")).name(), rs.getInt("chars"), String.valueOf(rs.getObject("created_at"))));
    }

    /** Makes a member a co-storyteller or a player (owner only; the owner's own role changes by transfer). */
    @Transactional
    public List<CampaignMemberDto> setRole(UUID campaignId, UUID userId, String role) {
        requireOwner(campaignId);
        Role current = memberRole(campaignId, userId);
        if (current == Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hand the campaign to someone else to stop being its owner");
        }
        jdbc.update("UPDATE source_members SET role = CAST(:role AS source_role), updated_at = NOW()"
                + " WHERE source_id = :campaign AND user_id = :member",
                params(campaignId).addValue("member", userId).addValue("role", "STORYTELLER".equals(role) ? "editor" : "viewer"));
        return members(campaignId);
    }

    /**
     * Removes a member, or leaves when it is the caller. Storytellers can remove players; only the owner can remove a
     * co-storyteller; the owner cannot leave (hand the campaign over or delete it). Their campaign characters stay,
     * still theirs, so nothing is lost if they come back.
     */
    @Transactional
    public void removeMember(UUID campaignId, UUID userId) {
        Role caller = requireMember(campaignId);
        Role target = memberRole(campaignId, userId);
        boolean self = userId.equals(currentUser.requireId());
        if (target == Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The owner cannot leave; hand the campaign to someone else or delete it");
        }
        if (!self && !(caller == Role.OWNER || (caller == Role.STORYTELLER && target == Role.PLAYER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot remove that member");
        }
        jdbc.update("DELETE FROM source_members WHERE source_id = :campaign AND user_id = :member",
                params(campaignId).addValue("member", userId));
        postSystem(campaignId, displayName(userId) + (self ? " left the campaign" : " was removed from the campaign"));
    }

    /** A line from the app in the campaign's chat, seen by every member. */
    private void postSystem(UUID campaignId, String text) {
        ChatMessageDto message = jdbc.queryForObject("""
                INSERT INTO campaign_messages (campaign_id, kind, body) VALUES (:campaign, 'system', :body)
                RETURNING id, created_at
                """, params(campaignId).addValue("body", text),
                (rs, i) -> new ChatMessageDto(rs.getString("id"), "SYSTEM", null, null, false, List.of(), text, null,
                        String.valueOf(rs.getObject("created_at")), null));
        events.publishEvent(new ChatBroker.ChatEvent(campaignId, "message", message));
    }

    private String displayName(UUID userId) {
        return jdbc.queryForObject("SELECT display_name FROM users WHERE id = :member",
                new MapSqlParameterSource("member", userId), String.class);
    }

    /** Hands the campaign to another member; the old owner stays as a co-storyteller. */
    @Transactional
    public CampaignDto transferOwnership(UUID campaignId, UUID newOwner) {
        requireOwner(campaignId);
        memberRole(campaignId, newOwner);
        UUID oldOwner = currentUser.requireId();
        if (newOwner.equals(oldOwner)) return get(campaignId);
        try {
            jdbc.update("UPDATE content_sources SET owner_user_id = :member, updated_at = NOW() WHERE id = :campaign",
                    params(campaignId).addValue("member", newOwner));
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "They already own a campaign with this name; rename it first");
        }
        jdbc.update("UPDATE source_members SET role = 'owner', updated_at = NOW() WHERE source_id = :campaign AND user_id = :member",
                params(campaignId).addValue("member", newOwner));
        jdbc.update("UPDATE source_members SET role = 'editor', updated_at = NOW() WHERE source_id = :campaign AND user_id = :user",
                params(campaignId));
        return get(campaignId);
    }

    private Role memberRole(UUID campaignId, UUID userId) {
        List<String> roles = jdbc.queryForList("SELECT CAST(role AS TEXT) FROM source_members WHERE source_id = :campaign"
                + " AND user_id = :member", params(campaignId).addValue("member", userId), String.class);
        if (roles.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That user is not a member of the campaign");
        return Role.fromDb(roles.get(0));
    }

    // ----------------------------
    // Allowed sources
    // ----------------------------

    /**
     * Replaces the books the campaign allows. The core rules and the campaign's own content are always allowed and
     * cannot be listed; each source must be one the storyteller can read.
     */
    @Transactional
    public CampaignDto setAllowedSources(UUID campaignId, List<String> sourceIds) {
        requireStoryteller(campaignId);
        Set<UUID> ids = new LinkedHashSet<>();
        for (String value : sourceIds) {
            ids.add(CharacterAccess.parseUuid(value, "source"));
        }
        if (ids.contains(campaignId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The campaign's own content is always allowed");
        }
        if (!ids.isEmpty()) {
            Integer core = jdbc.queryForObject("SELECT COUNT(*) FROM content_sources WHERE id IN (:ids) AND CAST(kind AS TEXT) = 'core'",
                    new MapSqlParameterSource("ids", ids), Integer.class);
            if (core != null && core > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The core rules are always allowed and cannot be removed");
            }
            if (sourceRepository.countAvailable(ids, currentUser.requireId(), currentUser.isAdmin()) != ids.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One of the sources is not available to you");
            }
        }
        jdbc.update("DELETE FROM campaign_allowed_sources WHERE campaign_id = :campaign", params(campaignId));
        for (UUID id : ids) {
            jdbc.update("INSERT INTO campaign_allowed_sources (campaign_id, source_id) VALUES (:campaign, :source)",
                    params(campaignId).addValue("source", id));
        }
        jdbc.update("UPDATE campaigns SET updated_at = NOW() WHERE id = :campaign", params(campaignId));
        return get(campaignId);
    }

    // ----------------------------
    // Characters
    // ----------------------------

    @Transactional(readOnly = true)
    public List<CampaignCharacterDto> characters(UUID campaignId) {
        requireMember(campaignId);
        return jdbc.query("""
                SELECT ch.id, ch.name, UPPER(CAST(ch.status AS TEXT)) AS status, ch.user_id, u.display_name,
                       ch.origin_character_id, j.name AS job_name, ch.updated_at,
                       (SELECT string_agg(r.race_name, '|' ORDER BY cr.sort_order) FROM character_races cr
                        JOIN races r ON r.id = cr.race_id WHERE cr.character_id = ch.id) AS races
                FROM characters ch JOIN users u ON u.id = ch.user_id LEFT JOIN jobs j ON j.id = ch.job_id
                WHERE ch.campaign_id = :campaign
                ORDER BY ch.name, u.display_name
                """, params(campaignId), (rs, i) -> characterDto(rs, campaignId));
    }

    private CampaignCharacterDto characterDto(ResultSet rs, UUID campaignId) throws SQLException {
        String races = rs.getString("races");
        UUID id = UUID.fromString(rs.getString("id"));
        return new CampaignCharacterDto(id.toString(), rs.getString("name"), rs.getString("status"), rs.getString("user_id"),
                rs.getString("display_name"), rs.getString("origin_character_id"), rs.getString("job_name"),
                races == null ? List.of() : Arrays.asList(races.split("\\|")),
                contentCheck.issues(id, campaignId).size(), String.valueOf(rs.getObject("updated_at")));
    }

    /**
     * What bringing one of the caller's characters into the campaign would do: everything the campaign does not allow
     * is listed, so the player can decide before anything happens.
     */
    @Transactional(readOnly = true)
    public ImportCheckDto checkImport(UUID campaignId, String characterId) {
        requireMember(campaignId);
        PlayerCharacter original = ownOriginal(characterId);
        boolean already = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM characters WHERE campaign_id = :campaign AND origin_character_id = :character)",
                params(campaignId).addValue("character", original.getId()), Boolean.class));
        if (already) {
            return new ImportCheckDto(original.getId().toString(), original.getName(), false,
                    original.getName() + " is already in this campaign", List.of(), 0, false);
        }
        List<ContentIssueDto> removals = contentCheck.issues(original.getId(), campaignId);
        int refunded = removals.stream().mapToInt(ContentIssueDto::expSpent).sum();
        boolean draft = removals.stream().anyMatch(i -> List.of("RACE", "JOB", "SHEET").contains(i.kind()));
        String message = removals.isEmpty() ? "Everything on this character is allowed in the campaign"
                : removals.size() + " thing" + (removals.size() == 1 ? "" : "s")
                        + " on this character " + (removals.size() == 1 ? "is" : "are")
                        + " not allowed in the campaign and will be removed from the campaign version"
                        + " (your original character keeps everything)";
        return new ImportCheckDto(original.getId().toString(), original.getName(), true, message, removals, refunded, draft);
    }

    /** The outcome of an import: the new campaign version, or (when unconfirmed removals remain) the check to confirm. */
    public record ImportResult(CampaignCharacterDto character, ImportCheckDto confirmationNeeded) {
    }

    /**
     * Deep-copies the character into the campaign and removes what the campaign does not allow from the copy. When
     * something would be removed and {@code acceptRemovals} is not true, nothing happens and the check is returned.
     */
    @Transactional
    public ImportResult importCharacter(UUID campaignId, String characterId, boolean acceptRemovals) {
        ImportCheckDto check = checkImport(campaignId, characterId);
        if (!check.canImport()) throw new ResponseStatusException(HttpStatus.CONFLICT, check.message());
        if (!check.removals().isEmpty() && !acceptRemovals) return new ImportResult(null, check);

        UUID copy;
        try {
            copy = jdbc.queryForObject("SELECT copy_character_to_campaign(:character, :campaign)",
                    params(campaignId).addValue("character", UUID.fromString(check.characterId())), UUID.class);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, check.characterName() + " is already in this campaign");
        }
        // Remove from the copy (its items have new ids, so check the copy itself), then let its pickers search the
        // campaign's own content.
        contentCheck.remove(copy, contentCheck.issues(copy, campaignId));
        jdbc.update("""
                INSERT INTO character_content_sources (character_id, source_id) VALUES (:character, :campaign)
                ON CONFLICT DO NOTHING
                """, params(campaignId).addValue("character", copy));
        CampaignCharacterDto dto = characters(campaignId).stream().filter(c -> c.id().equals(copy.toString()))
                .findFirst().orElseThrow();
        return new ImportResult(dto, null);
    }

    /** Takes a campaign version out of the campaign (deletes it): its player, or a storyteller. */
    @Transactional
    public void removeCharacter(UUID campaignId, UUID characterId) {
        Role role = requireMember(campaignId);
        int deleted = jdbc.update("DELETE FROM characters WHERE id = :character AND campaign_id = :campaign"
                + (role.isStoryteller() ? "" : " AND user_id = :user"), params(campaignId).addValue("character", characterId));
        if (deleted == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found in this campaign");
    }

    private PlayerCharacter ownOriginal(String characterId) {
        PlayerCharacter c = characterRepository.findById(CharacterAccess.parseUuid(characterId, "character"))
                .filter(ch -> ch.getUserId().equals(currentUser.requireId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        if (c.getCampaignId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That is already a campaign version; bring in the original character instead");
        }
        return c;
    }

    private MapSqlParameterSource params(UUID campaignId) {
        return new MapSqlParameterSource("campaign", campaignId).addValue("user", currentUser.requireId());
    }
}
