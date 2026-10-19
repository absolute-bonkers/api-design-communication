package com.absolutebonkers.apidesign.pagination;

import java.time.Instant;
import java.util.Objects;

public record Item(String id, Instant createdAt, String name) {
    public Item {
        Objects.requireNonNull(id);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(name);
        if (!id.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Id must contain only letters, digits, '_' or '-'");
        }
    }
}
