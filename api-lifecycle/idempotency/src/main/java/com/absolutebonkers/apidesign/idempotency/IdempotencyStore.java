package com.absolutebonkers.apidesign.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class IdempotencyStore {
    private final ConcurrentHashMap<ScopedKey, Entry> entries = new ConcurrentHashMap<>();
    private final Duration retention;
    private final Clock clock;

    public IdempotencyStore(Duration retention, Clock clock) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("Retention must be positive");
        }
        this.retention = retention;
        this.clock = Objects.requireNonNull(clock);
    }

    public ReplayableResponse execute(
            String scope,
            String key,
            String requestFingerprint,
            Supplier<ReplayableResponse> operation) {
        requireText(scope, "scope");
        requireText(key, "idempotency key");
        requireText(requestFingerprint, "request fingerprint");
        Objects.requireNonNull(operation);
        ScopedKey scopedKey = new ScopedKey(scope, key);

        while (true) {
            Instant now = clock.instant();
            Entry candidate = new Entry(requestFingerprint);
            Entry existing = entries.putIfAbsent(scopedKey, candidate);
            Entry entry = existing == null ? candidate : existing;

            if (entry.isExpired(now)) {
                entries.remove(scopedKey, entry);
                continue;
            }
            if (!entry.fingerprint.equals(requestFingerprint)) {
                throw new IdempotencyConflictException(
                        "Idempotency key was already used for a different request");
            }
            if (existing == null) {
                return executeOnce(scopedKey, entry, operation);
            }
            return awaitResponse(entry.result);
        }
    }

    private ReplayableResponse executeOnce(
            ScopedKey key, Entry entry, Supplier<ReplayableResponse> operation) {
        try {
            ReplayableResponse response = Objects.requireNonNull(
                    operation.get(), "Operation must return a response");
            entry.expiresAt = clock.instant().plus(retention);
            entry.result.complete(response);
            return response;
        } catch (RuntimeException | Error failure) {
            entries.remove(key, entry);
            entry.result.completeExceptionally(failure);
            throw failure;
        }
    }

    private static ReplayableResponse awaitResponse(CompletableFuture<ReplayableResponse> result) {
        try {
            return result.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private record ScopedKey(String scope, String key) {}

    private static final class Entry {
        private final String fingerprint;
        private final CompletableFuture<ReplayableResponse> result = new CompletableFuture<>();
        private volatile Instant expiresAt;

        private Entry(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        private boolean isExpired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }
}
