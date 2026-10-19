package com.absolutebonkers.apidesign.pagination;

import java.util.List;

public record OffsetPage<T>(List<T> items, Integer nextOffset) {
    public OffsetPage {
        items = List.copyOf(items);
    }
}
