package com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka.message;

import com.locpham.bookstore.searchservice.domain.BookDocument;

public record BookCreatedMessage(
        String isbn, String title, String author, Double price, String publisher) {
    public BookDocument toDomain() {
        return new BookDocument(isbn(), title(), author(), price(), publisher());
    }
}
