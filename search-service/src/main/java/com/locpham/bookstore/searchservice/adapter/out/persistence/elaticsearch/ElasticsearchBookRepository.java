package com.locpham.bookstore.searchservice.adapter.out.persistence.elaticsearch;

import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;

public interface ElasticsearchBookRepository extends ReactiveElasticsearchRepository<BookDocument, String> {
}
