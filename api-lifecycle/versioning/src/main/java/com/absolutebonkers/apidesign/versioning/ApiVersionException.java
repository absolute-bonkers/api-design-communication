package com.absolutebonkers.apidesign.versioning;

public final class ApiVersionException extends RuntimeException {
    private final int statusCode;

    public ApiVersionException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
