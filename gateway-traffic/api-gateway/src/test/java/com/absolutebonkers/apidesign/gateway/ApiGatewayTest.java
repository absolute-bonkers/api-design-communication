package com.absolutebonkers.apidesign.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ApiGatewayTest {
    @Test
    void selectsTheLongestRouteAndForwardsASanitizedCorrelatedRequest() {
        var forwarded = new AtomicReference<ApiRequest>();
        Backend backend = request -> {
            forwarded.set(request);
            return new ApiResponse(200,
                    Map.of("Content-Type", "application/json", "Connection", "keep-alive"), "ok");
        };
        var gateway = new ApiGateway(List.of(
                new GatewayRoute("/api", "/public", null, request -> new ApiResponse(200, Map.of(), "general")),
                new GatewayRoute("/api/v1/orders", "/orders", "secret", backend)));

        ApiResponse response = gateway.handle(new ApiRequest(
                "GET", "/api/v1/orders/42",
                Map.of("X-API-KEY", "secret", "X-Request-Id", "client-value",
                        "Connection", "keep-alive, X-Private", "X-Private", "private",
                        "Accept", "application/json",
                        "X-Forwarded-For", "198.51.100.4"), ""));

        assertEquals(200, response.status());
        assertEquals("/orders/42", forwarded.get().path());
        assertEquals("application/json", forwarded.get().header("Accept"));
        assertNull(forwarded.get().header("X-Api-Key"));
        assertNull(forwarded.get().header("Connection"));
        assertNull(forwarded.get().header("X-Private"));
        assertNull(forwarded.get().header("X-Forwarded-For"));
        assertNotNull(forwarded.get().header("X-Request-Id"));
        assertNotNull(response.headers().get("X-Request-Id"));
        assertFalse(response.headers().containsKey("Connection"));
        assertFalse("client-value".equals(response.headers().get("X-Request-Id")));
    }

    @Test
    void removesResponseHeadersNominatedByConnection() {
        var gateway = new ApiGateway(List.of(new GatewayRoute(
                "/api", "/backend", null,
                request -> new ApiResponse(200,
                        Map.of("Connection", "X-Backend-Only", "X-Backend-Only", "private"), "ok"))));

        ApiResponse response = gateway.handle(new ApiRequest("GET", "/api",
                Map.of(), ""));

        assertEquals(200, response.status());
        assertFalse(response.headers().containsKey("Connection"));
        assertFalse(response.headers().containsKey("X-Backend-Only"));
    }

    @Test
    void rejectsUnauthorizedAndUnmatchedRequests() {
        var gateway = new ApiGateway(List.of(
                new GatewayRoute("/api/orders", "/orders", "secret",
                        request -> new ApiResponse(200, Map.of(), "ok"))));

        assertEquals(401, gateway.handle(new ApiRequest("GET", "/api/orders",
                Map.of(), "")).status());
        assertEquals(404, gateway.handle(new ApiRequest("GET", "/unknown",
                Map.of(), "")).status());
    }

    @Test
    void rejectsUnsafePathsAndMapsBackendUnavailableToBadGateway() {
        var gateway = new ApiGateway(List.of(new GatewayRoute(
                "/api/orders", "/orders", null,
                request -> {
                    throw new GatewayBackendException("connection refused", null);
                })));

        assertEquals(400, gateway.handle(new ApiRequest("GET", "/api/orders/../admin",
                Map.of(), "")).status());
        assertEquals(400, gateway.handle(new ApiRequest("GET", "/api/orders/%2e%2e/admin",
                Map.of(), "")).status());
        ApiResponse unavailable = gateway.handle(new ApiRequest("GET", "/api/orders/42",
                Map.of(), ""));
        assertEquals(502, unavailable.status());
        assertTrue(unavailable.headers().containsKey("X-Request-Id"));
    }

    @Test
    void validatesRouteConfiguration() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ApiGateway(List.of(
                        new GatewayRoute("/api/orders", "/orders", null, request -> new ApiResponse(200, Map.of(), "")),
                        new GatewayRoute("/api/orders", "/other", null, request -> new ApiResponse(200, Map.of(), "")))))
                .getMessage().contains("Duplicate"));
        assertThrows(IllegalArgumentException.class, () -> new ApiRequest(
                "GET", "/", Map.of("X-Api-Key", "first", "x-api-key", "second"), ""));
    }
}
