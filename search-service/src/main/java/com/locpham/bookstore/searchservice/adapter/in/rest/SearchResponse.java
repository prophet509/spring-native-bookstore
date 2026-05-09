package com.locpham.bookstore.searchservice.adapter.in.rest;

public record SearchResponse(
        String isbn, String title, String author, Double price, String publisher) {
    public static SearchResponse from(com.locpham.bookstore.searchservice.domain.BookDocument doc) {
        return new SearchResponse(
                doc.isbn(), doc.title(), doc.author(), doc.price(), doc.publisher());
    }
}
