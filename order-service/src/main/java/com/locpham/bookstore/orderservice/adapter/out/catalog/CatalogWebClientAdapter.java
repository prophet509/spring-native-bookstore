package com.locpham.bookstore.orderservice.adapter.out.catalog;

import com.locpham.bookstore.orderservice.application.port.out.CatalogBookPort;
import com.locpham.bookstore.orderservice.domain.exception.BookNotFoundException;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class CatalogWebClientAdapter implements CatalogBookPort {
    private final WebClient webClient;

    public CatalogWebClientAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${polar.catalog-service-url}") String catalogServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(catalogServiceUrl).build();
    }

    @Override
    public Mono<BookSnapshot> loadBook(String isbn) {
        return webClient
                .get()
                .uri("/books/{isbn}", isbn)
                .retrieve()
                .bodyToMono(BookDto.class)
                .map(this::toBookSnapshot)
                .onErrorResume(e -> Mono.error(new BookNotFoundException(isbn)));
    }

    private BookSnapshot toBookSnapshot(BookDto dto) {
        return new BookSnapshot(dto.isbn(), dto.title(), dto.price());
    }
}
