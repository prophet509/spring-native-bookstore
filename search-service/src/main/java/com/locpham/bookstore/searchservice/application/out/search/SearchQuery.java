package com.locpham.bookstore.searchservice.application.out.search;

import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SearchQuery {
    Mono<SearchPage<BookDocument>> searchByTitle(String title, Pageable pageable);

    Mono<SearchPage<BookDocument>> searchByAuthor(String author, Pageable pageable);

    Mono<SearchPage<BookDocument>> searchByPublisher(String publisher, Pageable pageable);

    Mono<SearchPage<BookDocument>> searchByIsbn(String isbn, Pageable pageable);

    Mono<SearchPage<BookDocument>> searchAll(Pageable pageable);

    Flux<String> suggestByTitle(String prefix);

    Flux<String> suggestByAuthor(String prefix);
}
