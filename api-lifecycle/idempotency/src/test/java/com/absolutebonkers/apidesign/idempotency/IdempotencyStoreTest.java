package com.absolutebonkers.apidesign.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyStoreTest {
    @Test
    void replaysTheOriginalResponseAndScopesKeys() {
        var store = new IdempotencyStore(Duration.ofMinutes(5), Clock.systemUTC());
        var calls = new AtomicInteger();
        var original = store.execute("customer-1", "key-1", "order:A",
                () -> response("order-1", calls));
        var replay = store.execute("customer-1", "key-1", "order:A",
                () -> response("unexpected", calls));

        assertEquals(original, replay);
        assertEquals(1, calls.get());
        assertEquals("order-2", store.execute("customer-2", "key-1", "order:A",
                () -> response("order-2", calls)).body());
    }

    @Test
    void rejectsAKeyReusedWithDifferentInputAndAllowsRetryAfterFailure() {
        var store = new IdempotencyStore(Duration.ofMinutes(5), Clock.systemUTC());
        store.execute("customer-1", "key-1", "order:A", () -> new ReplayableResponse(201, Map.of(), "created"));
        assertThrows(IdempotencyConflictException.class,
                () -> store.execute("customer-1", "key-1", "order:B", () -> response("unused", new AtomicInteger())));

        var attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> store.execute(
                "customer-1", "key-2", "order:C", () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("temporary failure");
                }));
        assertEquals("created", store.execute("customer-1", "key-2", "order:C", () -> {
            attempts.incrementAndGet();
            return new ReplayableResponse(201, Map.of(), "created");
        }).body());
        assertEquals(2, attempts.get());
    }

    @Test
    void expiresCompletedKeysUsingTheInjectedClock() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var store = new IdempotencyStore(Duration.ofSeconds(30), clock);
        store.execute("customer-1", "key-1", "order:A", () -> new ReplayableResponse(201, Map.of(), "first"));
        clock.advance(Duration.ofSeconds(30));

        assertEquals("second", store.execute("customer-1", "key-1", "order:A",
                () -> new ReplayableResponse(201, Map.of(), "second")).body());
    }

    @Test
    void concurrentRetriesRunTheSideEffectOnlyOnce() throws Exception {
        var store = new IdempotencyStore(Duration.ofMinutes(5), Clock.systemUTC());
        var calls = new AtomicInteger();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> executor.submit(() -> store.execute("customer", "key", "same", () -> {
                        calls.incrementAndGet();
                        started.countDown();
                        await(release);
                        return new ReplayableResponse(201, Map.of(), "created");
                    })))
                    .toList();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            release.countDown();
            for (var task : tasks) {
                assertEquals("created", task.get(5, TimeUnit.SECONDS).body());
            }
        }
        assertEquals(1, calls.get());
    }

    private static ReplayableResponse response(String body, AtomicInteger calls) {
        calls.incrementAndGet();
        return new ReplayableResponse(201, Map.of("Location", body), body);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent request");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent request", exception);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
