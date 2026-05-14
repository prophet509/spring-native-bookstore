package com.locpham.bookstore.orderservice.domain.model;

import com.locpham.bookstore.orderservice.domain.exception.IllegalOrderException;

public record Order(
        Long id,
        BookInfo book,
        int quantity,
        OrderStatus status,
        AuditMetadata audit,
        int version) {

    public Order {
        if (quantity <= 0) {
            throw new IllegalOrderException("Quantity must be positive");
        }
        if (book == null) {
            throw new IllegalOrderException("Book must not be null");
        }
        if (status == null) {
            throw new IllegalOrderException("Status must not be null");
        }
        if (audit == null) {
            throw new IllegalOrderException("Audit must not be null");
        }
        if (version < 0) {
            throw new IllegalOrderException("Version must be non-negative");
        }
    }

    public static Order createPending(String isbn, String title, double price, int quantity) {
        return createPending(isbn, title, price, quantity, null);
    }

    public static Order createPending(
            String isbn, String title, double price, int quantity, String createdBy) {
        return new Order(
                null,
                new BookInfo(isbn, title, price),
                quantity,
                OrderStatus.PENDING,
                AuditMetadata.init(createdBy),
                0);
    }

    public static Order createAccepted(String isbn, String title, double price, int quantity) {
        return createAccepted(isbn, title, price, quantity, null);
    }

    public static Order createAccepted(
            String isbn, String title, double price, int quantity, String createdBy) {
        return new Order(
                null,
                new BookInfo(isbn, title, price),
                quantity,
                OrderStatus.ACCEPTED,
                AuditMetadata.init(createdBy),
                0);
    }

    public static Order createRejected(String isbn, String title, double price, int quantity) {
        return createRejected(isbn, title, price, quantity, null);
    }

    public static Order createRejected(
            String isbn, String title, double price, int quantity, String createdBy) {
        return new Order(
                null,
                new BookInfo(isbn, title, price),
                quantity,
                OrderStatus.REJECTED,
                AuditMetadata.init(createdBy),
                0);
    }

    public Order accept() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalOrderException("Order must be PENDING to accept");
        }
        return new Order(id, book, quantity, OrderStatus.ACCEPTED, audit.update(), version + 1);
    }

    public Order reject() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalOrderException("Order must be PENDING to reject");
        }
        return new Order(id, book, quantity, OrderStatus.REJECTED, audit.update(), version + 1);
    }

    public Order markDispatched() {
        if (status != OrderStatus.ACCEPTED) {
            throw new IllegalOrderException("Order must be ACCEPTED to mark as dispatched");
        }
        return new Order(id, book, quantity, OrderStatus.DISPATCHED, audit.update(), version + 1);
    }
}
