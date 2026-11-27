package com.journey.api_rest_v1.service.campaign;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.dto.campaign.ChatMessageDto;
import com.journey.api_rest_v1.dto.campaign.ChatPageDto;
import com.journey.api_rest_v1.dto.campaign.CombatEventRequest;
import com.journey.api_rest_v1.dto.campaign.RollRequest;
import com.journey.api_rest_v1.dto.campaign.SendMessageRequest;
import com.journey.api_rest_v1.dto.character.NamedRefDto;
import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A campaign's chat: messages, whispers, server-rolled dice, and the combat log. Who can read what is decided by
 * the database function campaign_messages_for (storytellers read every message, whispers included).
 */
@Service
public class CampaignChatService {

    public static final int DEFAULT_PAGE = 50;
    public static final int MAX_PAGE = 200;
    /** Size limit of a combat event's details, as JSON. */
    private static final int MAX_DETAILS_CHARS = 8_000;

    private static final Map<DiceRoller.Degree, String> DEGREE_NAMES = Map.of(
            DiceRoller.Degree.CRITICAL, "Critical success",
            DiceRoller.Degree.SUPERIOR, "Superior success",
            DiceRoller.Degree.MAJOR, "Major success",
            DiceRoller.Degree.SUCCESS, "Success",
            DiceRoller.Degree.FAILURE, "Failure",
            DiceRoller.Degree.FUMBLE, "Fumble");

    private final NamedParameterJdbcTemplate jdbc;
    private final CampaignService campaigns;
    private final CurrentUser currentUser;
    private final DiceRoller dice;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    public CampaignChatService(NamedParameterJdbcTemplate jdbc, CampaignService campaigns, CurrentUser currentUser,
            DiceRoller dice, ObjectMapper json, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.campaigns = campaigns;
        this.currentUser = currentUser;
        this.dice = dice;
        this.json = json;
        this.events = events;
    }

    // ----------------------------
    // Reading
    // ----------------------------

