package com.journey.api_rest_v1.service.campaign;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.journey.api_rest_v1.dto.campaign.ChatMessageDto;
import com.journey.api_rest_v1.dto.character.NamedRefDto;

import tools.jackson.databind.ObjectMapper;

/**
 * Pushes chat to the members who have a campaign's chat open, as server-sent events:
 * <ul>
 *   <li>{@code message}: a new message (a {@code ChatMessageDto})</li>
 *   <li>{@code edited}: a message whose text changed (a {@code ChatMessageDto})</li>
 *   <li>{@code deleted}: {@code {"id": ...}}</li>
 * </ul>
 * Each event goes only to members who may read the message (storytellers get every whisper), and only after the
 * change is committed. A stream ends when the access token it was opened with expires; the client reconnects with
 * a fresh one (and reloads what it missed with GET .../messages).
 *
 * <p>Every API instance keeps its own open streams. A change is delivered to this instance's streams at once and
 * published on the Redis channel {@value #CHANNEL}, so the other instances deliver it to theirs (each ignores what it
 * published itself). If Redis is down, this instance's streams still get everything; Redis is then skipped for
 * {@link #REDIS_RETRY_DELAY_MILLIS} so chat does not wait on it.</p>
 */
@Component
public class ChatBroker {

    private static final Logger log = LoggerFactory.getLogger(ChatBroker.class);

    /** Streams are closed after this long at most, and the client reconnects. */
    private static final long MAX_STREAM_MS = 30 * 60 * 1000L;

    /** The Redis channel chat changes are shared on between API instances. */
    public static final String CHANNEL = "journey:chat";
    static final long REDIS_RETRY_DELAY_MILLIS = 10_000;

    /** A change to push. */
    public record ChatEvent(UUID campaignId, String type, ChatMessageDto message) {
    }

    /** A change on the Redis channel, with the instance that published it. */
    record Envelope(String origin, ChatEvent event) {
    }

    /** Tells this instance's messages apart from other instances' on the channel. */
    private final String instanceId = UUID.randomUUID().toString();
    private volatile long redisRetryAt;

    private record Subscriber(UUID userId, Instant expiresAt, SseEmitter emitter) {
    }

    private final Map<UUID, Set<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public ChatBroker(NamedParameterJdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper json) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.json = json;
    }

    /** Opens a stream for a member (the caller has checked membership). */
    public SseEmitter subscribe(UUID campaignId, UUID userId, Instant tokenExpiresAt) {
        long untilExpiry = tokenExpiresAt == null ? MAX_STREAM_MS : tokenExpiresAt.toEpochMilli() - System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(Math.max(1_000L, Math.min(MAX_STREAM_MS, untilExpiry)));
        Subscriber subscriber = new Subscriber(userId, tokenExpiresAt, emitter);
        Set<Subscriber> set = subscribers.computeIfAbsent(campaignId, id -> ConcurrentHashMap.newKeySet());
        set.add(subscriber);
        Runnable remove = () -> {
            set.remove(subscriber);
            subscribers.computeIfPresent(campaignId, (id, s) -> s.isEmpty() ? null : s);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(error -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("campaignId", campaignId.toString()),
                    MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            remove.run();
        }
        return emitter;
    }

    /** Once a change is committed: delivers it here, and shares it with the other API instances through Redis. */
    @TransactionalEventListener(fallbackExecution = true)
    public void publish(ChatEvent event) {
        deliver(event);
        if (System.currentTimeMillis() < redisRetryAt) return;
        try {
            redis.convertAndSend(CHANNEL, json.writeValueAsString(new Envelope(instanceId, event)));
        } catch (RuntimeException ex) {
            redisRetryAt = System.currentTimeMillis() + REDIS_RETRY_DELAY_MILLIS;
            log.warn("Could not share a chat change through Redis ({}); other API instances will not see it live",
                    ex.getMessage());
        }
    }

    /** A change from the Redis channel (see ChatRedisConfig); what this instance published is already delivered. */
    public void onRedisMessage(String payload) {
        try {
            Envelope envelope = json.readValue(payload, Envelope.class);
            if (!instanceId.equals(envelope.origin())) deliver(envelope.event());
        } catch (RuntimeException ex) {
            log.warn("Ignored an unreadable chat change from Redis: {}", ex.getMessage());
        }
    }

    /** Sends a change to this instance's streams of members who may read it. */
    private void deliver(ChatEvent event) {
        Set<Subscriber> set = subscribers.get(event.campaignId());
        if (set == null || set.isEmpty()) return;
        // Members now, with whether each is a storyteller (someone removed since subscribing gets nothing).
        Map<UUID, Boolean> members = jdbc.query("""
                SELECT m.user_id, CAST(m.role AS TEXT) IN ('owner', 'editor') OR s.owner_user_id = m.user_id AS st
                FROM source_members m JOIN content_sources s ON s.id = m.source_id WHERE m.source_id = :campaign
                """, new MapSqlParameterSource("campaign", event.campaignId()),
                (rs, i) -> Map.entry(UUID.fromString(rs.getString("user_id")), rs.getBoolean("st")))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        ChatMessageDto m = event.message();
        Set<String> recipients = m.recipients().stream().map(NamedRefDto::id).collect(Collectors.toSet());
        Object payload = "deleted".equals(event.type()) ? Map.of("id", m.id()) : m;
        for (Subscriber s : List.copyOf(set)) {
            Boolean storyteller = members.get(s.userId());
            if (storyteller == null) {
                s.emitter().complete();
                continue;
            }
            boolean canRead = !m.whisper() || storyteller || s.userId().toString().equals(m.senderId())
                    || recipients.contains(s.userId().toString());
            if (canRead) send(s, event.type(), payload);
        }
    }

    /** Keeps idle streams open through proxies, and ends streams whose token has expired. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        Instant now = Instant.now();
        for (Set<Subscriber> set : subscribers.values()) {
            for (Subscriber s : List.copyOf(set)) {
                if (s.expiresAt() != null && s.expiresAt().isBefore(now)) {
                    s.emitter().complete();
                } else {
                    try {
                        s.emitter().send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException ex) {
                        set.remove(s);
                    }
                }
            }
        }
    }

    private void send(Subscriber s, String type, Object payload) {
        try {
            s.emitter().send(SseEmitter.event().name(type).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Chat stream closed while sending: {}", ex.getMessage());
            s.emitter().completeWithError(ex);
        }
    }
}
