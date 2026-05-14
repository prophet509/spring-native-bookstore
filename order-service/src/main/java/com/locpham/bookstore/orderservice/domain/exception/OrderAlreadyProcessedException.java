package com.locpham.bookstore.orderservice.domain.exception;

public class OrderAlreadyProcessedException extends RuntimeException {

    public OrderAlreadyProcessedException(Long orderId) {
        super(String.format("Order with ID '%d' has already been processed", orderId));
    }
}
