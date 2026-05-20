package com.locpham.bookstore.orderservice.domain.exception;

public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String isbn, Throwable cause) {
        super("Catalog service unavailable while fetching book with ISBN '" + isbn + "'", cause);
    }
}
