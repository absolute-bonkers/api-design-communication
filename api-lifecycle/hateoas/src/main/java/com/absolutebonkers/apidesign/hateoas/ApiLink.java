package com.absolutebonkers.apidesign.hateoas;

import java.util.Objects;

public record ApiLink(String href, String method) {
    public ApiLink {
        Objects.requireNonNull(href);
        Objects.requireNonNull(method);
        if (!href.startsWith("/") || method.isBlank()) {
            throw new IllegalArgumentException("Links require a relative API path and HTTP method");
        }
    }
}
