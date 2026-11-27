package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

import com.journey.api_rest_v1.dto.character.NamedRefDto;

import tools.jackson.databind.JsonNode;

/**
 * One chat line: a message, a roll, a combat event, or a system message.
 *
 * @param kind        MESSAGE, ROLL, COMBAT, or SYSTEM
 * @param senderName  the sender's display name (null for system messages or deleted accounts)
 * @param whisper     only the sender, {@code recipients}, and the storytellers can read it (no recipients: the
 *                    storytellers only)
 * @param data        rolls: formula, dice, total, and for skill rolls the rating and degree; combat: the event
 */
public record ChatMessageDto(
    String id,
    String kind,
    String senderId,
    String senderName,
    boolean whisper,
    List<NamedRefDto> recipients,
    String body,
    JsonNode data,
    String createdAt,
    String editedAt
) {
}
