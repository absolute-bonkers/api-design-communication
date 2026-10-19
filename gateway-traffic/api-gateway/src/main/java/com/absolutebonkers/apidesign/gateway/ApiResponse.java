package com.absolutebonkers.apidesign.gateway;

import java.util.Map;
import java.util.Objects;

public record ApiResponse(int status, Map<String, String> headers, String body) {
    public ApiResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("HTTP status must be between 100 and 599");
        }
        headers = Map.copyOf(headers);
        Objects.requireNonNull(body);
    }
}
