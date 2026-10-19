package com.absolutebonkers.apidesign.pagination;

import java.util.List;
import java.util.Objects;

public final class OffsetPager {
    private static final int MAX_PAGE_SIZE = 100;

    public <T> OffsetPage<T> page(List<T> source, int offset, int pageSize) {
        Objects.requireNonNull(source);
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must not be negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset >= source.size()) {
            return new OffsetPage<>(List.of(), null);
        }
        int end = (int) Math.min((long) source.size(), (long) offset + pageSize);
        Integer nextOffset = end < source.size() ? end : null;
        return new OffsetPage<>(source.subList(offset, end), nextOffset);
    }
}
