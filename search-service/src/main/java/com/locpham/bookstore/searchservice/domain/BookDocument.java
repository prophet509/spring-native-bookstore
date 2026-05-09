package com.locpham.bookstore.searchservice.domain;

public record BookDocument(
        String isbn, String title, String author, Double price, String publisher) {}
