package com.locpham.bookstore.searchservice.application.service;

import com.locpham.bookstore.searchservice.application.in.SearchBookUseCase;
import com.locpham.bookstore.searchservice.application.out.persistence.BookIndexRepository;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.awt.print.Pageable;

public class SearchBookService implements SearchBookUseCase {


    public SearchBookService() {
    }

    @Override
    public Mono<Page<BookDocument>> search(String query, Pageable pageable) {
        return null;
    }

    @Override
    public Mono<Page<BookDocument>> searchByAuthor(String author, Pageable pageable) {
        return null;
    }

    @Override
    public Flux<String> suggest(String prefix) {
        return null;
    }
}
