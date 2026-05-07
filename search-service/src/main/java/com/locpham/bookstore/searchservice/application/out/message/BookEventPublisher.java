package com.locpham.bookstore.searchservice.application.out.message;

import java.awt.print.Book;

public interface BookEventPublisher {
    void publishBookCreated(Book book);
    void publishBookUpdated(Book book);
    void publishBookDeleted(String isbn);
}
