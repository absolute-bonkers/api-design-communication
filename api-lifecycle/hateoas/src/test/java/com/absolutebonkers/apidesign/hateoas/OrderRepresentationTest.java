package com.absolutebonkers.apidesign.hateoas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderRepresentationTest {
    @Test
    void offersOnlyActionsValidForANewOrder() {
        var order = OrderRepresentation.forOrder("42", OrderStatus.CREATED);

        assertEquals("/api/v1/orders/42", order.links().get("self").href());
        assertEquals("POST", order.links().get("pay").method());
        assertEquals("POST", order.links().get("cancel").method());
        assertFalse(order.links().containsKey("receipt"));
        assertTrue(order.asJson().containsKey("_links"));
    }

    @Test
    void paidAndCancelledOrdersExposeDifferentFollowUpLinks() {
        var paid = OrderRepresentation.forOrder("42", OrderStatus.PAID);
        var cancelled = OrderRepresentation.forOrder("43", OrderStatus.CANCELLED);

        assertTrue(paid.links().containsKey("receipt"));
        assertFalse(paid.links().containsKey("pay"));
        assertTrue(cancelled.links().containsKey("cancellation"));
        assertFalse(cancelled.links().containsKey("pay"));
    }

    @Test
    void rejectsIdsThatCouldChangeTheLinkPath() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderRepresentation.forOrder("../admin", OrderStatus.CREATED));
    }
}
