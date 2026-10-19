package com.absolutebonkers.apidesign.gateway;

@FunctionalInterface
public interface Backend {
    ApiResponse forward(ApiRequest request);
}
