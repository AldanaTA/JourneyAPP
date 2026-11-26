package com.journey.api_rest_v1.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.journey.api_rest_v1.security.RateLimiter.Decision;
import com.journey.api_rest_v1.security.RateLimiter.Rule;

/**
 * In-memory token buckets, used by {@link RateLimiter} only while Redis is unreachable, so the limits keep
 * protecting the API instead of switching off. Same algorithm as the Redis script.
 *
 * <p>Memory is bounded: idle buckets are dropped by {@link #evictIdleBuckets()}, and at most
 * {@link #MAX_BUCKETS} exist; past that, new keys are let through and a warning is logged.</p>
 */
final class LocalRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LocalRateLimiter.class);
    static final int MAX_BUCKETS = 100_000;
    /** The longest period of any rule; a bucket idle this long is full again, whatever its rule. */
    private static final long MAX_IDLE_NANOS = Duration.ofHours(1).toNanos();

    private static final class Bucket {
        private double tokens;
        private long updatedAt;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.updatedAt = now;
        }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile boolean warnedFull;

    Decision update(Rule rule, String key, boolean consume) {
        long now = System.nanoTime();
        String id = rule.name() + '|' + key;
        Bucket bucket = buckets.get(id);
        if (bucket == null) {
            if (!consume) {
                return Decision.ALLOWED;
            }
            if (buckets.size() >= MAX_BUCKETS) {
                if (!warnedFull) {
                    warnedFull = true;
                    log.warn("Fallback rate limiter is tracking {} keys; letting new keys through", MAX_BUCKETS);
                }
                return Decision.ALLOWED;
            }
            bucket = buckets.computeIfAbsent(id, ignored -> new Bucket(rule.capacity(), now));
        }
        double perNano = (double) rule.capacity() / rule.period().toNanos();
        synchronized (bucket) {
            bucket.tokens = Math.min(rule.capacity(), bucket.tokens + (now - bucket.updatedAt) * perNano);
            bucket.updatedAt = now;
            if (bucket.tokens >= 1) {
                if (consume) {
                    bucket.tokens -= 1;
                }
                return Decision.ALLOWED;
            }
            double nanosUntilToken = (1 - bucket.tokens) / perNano;
            return new Decision(false, Math.max(1, (long) Math.ceil(nanosUntilToken / 1_000_000_000d)));
        }
    }

    /** Drops buckets idle long enough to be full again (a full bucket behaves exactly like a missing one). */
    void evictIdleBuckets() {
        long now = System.nanoTime();
        buckets.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return now - entry.getValue().updatedAt > MAX_IDLE_NANOS;
            }
        });
        if (buckets.size() < MAX_BUCKETS) {
            warnedFull = false;
        }
    }

    int size() {
        return buckets.size();
    }
}
