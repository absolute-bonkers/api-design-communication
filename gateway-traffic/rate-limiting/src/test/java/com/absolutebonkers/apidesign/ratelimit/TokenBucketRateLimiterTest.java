package com.absolutebonkers.apidesign.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {
    @Test
    void allowsABoundedBurstThenReportsRetryDelay() {
        var clock = new AtomicLong();
        var limiter = new TokenBucketRateLimiter(3, 2, clock::get);

        assertTrue(limiter.tryConsume("client").allowed());
        assertTrue(limiter.tryConsume("client").allowed());
        assertTrue(limiter.tryConsume("client").allowed());
        var denied = limiter.tryConsume("client");
        assertFalse(denied.allowed());
        assertEquals(Duration.ofMillis(500), denied.retryAfter());

        clock.set(Duration.ofMillis(500).toNanos());
        assertTrue(limiter.tryConsume("client").allowed());
    }

    @Test
    void maintainsIndependentBucketsAndSupportsWeightedRequests() {
        var limiter = new TokenBucketRateLimiter(3, 1, () -> 0);

        assertTrue(limiter.tryConsume("client-a", 2).allowed());
        assertEquals(0, limiter.tryConsume("client-a").remaining());
        assertEquals(1, limiter.tryConsume("client-b", 2).remaining());
        assertFalse(limiter.tryConsume("client-a", 2).allowed());
    }

    @Test
    void serializesConcurrentTokenConsumption() throws Exception {
        var limiter = new TokenBucketRateLimiter(10, 1, () -> 0);
        try (var executor = Executors.newFixedThreadPool(20)) {
            var results = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(ignored -> executor.submit(() -> limiter.tryConsume("same-client").allowed()))
                    .toList();
            long allowed = 0;
            for (var result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertEquals(10, allowed);
        }
    }

    @Test
    void validatesConfigurationAndRequests() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 1, () -> 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(1, Double.NaN, () -> 0));
        var limiter = new TokenBucketRateLimiter(2, 1, () -> 0);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryConsume(" "));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryConsume("client", 3));
    }
}
