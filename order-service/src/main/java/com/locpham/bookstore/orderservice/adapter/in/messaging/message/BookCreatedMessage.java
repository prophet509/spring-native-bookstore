package com.locpham.bookstore.orderservice.adapter.in.messaging.message;

public record BookCreatedMessage(
        String isbn, String title, String author, Double price, String publisher) {}
