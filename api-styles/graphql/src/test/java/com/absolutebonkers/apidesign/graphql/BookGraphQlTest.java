package com.absolutebonkers.apidesign.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BookGraphQlTest {
    private final BookGraphQl api = new BookGraphQl();

    @Test
    void returnsOnlyTheFieldsSelectedByTheClient() {
        var result = api.execute("""
                query FindBook($id: ID!) {
                  book(id: $id) { title }
                }
                """, Map.of("id", "1"));

        assertTrue(result.getErrors().isEmpty());
        assertEquals(Map.of("book", Map.of("title", "Designing Data-Intensive Applications")),
                result.getData());
    }

    @Test
    void returnsNullForAMissingBookAndReportsInvalidFields() {
        var missing = api.execute("{ book(id: \"404\") { title } }", Map.of());
        assertTrue(missing.getErrors().isEmpty());
        assertEquals(java.util.Collections.singletonMap("book", null), missing.getData());

        var invalid = api.execute("{ books { unknownField } }", Map.of());
        assertFalse(invalid.getErrors().isEmpty());
    }

    @Test
    void listsBooksInStableOrder() {
        var result = api.execute("{ books { id } }", Map.of());

        assertTrue(result.getErrors().isEmpty());
        assertEquals(Map.of("books", java.util.List.of(
                Map.of("id", "1"), Map.of("id", "2"))), result.getData());
    }
}
