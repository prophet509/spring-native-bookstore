package com.locpham.bookstore.searchservice.adapter.in.messaging.message;

public record BookUpdatedMessage(String isbn, String title, String author, Double price, String publisher) {}