    /** The newest messages the caller can read, or those before {@code before} (a message id). */
    @Transactional(readOnly = true)
    public ChatPageDto page(UUID campaignId, String before, Integer limit) {
        campaigns.requireMember(campaignId);
        int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(MAX_PAGE, limit));
        UUID cursor = before == null || before.isBlank() ? null : CharacterAccess.parseUuid(before, "message");
        List<ChatMessageDto> messages = query("""
                SELECT m.*, u.display_name FROM campaign_messages_for(:campaign, :user) m
                LEFT JOIN users u ON u.id = m.sender_user_id
                WHERE CAST(:before AS uuid) IS NULL
                   OR (m.created_at, m.id) < (SELECT created_at, id FROM campaign_messages WHERE id = CAST(:before AS uuid))
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT :limit
                """, params(campaignId).addValue("before", cursor).addValue("limit", size + 1));
        String next = null;
        if (messages.size() > size) {
            messages = messages.subList(0, size);
            next = messages.get(size - 1).id();
        }
        return new ChatPageDto(messages, next, unread(campaignId));
    }

    /** Messages from others the caller has not read yet. */
    private int unread(UUID campaignId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM campaign_messages_for(:campaign, :user) m
                WHERE m.sender_user_id IS DISTINCT FROM :user
                  AND m.created_at > COALESCE((SELECT last_read_at FROM campaign_chat_reads
                                               WHERE campaign_id = :campaign AND user_id = :user), '-infinity')
                """, params(campaignId), Integer.class);
        return count == null ? 0 : count;
    }

    /** Marks everything up to now as read. */
    @Transactional
    public int markRead(UUID campaignId) {
        campaigns.requireMember(campaignId);
        jdbc.update("""
                INSERT INTO campaign_chat_reads (campaign_id, user_id, last_read_at) VALUES (:campaign, :user, NOW())
                ON CONFLICT (campaign_id, user_id) DO UPDATE SET last_read_at = NOW()
                """, params(campaignId));
        return unread(campaignId);
    }

    // ----------------------------
    // Writing
    // ----------------------------

    @Transactional
    public ChatMessageDto send(UUID campaignId, SendMessageRequest r) {
        campaigns.requireMember(campaignId);
        return insert(campaignId, "message", r.body().trim(), null, Boolean.TRUE.equals(r.whisper()), r.whisper_to());
    }

    /** Rolls on the server: a skill roll (d100 with Degrees of Success) or a dice formula. */
    @Transactional
    public ChatMessageDto roll(UUID campaignId, RollRequest r) {
        campaigns.requireMember(campaignId);
        String label = r.label() == null || r.label().isBlank() ? null : r.label().trim();
        Map<String, Object> data = new LinkedHashMap<>();
        String body;
        if (r.skill_rating() != null) {
            int modifier = r.modifier() == null ? 0 : r.modifier();
            int rating = Math.max(0, r.skill_rating() + modifier);
            int rolled = dice.rollPercent();
            DiceRoller.Degree degree = DiceRoller.degree(rolled, rating);
            data.put("type", "skill");
            data.put("label", label);
            data.put("formula", "1d100");
            data.put("skillRating", r.skill_rating());
            data.put("modifier", modifier);
            data.put("rating", rating);
            data.put("roll", rolled);
            data.put("total", rolled);
            data.put("degree", degree.name());
            data.put("success", degree.ordinal() <= DiceRoller.Degree.SUCCESS.ordinal());
            body = (label == null ? "Skill roll" : label) + ": " + rolled + " vs " + rating + "% — " + DEGREE_NAMES.get(degree);
        } else {
            DiceRoller.Roll roll = dice.roll(r.formula());
            data.put("type", "dice");
            data.put("label", label);
            data.put("formula", roll.formula());
            data.put("dice", roll.dice());
            data.put("modifier", roll.modifier());
            data.put("total", roll.total());
            List<Integer> faces = roll.dice().stream().flatMap(g -> g.results().stream()).toList();
            body = (label == null ? "Roll" : label) + ": " + roll.formula() + " = " + roll.total()
                    + (faces.size() > 1 || roll.modifier() != 0 ? " (" + String.join(", ", faces.stream().map(String::valueOf).toList()) + ")" : "");
        }
        return insert(campaignId, "roll", body, data, Boolean.TRUE.equals(r.whisper()), r.whisper_to());
    }

    @Transactional
    public ChatMessageDto combat(UUID campaignId, CombatEventRequest r) {
        campaigns.requireMember(campaignId);
        Map<String, Object> data = new LinkedHashMap<>(r.details());
        data.put("event", r.event().trim());
        if (json.writeValueAsString(data).length() > MAX_DETAILS_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The combat event's details are too long");
        }
        return insert(campaignId, "combat", r.summary().trim(), data, Boolean.TRUE.equals(r.whisper()), r.whisper_to());
    }

    /** Changes the text of the caller's own message. */
    @Transactional
    public ChatMessageDto edit(UUID campaignId, UUID messageId, String body) {
        campaigns.requireMember(campaignId);
        int updated = jdbc.update("""
                UPDATE campaign_messages SET body = :body, edited_at = NOW()
                WHERE id = :message AND campaign_id = :campaign AND sender_user_id = :user AND CAST(kind AS TEXT) = 'message'
                """, params(campaignId).addValue("message", messageId).addValue("body", body.trim()));
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You can only edit your own messages (not rolls)");
        }
        ChatMessageDto dto = load(campaignId, messageId);
        events.publishEvent(new ChatBroker.ChatEvent(campaignId, "edited", dto));
        return dto;
    }

    /** Deletes a message: the sender's own, or any message for a storyteller. */
    @Transactional
    public void delete(UUID campaignId, UUID messageId) {
        CampaignService.Role role = campaigns.requireMember(campaignId);
        ChatMessageDto dto = load(campaignId, messageId);
        if (!role.isStoryteller() && !currentUser.requireId().toString().equals(dto.senderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own messages");
        }
        jdbc.update("DELETE FROM campaign_messages WHERE id = :message", new MapSqlParameterSource("message", messageId));
        events.publishEvent(new ChatBroker.ChatEvent(campaignId, "deleted", dto));
    }

    // ----------------------------
    // Helpers
    // ----------------------------

    private ChatMessageDto insert(UUID campaignId, String kind, String body, Map<String, Object> data, boolean whisper,
            List<String> whisperTo) {
        UUID user = currentUser.requireId();
        Set<UUID> recipients = new LinkedHashSet<>();
        if (whisper && whisperTo != null) {
            for (String id : whisperTo) {
                UUID recipient = CharacterAccess.parseUuid(id, "recipient");
                if (!recipient.equals(user)) recipients.add(recipient);
            }
            if (!recipients.isEmpty()) {
                Integer members = jdbc.queryForObject("SELECT COUNT(*) FROM source_members WHERE source_id = :campaign"
                        + " AND user_id IN (:ids)", new MapSqlParameterSource("campaign", campaignId).addValue("ids", recipients),
                        Integer.class);
                if (members == null || members != recipients.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can only whisper to members of the campaign");
                }
            }
        } else if (!whisper && whisperTo != null && !whisperTo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set whisper to send to particular members");
        }
        UUID id = jdbc.queryForObject("""
                INSERT INTO campaign_messages (campaign_id, kind, sender_user_id, is_whisper, body, data)
                VALUES (:campaign, CAST(:kind AS campaign_message_kind), :user, :whisper, :body, CAST(:data AS jsonb))
                RETURNING id
                """, params(campaignId).addValue("kind", kind).addValue("whisper", whisper).addValue("body", body)
                .addValue("data", data == null ? null : json.writeValueAsString(data)), UUID.class);
        for (UUID recipient : recipients) {
            jdbc.update("INSERT INTO campaign_message_recipients (message_id, user_id) VALUES (:message, :recipient)",
                    new MapSqlParameterSource("message", id).addValue("recipient", recipient));
        }
        ChatMessageDto dto = load(campaignId, id);
        events.publishEvent(new ChatBroker.ChatEvent(campaignId, "message", dto));
        return dto;
    }

    /** One message the caller can read; 404 otherwise. */
    private ChatMessageDto load(UUID campaignId, UUID messageId) {
        List<ChatMessageDto> found = query("""
                SELECT m.*, u.display_name FROM campaign_messages_for(:campaign, :user) m
                LEFT JOIN users u ON u.id = m.sender_user_id WHERE m.id = :message
                """, params(campaignId).addValue("message", messageId));
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        return found.get(0);
    }

    private List<ChatMessageDto> query(String sql, MapSqlParameterSource p) {
        record Row(String id, String kind, String senderId, String senderName, boolean whisper, String body, String data,
                String createdAt, String editedAt) {
        }
        List<Row> rows = jdbc.query(sql, p, (rs, i) -> new Row(rs.getString("id"), rs.getString("kind").toUpperCase(),
                rs.getString("sender_user_id"), rs.getString("display_name"), rs.getBoolean("is_whisper"),
                rs.getString("body"), rs.getString("data"), String.valueOf(rs.getObject("created_at")),
                rs.getObject("edited_at") == null ? null : String.valueOf(rs.getObject("edited_at"))));
        Map<String, List<NamedRefDto>> recipients = new HashMap<>();
        List<UUID> whispers = rows.stream().filter(Row::whisper).map(r -> UUID.fromString(r.id())).toList();
        if (!whispers.isEmpty()) {
            jdbc.query("""
                    SELECT r.message_id, r.user_id, u.display_name FROM campaign_message_recipients r
                    JOIN users u ON u.id = r.user_id WHERE r.message_id IN (:ids) ORDER BY u.display_name
                    """, new MapSqlParameterSource("ids", whispers), rs -> {
                        recipients.computeIfAbsent(rs.getString("message_id"), k -> new ArrayList<>())
                                .add(new NamedRefDto(rs.getString("user_id"), rs.getString("display_name")));
                    });
        }
        return rows.stream().map(r -> new ChatMessageDto(r.id(), r.kind(), r.senderId(), r.senderName(), r.whisper(),
                recipients.getOrDefault(r.id(), List.of()), r.body(), r.data() == null ? null : readJson(r.data()),
                r.createdAt(), r.editedAt())).toList();
    }

    private JsonNode readJson(String text) {
        return json.readTree(text);
    }

    private MapSqlParameterSource params(UUID campaignId) {
        return new MapSqlParameterSource("campaign", campaignId).addValue("user", currentUser.requireId());
    }
}
