package com.humanworkstream.baha.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * In-memory token-bucket rate limiter (single API instance). Buckets are keyed by an arbitrary
 * string (e.g. {@code "login:<ip>"}) and evicted an hour after their last use. Swap Caffeine for
 * Redis if ever scaled to multiple replicas — the {@link #tryAcquire} contract stays the same.
 */
@Component
public class RateLimiterService {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(200_000)
            .build();

    /**
     * Consume one token from the bucket for {@code key}, creating it on first use with the given
     * capacity and refill schedule. Returns {@code false} when empty (reject with 429).
     */
    public boolean tryAcquire(String key, int capacity, int refillTokens, Duration refillPeriod) {
        Bucket bucket = buckets.get(key, k -> new Bucket(capacity, refillTokens, refillPeriod));
        return bucket.tryConsume();
    }

    /** Thread-safe lazily-refilled token bucket. */
    private static final class Bucket {
        private final double capacity;
        private final double tokensPerNano;
        private double tokens;
        private long lastRefillNanos;

        Bucket(int capacity, int refillTokens, Duration refillPeriod) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.tokensPerNano = (double) refillTokens / refillPeriod.toNanos();
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * tokensPerNano);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
