package com.locpham.bookstore.searchservice.adapter.out;

import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

import com.locpham.bookstore.searchservice.domain.BookDocument;

public interface ElasticsearchRepository extends ReactiveElasticsearchRepository<BookDocument, String> {
}
