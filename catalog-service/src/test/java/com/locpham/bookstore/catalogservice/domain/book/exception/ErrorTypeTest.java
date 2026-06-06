package com.locpham.bookstore.catalogservice.domain.book.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorTypeTest {

    @Test
    void exposesTypeAndTitle() {
        assertThat(ErrorType.BOOK_NOT_FOUND.getType().toString())
                .isEqualTo("https://bookstore.api/errors/book-not-found");
        assertThat(ErrorType.BOOK_NOT_FOUND.getTitle()).isEqualTo("Book not found");
        assertThat(ErrorType.BOOK_ALREADY_EXISTS.getTitle()).isEqualTo("Book already exists");
        assertThat(ErrorType.VALIDATION_FAILED.getType().toString())
                .isEqualTo("https://bookstore.api/errors/validation-failed");
    }

    @Test
    void hasExpectedValues() {
        assertThat(ErrorType.values()).hasSize(3);
        assertThat(ErrorType.valueOf("BOOK_NOT_FOUND")).isEqualTo(ErrorType.BOOK_NOT_FOUND);
    }
}
