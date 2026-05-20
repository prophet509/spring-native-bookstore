package com.locpham.bookstore.inventoryservice.domain;

import java.util.UUID;

public record Reservation(
        UUID reservationId, Long orderId, String isbn, int quantity, ReservationStatus status) {

    public Reservation {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (isbn == null || isbn.isBlank()) {
            throw new IllegalArgumentException("ISBN must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }
    }

    public static Reservation create(Long orderId, String isbn, int quantity) {
        return new Reservation(null, orderId, isbn, quantity, ReservationStatus.RESERVED);
    }

    public Reservation markReleased() {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Reservation must be RESERVED to release");
        }
        return new Reservation(reservationId, orderId, isbn, quantity, ReservationStatus.RELEASED);
    }
}
