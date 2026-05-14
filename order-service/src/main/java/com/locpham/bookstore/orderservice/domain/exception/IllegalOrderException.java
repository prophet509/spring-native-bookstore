package com.locpham.bookstore.orderservice.domain.exception;

public class IllegalOrderException extends RuntimeException {

    public IllegalOrderException(String message) {
        super(message);
    }

    public IllegalOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
