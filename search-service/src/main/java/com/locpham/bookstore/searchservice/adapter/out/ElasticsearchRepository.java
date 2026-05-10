package com.locpham.bookstore.searchservice.adapter.out;

import com.locpham.bookstore.searchservice.adapter.out.persistence.elaticsearch.ElasticsearchBookDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ElasticsearchRepository
        extends ReactiveElasticsearchRepository<ElasticsearchBookDocument, String> {
    Flux<ElasticsearchBookDocument> searchByTitle(String title, Pageable pageable);

    Mono<Long> countByTitle(String title);

    Flux<ElasticsearchBookDocument> searchByAuthor(String author, Pageable pageable);

    Mono<Long> countByAuthor(String author);

    Flux<ElasticsearchBookDocument> searchByPublisher(String publisher, Pageable pageable);

    Mono<Long> countByPublisher(String publisher);

    Flux<ElasticsearchBookDocument> searchByIsbn(String isbn, Pageable pageable);

    Mono<Long> countByIsbn(String isbn);

    Flux<ElasticsearchBookDocument> findAllBy(Pageable pageable);

    @Query("{\"bool\": {\"should\": [{\"match_phrase_prefix\": {\"title\": \"?0\"}}]}}}")
    Flux<ElasticsearchBookDocument> findTitlesByTitleStartingWith(String prefix);

    @Query("{\"bool\": {\"should\": [{\"match_phrase_prefix\": {\"author\": \"?0\"}}]}}}")
    Flux<ElasticsearchBookDocument> findAuthorsByAuthorStartingWith(String prefix);
}
