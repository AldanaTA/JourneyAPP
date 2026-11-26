package com.journey.api_rest_v1.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token-bucket rate limiter backed by Redis, so limits survive API restarts and are shared by every API
 * instance.
 *
 * <p>Each (rule, key) pair, e.g. ("login", "203.0.113.7"), has a bucket holding up to {@code capacity}
 * tokens that refills evenly over {@code period}. A request takes one token; with none left it is refused and
 * told how long to wait. So "10 per minute" allows a burst of 10, then one more every 6 seconds.</p>
 *
 * <p>One Lua script reads and updates a bucket atomically (no race between concurrent requests) using the
 * Redis clock (so instances agree on time). Buckets expire on their own once idle for a full period.
 * Keys are hashed, so no IP addresses or emails are stored in Redis in plain text.</p>
 *
 * <p>If Redis cannot be reached, the limits fall back to in-memory buckets on this instance (a warning is
 * logged at most once a minute) rather than switching off. After a failure Redis is skipped for
 * {@link #REDIS_RETRY_DELAY_MILLIS}, so an outage does not make every request wait for the Redis timeout;
 * then it is tried again.</p>
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String KEY_PREFIX = "journey:ratelimit:";
    private static final long WARN_INTERVAL_MILLIS = 60_000;
    static final long REDIS_RETRY_DELAY_MILLIS = 10_000;

    /**
     * KEYS[1] bucket key; ARGV capacity, period in ms, 1 to take a token or 0 to only check.
     * Returns {allowed (1/0), milliseconds until a token is available}.
     */
    private static final RedisScript<List<?>> TOKEN_BUCKET = RedisScript.of("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local period_ms = tonumber(ARGV[2])
            local consume = tonumber(ARGV[3]) == 1
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local state = redis.call('HMGET', key, 'tokens', 'ts')
            local exists = state[1] ~= false
            local tokens = capacity
            if exists then
              tokens = math.min(capacity, tonumber(state[1]) + (now - tonumber(state[2])) * capacity / period_ms)
            end
            if tokens >= 1 then
              if consume then
                tokens = tokens - 1
              end
              if consume or exists then
                redis.call('HSET', key, 'tokens', tostring(tokens), 'ts', tostring(now))
                redis.call('PEXPIRE', key, period_ms)
              end
              return {1, 0}
            end
            return {0, math.ceil((1 - tokens) * period_ms / capacity)}
            """, (Class<List<?>>) (Class<?>) List.class);

    /** A limit: {@code capacity} requests per {@code period}, refilled evenly. */
    public record Rule(String name, int capacity, Duration period) {
    }

    /** The outcome of a request: allowed, or refused with how long until a token is available. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }

    private final StringRedisTemplate redis;
    private final LocalRateLimiter fallback = new LocalRateLimiter();
    private volatile long lastWarnedAt;
    /** While in the future, Redis is considered down and the fallback is used without trying it. */
    private volatile long redisRetryAt;

    /** Wires the Redis connection used for the buckets. */
    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Takes one token for the key; refused (with a wait time) when the bucket is empty. */
    public Decision tryConsume(Rule rule, String key) {
        return update(rule, key, true);
    }

    /** Whether a request would be allowed, without taking a token (e.g. before checking a password). */
    public Decision check(Rule rule, String key) {
        return update(rule, key, false);
    }

    private Decision update(Rule rule, String key, boolean consume) {
        if (System.currentTimeMillis() < redisRetryAt) {
            return fallback.update(rule, key, consume);
        }
        try {
            List<?> result = redis.execute(TOKEN_BUCKET, List.of(redisKey(rule, key)),
                    Integer.toString(rule.capacity()),
                    Long.toString(rule.period().toMillis()),
                    consume ? "1" : "0");
            if (result == null || result.size() < 2) {
                throw new IllegalStateException("Unexpected rate limit script result: " + result);
            }
            boolean allowed = ((Number) result.get(0)).longValue() == 1;
            long retryAfterMillis = ((Number) result.get(1)).longValue();
            return allowed ? Decision.ALLOWED
                    : new Decision(false, Math.max(1, (retryAfterMillis + 999) / 1000));
        } catch (RuntimeException ex) {
            redisRetryAt = System.currentTimeMillis() + REDIS_RETRY_DELAY_MILLIS;
            warnFallback(ex);
            return fallback.update(rule, key, consume);
        }
    }

    private void warnFallback(RuntimeException ex) {
        long now = System.currentTimeMillis();
        if (now - lastWarnedAt > WARN_INTERVAL_MILLIS) {
            lastWarnedAt = now;
            log.warn("Rate limiting is using in-memory buckets because Redis failed: {}", ex.getMessage());
        }
    }

    /** "journey:ratelimit:login:<sha256 of key>" (hashed, so no IPs or emails are kept in plain text). */
    static String redisKey(Rule rule, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + rule.name() + ':' + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /** Cleans up the fallback buckets (Redis keys expire on their own). */
    @Scheduled(fixedDelay = 60_000)
    void evictFallbackBuckets() {
        fallback.evictIdleBuckets();
    }
}
