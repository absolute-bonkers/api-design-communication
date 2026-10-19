package com.absolutebonkers.apidesign.hateoas;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record OrderRepresentation(String id, OrderStatus status, Map<String, ApiLink> links) {
    public OrderRepresentation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        if (!id.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Order id must contain only letters, digits, '_' or '-'");
        }
        links = Map.copyOf(links);
    }

    public static OrderRepresentation forOrder(String id, OrderStatus status) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        String self = "/api/v1/orders/" + id;
        var links = new LinkedHashMap<String, ApiLink>();
        links.put("self", new ApiLink(self, "GET"));
        links.put("collection", new ApiLink("/api/v1/orders", "GET"));
        switch (status) {
            case CREATED -> {
                links.put("pay", new ApiLink(self + "/payments", "POST"));
                links.put("cancel", new ApiLink(self + "/cancellation", "POST"));
            }
            case PAID -> links.put("receipt", new ApiLink(self + "/receipt", "GET"));
            case CANCELLED -> links.put("cancellation", new ApiLink(self + "/cancellation", "GET"));
        }
        return new OrderRepresentation(id, status, links);
    }

    public Map<String, Object> asJson() {
        return Map.of(
                "id", id,
                "status", status.name(),
                "_links", links.entrySet().stream().collect(
                        java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> Map.of("href", entry.getValue().href(),
                                        "method", entry.getValue().method()))));
    }
}
