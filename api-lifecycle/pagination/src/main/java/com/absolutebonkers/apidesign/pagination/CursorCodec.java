package com.absolutebonkers.apidesign.pagination;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CursorCodec {
    private final byte[] secret;

    public CursorCodec(byte[] secret) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("Cursor signing secret must be at least 32 bytes");
        }
        this.secret = secret.clone();
    }

    public String encode(Item item) {
        String payload = item.createdAt().getEpochSecond() + ":"
                + item.createdAt().getNano() + ":" + item.id();
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payloadBytes));
    }

    public Position decode(String cursor) {
        try {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2) {
                throw new InvalidCursorException("Cursor is malformed");
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(signature, sign(payload))) {
                throw new InvalidCursorException("Cursor signature is invalid");
            }
            String[] fields = new String(payload, StandardCharsets.UTF_8).split(":", 3);
            if (fields.length != 3 || !fields[2].matches("[A-Za-z0-9_-]+")) {
                throw new InvalidCursorException("Cursor payload is invalid");
            }
            Instant createdAt = Instant.ofEpochSecond(
                    Long.parseLong(fields[0]), Long.parseLong(fields[1]));
            return new Position(createdAt, fields[2]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCursorException("Cursor is malformed");
        } catch (DateTimeException exception) {
            throw new InvalidCursorException("Cursor timestamp is invalid");
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    public record Position(Instant createdAt, String id) {}
}
