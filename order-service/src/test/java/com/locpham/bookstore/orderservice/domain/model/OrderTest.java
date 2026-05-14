package com.locpham.bookstore.orderservice.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import com.locpham.bookstore.orderservice.domain.exception.IllegalOrderException;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void accept() {
        var order = Order.createPending("1234567890", "Test Book", 9.99, 2);

        var acceptedOrder = order.accept();

        assertEquals(OrderStatus.ACCEPTED, acceptedOrder.status());
        assertEquals(order.version() + 1, acceptedOrder.version());
    }

    @Test
    void accept_shouldThrowException_whenOrderNotPending() {
        var acceptedOrder = Order.createAccepted("1234567890", "Test Book", 9.99, 2);

        var exception = assertThrows(IllegalOrderException.class, acceptedOrder::accept);
        assertEquals("Order must be PENDING to accept", exception.getMessage());
    }

    @Test
    void reject() {

        var order = Order.createPending("1234567890", "Test Book", 9.99, 2);

        var rejectedOrder = order.reject();

        assertEquals(OrderStatus.REJECTED, rejectedOrder.status());
        assertEquals(order.version() + 1, rejectedOrder.version());
    }

    @Test
    void markDispatched() {
        var order = Order.createAccepted("1234567890", "Test Book", 9.99, 2);

        var dispatchedOrder = order.markDispatched();

        assertEquals(OrderStatus.DISPATCHED, dispatchedOrder.status());
    }

    @Test
    void markDispatched_shouldThrowException_whenOrderNotAccepted() {
        var order = Order.createPending("1234567890", "Test Book", 9.99, 2);

        var exception = assertThrows(IllegalOrderException.class, order::markDispatched);
        assertEquals("Order must be ACCEPTED to mark as dispatched", exception.getMessage());
    }

    @Test
    void reject_shouldThrowException_whenOrderNotPending() {
        var order = Order.createAccepted("1234567890", "Test Book", 9.99, 2);

        var exception = assertThrows(IllegalOrderException.class, order::reject);
        assertEquals("Order must be PENDING to reject", exception.getMessage());
    }

    @Test
    void shouldThrowException_whenQuantityZero() {
        var exception =
                assertThrows(
                        IllegalOrderException.class,
                        () -> Order.createPending("1234567890", "Test Book", 9.99, 0));
        assertEquals("Quantity must be positive", exception.getMessage());
    }

    @Test
    void shouldThrowException_whenQuantityNegative() {
        var exception =
                assertThrows(
                        IllegalOrderException.class,
                        () -> Order.createPending("1234567890", "Test Book", 9.99, -1));
        assertEquals("Quantity must be positive", exception.getMessage());
    }

    @Test
    void shouldThrowException_whenBookNull() {
        var exception =
                assertThrows(
                        IllegalOrderException.class,
                        () ->
                                new Order(
                                        null,
                                        null,
                                        1,
                                        OrderStatus.PENDING,
                                        AuditMetadata.init(),
                                        0));
        assertEquals("Book must not be null", exception.getMessage());
    }

    @Test
    void shouldThrowException_whenStatusNull() {
        var exception =
                assertThrows(
                        IllegalOrderException.class,
                        () ->
                                new Order(
                                        null,
                                        new BookInfo("1234567890", "Test Book", 9.99),
                                        1,
                                        null,
                                        AuditMetadata.init(),
                                        0));
        assertEquals("Status must not be null", exception.getMessage());
    }

    @Test
    void shouldThrowException_whenAuditNull() {
        var exception =
                assertThrows(
                        IllegalOrderException.class,
                        () ->
                                new Order(
                                        null,
                                        new BookInfo("1234567890", "Test Book", 9.99),
                                        1,
                                        OrderStatus.PENDING,
                                        null,
                                        0));
        assertEquals("Audit must not be null", exception.getMessage());
    }

    @Test
    void shouldThrowException_whenVersionNegative() {
        var exception =
                assertThrows(
                        IllegalOrderException.class,
                        () ->
                                new Order(
                                        null,
                                        new BookInfo("1234567890", "Test Book", 9.99),
                                        1,
                                        OrderStatus.PENDING,
                                        AuditMetadata.init(),
                                        -1));
        assertEquals("Version must be non-negative", exception.getMessage());
    }
}
