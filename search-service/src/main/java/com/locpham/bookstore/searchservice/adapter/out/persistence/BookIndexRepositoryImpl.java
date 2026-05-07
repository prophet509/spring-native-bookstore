package com.locpham.bookstore.searchservice.adapter.out.persistence;

import com.locpham.bookstore.searchservice.adapter.out.persistence.elaticsearch.ElasticsearchBookRepository;
import com.locpham.bookstore.searchservice.application.out.persistence.BookIndexRepository;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class BookIndexRepositoryImpl implements BookIndexRepository {
    private  final ElasticsearchBookRepository elasticsearchBookRepository;

    public BookIndexRepositoryImpl(ElasticsearchBookRepository elasticsearchBookRepository) {
        this.elasticsearchBookRepository = elasticsearchBookRepository;
    }

    @Override
    public Mono<BookDocument> save(BookDocument doc) {
        return elasticsearchBookRepository.save(doc);
    }

    @Override
    public Mono<Void> deleteByIsbn(String isbn) {
        return elasticsearchBookRepository.deleteById(isbn);
    }

    @Override
    public Mono<BookDocument> findByIsbn(String isbn) {
        return elasticsearchBookRepository.findById(isbn);
    }
}
