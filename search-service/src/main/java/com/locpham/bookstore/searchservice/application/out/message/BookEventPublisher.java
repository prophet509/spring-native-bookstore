package com.locpham.bookstore.searchservice.application.out.message;


import com.locpham.bookstore.searchservice.domain.BookDocument;
import reactor.core.publisher.Mono;

public interface BookEventPublisher {
    Mono<Void> publishBookCreated(BookDocument book);
    Mono<Void> publishBookUpdated(BookDocument book);
    Mono<Void> publishBookDeleted(String isbn);
}
