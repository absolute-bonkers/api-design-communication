package com.absolutebonkers.apidesign.gateway;

public final class GatewayBackendException extends RuntimeException {
    public GatewayBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
