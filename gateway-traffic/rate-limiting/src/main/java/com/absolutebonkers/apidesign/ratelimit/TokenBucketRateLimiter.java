package com.absolutebonkers.apidesign.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class TokenBucketRateLimiter {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final int capacity;
    private final double refillTokensPerSecond;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillTokensPerSecond, LongSupplier nanoTime) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (!Double.isFinite(refillTokensPerSecond) || refillTokensPerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be finite and positive");
        }
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    public RateLimitDecision tryConsume(String clientKey) {
        return tryConsume(clientKey, 1);
    }

    public RateLimitDecision tryConsume(String clientKey, int cost) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalArgumentException("Client key must not be blank");
        }
        if (cost < 1 || cost > capacity) {
            throw new IllegalArgumentException("Cost must be between 1 and bucket capacity");
        }
        Bucket bucket = buckets.computeIfAbsent(clientKey, ignored -> new Bucket(capacity, nanoTime.getAsLong()));
        return bucket.consume(cost, nanoTime.getAsLong(), refillTokensPerSecond);
    }

    private static final class Bucket {
        private final int capacity;
        private double tokens;
        private long lastRefillNanos;

        private Bucket(int capacity, long now) {
            this.capacity = capacity;
            tokens = capacity;
            lastRefillNanos = now;
        }

        private synchronized RateLimitDecision consume(int cost, long now, double refillRate) {
            long elapsed = now - lastRefillNanos;
            if (elapsed < 0) {
                throw new IllegalStateException("Monotonic clock moved backwards");
            }
            tokens = Math.min(tokens + elapsed / NANOS_PER_SECOND * refillRate, capacity);
            lastRefillNanos = now;

            if (tokens >= cost) {
                tokens -= cost;
                return new RateLimitDecision(true, (int) Math.floor(tokens), Duration.ZERO);
            }
            double waitNanos = Math.ceil((cost - tokens) / refillRate * NANOS_PER_SECOND);
            long retryNanos = waitNanos >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) waitNanos;
            return new RateLimitDecision(false, (int) Math.floor(tokens), Duration.ofNanos(retryNanos));
        }
    }
}
