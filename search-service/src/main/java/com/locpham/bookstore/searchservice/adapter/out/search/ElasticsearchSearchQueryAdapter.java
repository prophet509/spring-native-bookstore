package com.locpham.bookstore.searchservice.adapter.out.search;

import com.locpham.bookstore.searchservice.adapter.out.ElasticsearchRepository;
import com.locpham.bookstore.searchservice.adapter.out.persistence.elaticsearch.ElasticsearchBookDocument;
import com.locpham.bookstore.searchservice.application.out.search.SearchQuery;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class ElasticsearchSearchQueryAdapter implements SearchQuery {

    private final ElasticsearchRepository elasticsearchRepository;

    public ElasticsearchSearchQueryAdapter(ElasticsearchRepository elasticsearchRepository) {
        this.elasticsearchRepository = elasticsearchRepository;
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByTitle(String title, Pageable pageable) {
        Mono<List<BookDocument>> dataMono =
                elasticsearchRepository
                        .searchByTitle(title, pageable)
                        .map(ElasticsearchBookDocument::toDomain)
                        .collectList();
        Mono<Long> countMono = elasticsearchRepository.countByTitle(title);

        return Mono.zip(dataMono, countMono)
                .map(
                        tuple ->
                                SearchPage.of(
                                        tuple.getT1(),
                                        pageable.getPageNumber(),
                                        pageable.getPageSize(),
                                        tuple.getT2()));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByAuthor(String author, Pageable pageable) {
        Mono<List<BookDocument>> dataMono =
                elasticsearchRepository
                        .searchByAuthor(author, pageable)
                        .map(ElasticsearchBookDocument::toDomain)
                        .collectList();
        Mono<Long> countMono = elasticsearchRepository.countByAuthor(author);

        return Mono.zip(dataMono, countMono)
                .map(
                        tuple ->
                                SearchPage.of(
                                        tuple.getT1(),
                                        pageable.getPageNumber(),
                                        pageable.getPageSize(),
                                        tuple.getT2()));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByPublisher(String publisher, Pageable pageable) {
        Mono<List<BookDocument>> dataMono =
                elasticsearchRepository
                        .searchByPublisher(publisher, pageable)
                        .map(ElasticsearchBookDocument::toDomain)
                        .collectList();
        Mono<Long> countMono = elasticsearchRepository.countByPublisher(publisher);

        return Mono.zip(dataMono, countMono)
                .map(
                        tuple ->
                                SearchPage.of(
                                        tuple.getT1(),
                                        pageable.getPageNumber(),
                                        pageable.getPageSize(),
                                        tuple.getT2()));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByIsbn(String isbn, Pageable pageable) {
        Mono<List<BookDocument>> dataMono =
                elasticsearchRepository
                        .searchByIsbn(isbn, pageable)
                        .map(ElasticsearchBookDocument::toDomain)
                        .collectList();
        Mono<Long> countMono = elasticsearchRepository.countByIsbn(isbn);

        return Mono.zip(dataMono, countMono)
                .map(
                        tuple ->
                                SearchPage.of(
                                        tuple.getT1(),
                                        pageable.getPageNumber(),
                                        pageable.getPageSize(),
                                        tuple.getT2()));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchAll(Pageable pageable) {
        Mono<List<BookDocument>> dataMono =
                elasticsearchRepository
                        .findAll(pageable)
                        .map(ElasticsearchBookDocument::toDomain)
                        .collectList();
        Mono<Long> countMono = elasticsearchRepository.count();

        return Mono.zip(dataMono, countMono)
                .map(
                        tuple ->
                                SearchPage.of(
                                        tuple.getT1(),
                                        pageable.getPageNumber(),
                                        pageable.getPageSize(),
                                        tuple.getT2()));
    }

    @Override
    public Flux<String> suggestByTitle(String prefix) {
        return elasticsearchRepository.findTitlesByTitleStartingWith(prefix);
    }

    @Override
    public Flux<String> suggestByAuthor(String prefix) {
        return elasticsearchRepository.findAuthorsByAuthorStartingWith(prefix);
    }
}
