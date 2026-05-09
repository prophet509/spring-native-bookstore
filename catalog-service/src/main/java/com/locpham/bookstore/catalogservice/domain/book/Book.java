package com.locpham.bookstore.catalogservice.domain.book;

import com.locpham.bookstore.catalogservice.domain.audit.AuditMetadata;

public record Book(
        Long id,
        String isbn,
        String title,
        String author,
        Double price,
        String publisher,
        AuditMetadata auditMetadata) {
    public static Book build(
            String isbn, String title, String author, Double price, String publisher) {
        return new Book(null, isbn, title, author, price, publisher, null);
    }

    public Book updateWith(Book partial) {
        return new Book(
                this.id,
                this.isbn,
                partial.title() != null ? partial.title() : this.title,
                partial.author() != null ? partial.author() : this.author,
                partial.price() != null ? partial.price() : this.price,
                partial.publisher() != null ? partial.publisher() : this.publisher,
                this.auditMetadata);
    }
}
