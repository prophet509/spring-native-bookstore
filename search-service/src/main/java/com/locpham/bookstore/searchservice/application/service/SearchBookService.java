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
        log.atDebug()
                .addKeyValue("query", query)
                .addKeyValue("page", pageable.getPageNumber())
                .addKeyValue("size", pageable.getPageSize())
                .log("Search by title");
        return searchQuery
                .searchByTitle(query, pageable)
                .doOnError(
                        e ->
                                log.atError()
                                        .addKeyValue("query", query)
                                        .setCause(e)
                                        .log("Search by title failed"));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByAuthor(String author, Pageable pageable) {
        log.atDebug()
                .addKeyValue("author", author)
                .addKeyValue("page", pageable.getPageNumber())
                .addKeyValue("size", pageable.getPageSize())
                .log("Search by author");
        return searchQuery
                .searchByAuthor(author, pageable)
                .doOnError(
                        e ->
                                log.atError()
                                        .addKeyValue("author", author)
                                        .setCause(e)
                                        .log("Search by author failed"));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByPublisher(String publisher, Pageable pageable) {
        log.atDebug()
                .addKeyValue("publisher", publisher)
                .addKeyValue("page", pageable.getPageNumber())
                .addKeyValue("size", pageable.getPageSize())
                .log("Search by publisher");
        return searchQuery
                .searchByPublisher(publisher, pageable)
                .doOnError(
                        e ->
                                log.atError()
                                        .addKeyValue("publisher", publisher)
                                        .setCause(e)
                                        .log("Search by publisher failed"));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByIsbn(String isbn, Pageable pageable) {
        log.atDebug()
                .addKeyValue("isbn", isbn)
                .addKeyValue("page", pageable.getPageNumber())
                .addKeyValue("size", pageable.getPageSize())
                .log("Search by ISBN");
        return searchQuery
                .searchByIsbn(isbn, pageable)
                .doOnError(
                        e ->
                                log.atError()
                                        .addKeyValue("isbn", isbn)
                                        .setCause(e)
                                        .log("Search by ISBN failed"));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchAll(Pageable pageable) {
        log.atDebug()
                .addKeyValue("page", pageable.getPageNumber())
                .addKeyValue("size", pageable.getPageSize())
                .log("Search all");
        return searchQuery
                .searchAll(pageable)
                .doOnError(e -> log.atError().setCause(e).log("Search all failed"));
    }

    @Override
    public Flux<String> suggestByTitle(String prefix) {
        log.atDebug().addKeyValue("prefix", prefix).log("Suggest by title");
        return searchQuery
                .suggestByTitle(prefix)
                .doOnError(
                        e ->
                                log.atError()
                                        .addKeyValue("prefix", prefix)
                                        .setCause(e)
                                        .log("Suggest by title failed"));
    }

    @Override
    public Flux<String> suggestByAuthor(String prefix) {
        log.atDebug().addKeyValue("prefix", prefix).log("Suggest by author");
        return searchQuery
                .suggestByAuthor(prefix)
                .doOnError(
                        e ->
                                log.atError()
                                        .addKeyValue("prefix", prefix)
                                        .setCause(e)
                                        .log("Suggest by author failed"));
    }
}
