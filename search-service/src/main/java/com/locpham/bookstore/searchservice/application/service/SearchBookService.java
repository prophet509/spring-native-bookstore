package com.locpham.bookstore.searchservice.application.service;

import com.locpham.bookstore.searchservice.application.in.SearchBookUseCase;
import com.locpham.bookstore.searchservice.application.out.search.SearchQuery;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SearchBookService implements SearchBookUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchBookService.class);

    private final SearchQuery searchQuery;

    public SearchBookService(SearchQuery searchQuery) {
        this.searchQuery = searchQuery;
    }

    @Override
    public Mono<SearchPage<BookDocument>> search(String query, Pageable pageable) {
        log.debug(
                "Search by title query={} page={} size={}",
                query,
                pageable.getPageNumber(),
                pageable.getPageSize());
        return searchQuery
                .searchByTitle(query, pageable)
                .doOnError(
                        e ->
                                log.error(
                                        "Search by title failed query={} error={}",
                                        query,
                                        e.getMessage(),
                                        e));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByAuthor(String author, Pageable pageable) {
        log.debug(
                "Search by author author={} page={} size={}",
                author,
                pageable.getPageNumber(),
                pageable.getPageSize());
        return searchQuery
                .searchByAuthor(author, pageable)
                .doOnError(
                        e ->
                                log.error(
                                        "Search by author failed author={} error={}",
                                        author,
                                        e.getMessage(),
                                        e));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByPublisher(String publisher, Pageable pageable) {
        log.debug(
                "Search by publisher publisher={} page={} size={}",
                publisher,
                pageable.getPageNumber(),
                pageable.getPageSize());
        return searchQuery
                .searchByPublisher(publisher, pageable)
                .doOnError(
                        e ->
                                log.error(
                                        "Search by publisher failed publisher={} error={}",
                                        publisher,
                                        e.getMessage(),
                                        e));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByIsbn(String isbn, Pageable pageable) {
        log.debug(
                "Search by ISBN isbn={} page={} size={}",
                isbn,
                pageable.getPageNumber(),
                pageable.getPageSize());
        return searchQuery
                .searchByIsbn(isbn, pageable)
                .doOnError(
                        e ->
                                log.error(
                                        "Search by ISBN failed isbn={} error={}",
                                        isbn,
                                        e.getMessage(),
                                        e));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchAll(Pageable pageable) {
        log.debug("Search all page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return searchQuery
                .searchAll(pageable)
                .doOnError(e -> log.error("Search all failed error={}", e.getMessage(), e));
    }

    @Override
    public Flux<String> suggestByTitle(String prefix) {
        log.debug("Suggest by title prefix={}", prefix);
        return searchQuery
                .suggestByTitle(prefix)
                .doOnError(
                        e ->
                                log.error(
                                        "Suggest by title failed prefix={} error={}",
                                        prefix,
                                        e.getMessage(),
                                        e));
    }

    @Override
    public Flux<String> suggestByAuthor(String prefix) {
        log.debug("Suggest by author prefix={}", prefix);
        return searchQuery
                .suggestByAuthor(prefix)
                .doOnError(
                        e ->
                                log.error(
                                        "Suggest by author failed prefix={} error={}",
                                        prefix,
                                        e.getMessage(),
                                        e));
    }
}
