package com.absolutebonkers.apidesign.pagination;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CatalogPager {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Comparator<Item> ORDER = Comparator.comparing(Item::createdAt).thenComparing(Item::id);

    private final CursorCodec cursors;

    public CatalogPager(CursorCodec cursors) {
        this.cursors = Objects.requireNonNull(cursors);
    }

    public Page<Item> page(List<Item> source, int pageSize, String cursor) {
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        List<Item> ordered = source.stream().sorted(ORDER).toList();
        CursorCodec.Position position = cursor == null ? null : cursors.decode(cursor);
        List<Item> afterCursor = position == null ? ordered : ordered.stream()
                .filter(item -> ORDER.compare(item,
                        new Item(position.id(), position.createdAt(), "")) > 0)
                .toList();

        List<Item> result = afterCursor.stream().limit(pageSize).toList();
        boolean hasMore = afterCursor.size() > pageSize;
        String nextCursor = hasMore ? cursors.encode(result.getLast()) : null;
        return new Page<>(result, nextCursor);
    }
}
