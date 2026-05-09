package com.locpham.bookstore.searchservice.application.service;

import com.locpham.bookstore.searchservice.application.in.SearchBookUseCase;
import com.locpham.bookstore.searchservice.application.out.search.SearchQuery;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SearchBookService implements SearchBookUseCase {

    private final SearchQuery searchQuery;

    public SearchBookService(SearchQuery searchQuery) {
        this.searchQuery = searchQuery;
    }

    @Override
    public Mono<SearchPage<BookDocument>> search(String query, Pageable pageable) {
        return searchQuery.searchByTitle(query, pageable);
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByAuthor(String author, Pageable pageable) {
        return searchQuery.searchByAuthor(author, pageable);
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByPublisher(String publisher, Pageable pageable) {
        return searchQuery.searchByPublisher(publisher, pageable);
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByIsbn(String isbn, Pageable pageable) {
        return searchQuery.searchByIsbn(isbn, pageable);
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchAll(Pageable pageable) {
        return searchQuery.searchAll(pageable);
    }

    @Override
    public Flux<String> suggestByTitle(String prefix) {
        return searchQuery.suggestByTitle(prefix);
    }

    @Override
    public Flux<String> suggestByAuthor(String prefix) {
        return searchQuery.suggestByAuthor(prefix);
    }
}
