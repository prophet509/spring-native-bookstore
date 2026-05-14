package com.locpham.bookstore.orderservice.domain.exception;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(String isbn) {
        super("Book with ISBN '" + isbn + "' not found in catalog");
    }
}
