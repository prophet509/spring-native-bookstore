package com.locpham.bookstore.catalogservice.application.port.out;

import com.locpham.bookstore.catalogservice.domain.book.Book;
import reactor.core.publisher.Mono;

public interface BookEventPublisher {

    Mono<Void> publishBookCreated(Book book);

    Mono<Void> publishBookUpdated(Book book);

    Mono<Void> publishBookDeleted(String isbn);
}
