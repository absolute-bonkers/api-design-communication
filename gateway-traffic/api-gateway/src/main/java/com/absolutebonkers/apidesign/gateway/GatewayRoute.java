package com.absolutebonkers.apidesign.gateway;

import java.util.Objects;

public record GatewayRoute(String publicPrefix, String backendPrefix, String apiKey, Backend backend) {
    public GatewayRoute {
        Objects.requireNonNull(publicPrefix);
        Objects.requireNonNull(backendPrefix);
        Objects.requireNonNull(backend);
        if (!validPrefix(publicPrefix) || !validPrefix(backendPrefix)) {
            throw new IllegalArgumentException("Route prefixes must be absolute paths without trailing slashes");
        }
        if (apiKey != null && apiKey.isBlank()) {
            throw new IllegalArgumentException("API key must not be blank");
        }
    }

    private static boolean validPrefix(String prefix) {
        if (!prefix.startsWith("/") || prefix.length() <= 1 || prefix.endsWith("/")
                || prefix.contains("?") || prefix.contains("#") || prefix.contains("\\")
                || prefix.matches("(?i).*%(2e|2f|5c).*")) {
            return false;
        }
        for (String segment : prefix.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    boolean matches(String path) {
        return path.equals(publicPrefix) || path.startsWith(publicPrefix + "/");
    }
}
