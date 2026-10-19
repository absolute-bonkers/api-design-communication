package com.absolutebonkers.apidesign.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogPagerTest {
    private final CatalogPager pager = new CatalogPager(new CursorCodec(
            "a sufficiently long test-only signing secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    private final List<Item> items = List.of(
            new Item("b", Instant.parse("2026-01-01T00:00:00Z"), "second"),
            new Item("a", Instant.parse("2026-01-01T00:00:00Z"), "first"),
            new Item("c", Instant.parse("2026-01-02T00:00:00Z"), "third"));

    @Test
    void returnsStablePagesWithoutRepeatingEqualTimestampItems() {
        var first = pager.page(items, 2, null);
        var second = pager.page(items, 2, first.nextCursor());

        assertEquals(List.of("a", "b"), first.items().stream().map(Item::id).toList());
        assertEquals(List.of("c"), second.items().stream().map(Item::id).toList());
        assertNull(second.nextCursor());
    }

    @Test
    void rejectsTamperedMalformedAndWronglySignedCursors() {
        String cursor = pager.page(items, 1, null).nextCursor();
        assertThrows(InvalidCursorException.class,
                () -> pager.page(items, 1, cursor.substring(0, cursor.length() - 1) + "x"));
        assertThrows(InvalidCursorException.class, () -> pager.page(items, 1, "not-a-cursor"));

        var otherPager = new CatalogPager(new CursorCodec(
                "a different sufficiently long signing secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(InvalidCursorException.class, () -> otherPager.page(items, 1, cursor));
    }

    @Test
    void validatesPageSizeAndSecretStrength() {
        assertThrows(IllegalArgumentException.class, () -> pager.page(items, 0, null));
        assertThrows(IllegalArgumentException.class, () -> pager.page(items, 101, null));
        assertThrows(IllegalArgumentException.class, () -> new CursorCodec(new byte[31]));
    }

    @Test
    void offsetPaginationSupportsNumberedPagesAndOutOfRangeOffsets() {
        var offsetPager = new OffsetPager();
        var first = offsetPager.page(List.of("a", "b", "c", "d"), 0, 2);
        var second = offsetPager.page(List.of("a", "b", "c", "d"), first.nextOffset(), 2);
        var outside = offsetPager.page(List.of("a", "b", "c", "d"), 10, 2);

        assertEquals(List.of("a", "b"), first.items());
        assertEquals(2, first.nextOffset());
        assertEquals(List.of("c", "d"), second.items());
        assertNull(second.nextOffset());
        assertEquals(List.of(), outside.items());
        assertNull(outside.nextOffset());
    }

    @Test
    void offsetPagerValidatesBounds() {
        var offsetPager = new OffsetPager();
        assertThrows(IllegalArgumentException.class, () -> offsetPager.page(items, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> offsetPager.page(items, 0, 0));
    }
}
