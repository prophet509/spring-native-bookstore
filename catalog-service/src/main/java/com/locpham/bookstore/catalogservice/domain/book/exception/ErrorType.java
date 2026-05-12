package com.locpham.bookstore.catalogservice.domain.book.exception;

import java.net.URI;

public enum ErrorType {
    BOOK_NOT_FOUND(URI.create("https://bookstore.api/errors/book-not-found"), "Book not found"),
    BOOK_ALREADY_EXISTS(URI.create("https://bookstore.api/errors/book-already-exists"), "Book already exists"),
    VALIDATION_FAILED(URI.create("https://bookstore.api/errors/validation-failed"), "Validation failed");

    private final URI type;
    private final String title;

    ErrorType(URI type, String title) {
        this.type = type;
        this.title = title;
    }

    public URI getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }
}
