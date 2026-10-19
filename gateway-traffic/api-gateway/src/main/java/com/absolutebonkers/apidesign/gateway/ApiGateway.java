package com.absolutebonkers.apidesign.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class ApiGateway {
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");
    private static final Set<String> UNTRUSTED_FORWARDING_HEADERS = Set.of(
            "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto");
    private final java.util.List<GatewayRoute> routes;

    public ApiGateway(java.util.List<GatewayRoute> routes) {
        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException("At least one route is required");
        }
        var prefixes = new java.util.HashSet<String>();
        for (GatewayRoute route : routes) {
            if (!prefixes.add(route.publicPrefix())) {
                throw new IllegalArgumentException("Duplicate public route prefix: " + route.publicPrefix());
            }
        }
        this.routes = routes.stream()
                .sorted(Comparator.comparingInt((GatewayRoute route) -> route.publicPrefix().length()).reversed())
                .toList();
    }

    public ApiResponse handle(ApiRequest request) {
        if (!isSafePath(request.path())) {
            return new ApiResponse(400, Map.of(), "Invalid request path");
        }
        GatewayRoute route = routes.stream()
                .filter(candidate -> candidate.matches(request.path()))
                .findFirst()
                .orElse(null);
        if (route == null) {
            return new ApiResponse(404, Map.of(), "No route for request");
        }
        String requestId = UUID.randomUUID().toString();
        if (route.apiKey() != null && !matchesSecret(route.apiKey(), request.header("X-Api-Key"))) {
            return new ApiResponse(401, Map.of("X-Request-Id", requestId), "Unauthorized");
        }

        var forwardedHeaders = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        request.headers().forEach((name, value) -> {
            String normalized = name.toLowerCase(java.util.Locale.ROOT);
            if (!normalized.equals("x-api-key") && !normalized.equals("x-request-id")
                    && !HOP_BY_HOP_HEADERS.contains(normalized)
                    && !UNTRUSTED_FORWARDING_HEADERS.contains(normalized)) {
                forwardedHeaders.put(name, value);
            }
        });
        forwardedHeaders.put("X-Request-Id", requestId);
        String suffix = request.path().substring(route.publicPrefix().length());
        String backendPath = route.backendPrefix() + suffix;
        ApiRequest forwarded = new ApiRequest(
                request.method(), backendPath, forwardedHeaders, request.body());
        try {
            ApiResponse response = route.backend().forward(forwarded);
            var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            response.headers().forEach((name, value) -> {
                if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                    headers.put(name, value);
                }
            });
            headers.put("X-Request-Id", requestId);
            return new ApiResponse(response.status(), headers, response.body());
        } catch (GatewayBackendException exception) {
            System.getLogger(ApiGateway.class.getName())
                    .log(System.Logger.Level.ERROR, "API gateway backend request failed", exception);
            return new ApiResponse(502, Map.of("X-Request-Id", requestId), "Backend unavailable");
        }
    }

    private static boolean isSafePath(String path) {
        if (path == null || !path.startsWith("/") || path.contains("?") || path.contains("#")
                || path.contains("\\") || path.matches("(?i).*%(2e|2f|5c).*")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesSecret(String expected, String provided) {
        return provided != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
