package com.journey.api_rest_v1.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.journey.api_rest_v1.dto.campaign.ChatMessageDto;
import com.journey.api_rest_v1.dto.campaign.ChatPageDto;
import com.journey.api_rest_v1.dto.campaign.CombatEventRequest;
import com.journey.api_rest_v1.dto.campaign.EditMessageRequest;
import com.journey.api_rest_v1.dto.campaign.RollRequest;
import com.journey.api_rest_v1.dto.campaign.SendMessageRequest;
import com.journey.api_rest_v1.service.CurrentUser;
import com.journey.api_rest_v1.service.campaign.CampaignChatService;
import com.journey.api_rest_v1.service.campaign.CampaignService;
import com.journey.api_rest_v1.service.campaign.ChatBroker;
import com.journey.api_rest_v1.service.impl.CharacterAccess;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * A campaign's chat: messages and whispers, server-rolled dice, the combat log, unread counts, and a live stream.
 * Storytellers read every message, whispers between players included.
 */
@RestController
@RequestMapping("/api/v1/campaigns/{id}")
public class CampaignChatController {

    private final CampaignChatService chat;
    private final CampaignService campaigns;
    private final ChatBroker broker;
    private final CurrentUser currentUser;

    public CampaignChatController(CampaignChatService chat, CampaignService campaigns, ChatBroker broker, CurrentUser currentUser) {
        this.chat = chat;
        this.campaigns = campaigns;
        this.broker = broker;
        this.currentUser = currentUser;
    }

    /** The newest messages (50 by default, up to 200), or those before {@code before} (a message id). */
    @GetMapping("/messages")
    public ResponseEntity<ChatPageDto> page(@PathVariable String id, @RequestParam(required = false) String before,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(chat.page(uuid(id), before, limit));
    }

    /**
     * New, edited, and deleted messages as they happen (server-sent events: message, edited, deleted). Ends when the
     * access token expires; reconnect with a new one and load what was missed with GET .../messages.
     */
    @GetMapping(path = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id, @AuthenticationPrincipal Jwt jwt, HttpServletResponse response) {
        UUID campaignId = uuid(id);
        campaigns.requireMember(campaignId);
        // Tell proxies (nginx) not to buffer the stream.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return broker.subscribe(campaignId, currentUser.requireId(), jwt == null ? null : jwt.getExpiresAt());
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDto> send(@PathVariable String id, @Valid @RequestBody SendMessageRequest request) {
        return new ResponseEntity<>(chat.send(uuid(id), request), HttpStatus.CREATED);
    }

    /** A dice roll, rolled by the server: a skill roll (skill_rating) or a formula. */
    @PostMapping("/rolls")
    public ResponseEntity<ChatMessageDto> roll(@PathVariable String id, @Valid @RequestBody RollRequest request) {
        return new ResponseEntity<>(chat.roll(uuid(id), request), HttpStatus.CREATED);
    }

    /** A combat log entry (damage, healing, conditions, turn order, ...). */
    @PostMapping("/combat-events")
    public ResponseEntity<ChatMessageDto> combat(@PathVariable String id, @Valid @RequestBody CombatEventRequest request) {
        return new ResponseEntity<>(chat.combat(uuid(id), request), HttpStatus.CREATED);
    }

    /** Edits the text of the caller's own message. */
    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageDto> edit(@PathVariable String id, @PathVariable String messageId,
            @Valid @RequestBody EditMessageRequest request) {
        return ResponseEntity.ok(chat.edit(uuid(id), CharacterAccess.parseUuid(messageId, "message"), request.body()));
    }

    /** Deletes the caller's own message, or any message for a storyteller. */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> delete(@PathVariable String id, @PathVariable String messageId) {
        chat.delete(uuid(id), CharacterAccess.parseUuid(messageId, "message"));
        return ResponseEntity.noContent().build();
    }

    /** Marks the chat read up to now; returns the unread count (0, unless something arrived meanwhile). */
    @PostMapping("/messages/read")
    public ResponseEntity<Map<String, Integer>> markRead(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("unreadCount", chat.markRead(uuid(id))));
    }

    private static UUID uuid(String id) {
        return CharacterAccess.parseUuid(id, "campaign");
    }
}
