package com.absolutebonkers.apidesign.pagination;

public final class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String message) {
        super(message);
    }
}
