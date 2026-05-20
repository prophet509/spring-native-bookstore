package com.locpham.bookstore.searchservice.adapter.in.rest;

import com.locpham.bookstore.searchservice.application.in.SearchBookUseCase;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchBookUseCase searchBookUseCase;

    public SearchController(SearchBookUseCase searchBookUseCase) {
        this.searchBookUseCase = searchBookUseCase;
    }

    @GetMapping
    public Mono<Page<SearchResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false) String isbn,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        log.debug(
                "Search request q={} author={} publisher={} isbn={} page={} size={} sort={}",
                q,
                author,
                publisher,
                isbn,
                page,
                size,
                sort);

        Sort sortObj =
                sort != null
                        ? Sort.by(Sort.Direction.fromString(sort.split(",")[1]), sort.split(",")[0])
                        : Sort.unsorted();
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Mono<SearchPage<BookDocument>> result;

        if (q != null) {
            result = searchBookUseCase.search(q, pageable);
        } else if (author != null) {
            result = searchBookUseCase.searchByAuthor(author, pageable);
        } else if (publisher != null) {
            result = searchBookUseCase.searchByPublisher(publisher, pageable);
        } else if (isbn != null) {
            result = searchBookUseCase.searchByIsbn(isbn, pageable);
        } else {
            result = searchBookUseCase.searchAll(pageable);
        }

        return result.doOnSuccess(
                        searchPage ->
                                log.info(
                                        "Search completed totalElements={} page={} size={}",
                                        searchPage.totalElements(),
                                        searchPage.pageNumber(),
                                        searchPage.pageSize()))
                .map(
                        searchPage ->
                                new PageImpl<>(
                                        searchPage.content().stream()
                                                .map(SearchResponse::from)
                                                .toList(),
                                        PageRequest.of(
                                                searchPage.pageNumber(), searchPage.pageSize()),
                                        searchPage.totalElements()));
    }

    @GetMapping("/suggest")
    public Flux<String> suggest(@RequestParam String q) {
        log.debug("Suggest request q={}", q);
        return searchBookUseCase.suggestByTitle(q);
    }
}
