package com.absolutebonkers.apidesign.gateway;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.TreeMap;

public record ApiRequest(String method, String path, Map<String, String> headers, String body) {
    public ApiRequest {
        Objects.requireNonNull(method);
        Objects.requireNonNull(path);
        Objects.requireNonNull(headers);
        var normalizedHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, value) -> {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
            if (normalizedHeaders.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate case-insensitive header: " + name);
            }
        });
        headers = Collections.unmodifiableMap(normalizedHeaders);
        Objects.requireNonNull(body);
        if (method.isBlank()) {
            throw new IllegalArgumentException("HTTP method must not be blank");
        }
    }

    public String header(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
