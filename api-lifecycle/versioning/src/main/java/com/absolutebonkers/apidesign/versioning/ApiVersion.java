package com.absolutebonkers.apidesign.versioning;

public enum ApiVersion {
    V1(1, true),
    V2(2, false);

    private final int number;
    private final boolean deprecated;

    ApiVersion(int number, boolean deprecated) {
        this.number = number;
        this.deprecated = deprecated;
    }

    public int number() {
        return number;
    }

    public boolean deprecated() {
        return deprecated;
    }
}
