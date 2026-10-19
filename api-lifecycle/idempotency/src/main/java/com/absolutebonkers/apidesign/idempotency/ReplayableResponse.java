package com.absolutebonkers.apidesign.idempotency;

import java.util.Map;

public record ReplayableResponse(int status, Map<String, String> headers, String body) {
    public ReplayableResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("HTTP status must be between 100 and 599");
        }
        headers = Map.copyOf(headers);
        if (body == null) {
            throw new IllegalArgumentException("Response body must not be null");
        }
    }
}
