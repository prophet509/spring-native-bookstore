package com.locpham.bookstore.catalogservice.adapter.out.messaging;

public record BookCreatedMessage(
        String isbn, String title, String author, Double price, String publisher) {}
