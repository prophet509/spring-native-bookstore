package com.locpham.bookstore.orderservice.domain.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String isbn, int requested, int available) {
        super(
                String.format(
                        "Insufficient stock for book ISBN '%s': requested %d, available %d",
                        isbn, requested, available));
    }
}
