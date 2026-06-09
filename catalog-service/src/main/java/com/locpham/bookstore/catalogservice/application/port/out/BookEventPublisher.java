package com.locpham.bookstore.catalogservice.application.port.out;

import com.locpham.bookstore.catalogservice.domain.book.Book;

/**
 * Outbound port for catalog book events. Implementations are expected to be synchronous and
 * participate in the caller's {@link
 * org.springframework.transaction.annotation.Transactional @Transactional} boundary so the event
 * record commits atomically with the domain write (transactional outbox pattern).
 */
public interface BookEventPublisher {

    void publishBookCreated(Book book);

    void publishBookUpdated(Book book);

    void publishBookDeleted(String isbn);
}
