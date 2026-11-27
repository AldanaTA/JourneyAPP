package com.journey.api_rest_v1.dto.campaign;

import java.util.List;

/**
 * A page of messages, newest first. For older messages, ask again with {@code before} = {@code nextBefore}; null
 * when there are none.
 */
public record ChatPageDto(
    List<ChatMessageDto> messages,
    String nextBefore,
    int unreadCount
) {
}
