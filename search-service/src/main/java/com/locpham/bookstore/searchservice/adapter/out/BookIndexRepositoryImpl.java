package com.locpham.bookstore.searchservice.adapter.out;

import com.locpham.bookstore.searchservice.adapter.out.persistence.elaticsearch.ElasticsearchBookDocument;
import com.locpham.bookstore.searchservice.application.out.persistence.BookIndexRepository;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
class ElasticsearchBookRepositoryAdapter implements BookIndexRepository {
    private final ElasticsearchRepository elasticsearchRepository;

    public ElasticsearchBookRepositoryAdapter(ElasticsearchRepository elasticsearchRepository) {
        this.elasticsearchRepository = elasticsearchRepository;
    }

    public Mono<BookDocument> save(BookDocument doc) {
        return elasticsearchRepository
                .save(ElasticsearchBookDocument.fromDomain(doc))
                .map(ElasticsearchBookDocument::toDomain);
    }

    public Mono<Void> deleteByIsbn(String isbn) {
        return elasticsearchRepository.deleteById(isbn);
    }

    public Mono<BookDocument> findByIsbn(String isbn) {
        return elasticsearchRepository.findById(isbn).map(ElasticsearchBookDocument::toDomain);
    }
}
