package com.locpham.bookstore.orderservice.application.port.out;

import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import reactor.core.publisher.Mono;

public interface CatalogBookSnapshotPort {
    Mono<BookSnapshot> findByIsbn(String isbn);

    Mono<Long> upsert(String isbn, String title, Double price);

    Mono<Long> deleteByIsbn(String isbn);
}
