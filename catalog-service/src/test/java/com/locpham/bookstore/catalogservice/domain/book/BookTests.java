package com.locpham.bookstore.catalogservice.domain.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookTests {

    @Test
    void buildShouldCreateBookWithNullIdAndAuditMetadata() {
        var book = Book.build("1234567890", "Title", "Author", 9.90, "Polarsophia");

        assertThat(book.id()).isNull();
        assertThat(book.isbn()).isEqualTo("1234567890");
        assertThat(book.title()).isEqualTo("Title");
        assertThat(book.author()).isEqualTo("Author");
        assertThat(book.price()).isEqualTo(9.90);
        assertThat(book.publisher()).isEqualTo("Polarsophia");
        assertThat(book.auditMetadata()).isNull();
    }

    @Test
    void updateWithShouldOverrideOnlyProvidedFields() {
        var existing = Book.build("1234567890", "Old", "Old", 1.0, "OldPub");
        var partial = Book.build(null, "NewTitle", null, 99.99, null);

        var updated = existing.updateWith(partial);

        assertThat(updated.isbn()).isEqualTo("1234567890");
        assertThat(updated.title()).isEqualTo("NewTitle");
        assertThat(updated.author()).isEqualTo("Old");
        assertThat(updated.price()).isEqualTo(99.99);
        assertThat(updated.publisher()).isEqualTo("OldPub");
    }

    @Test
    void updateWithShouldKeepOriginalIdAndAudit() {
        var original =
                new Book(
                        42L,
                        "1234567890",
                        "Old",
                        "Old",
                        1.0,
                        "OldPub",
                        new com.locpham.bookstore.catalogservice.domain.audit.AuditMetadata(
                                null, null, 0));
        var partial = Book.build(null, "NewTitle", null, null, null);

        var updated = original.updateWith(partial);

        assertThat(updated.id()).isEqualTo(42L);
        assertThat(updated.auditMetadata()).isEqualTo(original.auditMetadata());
    }
}
