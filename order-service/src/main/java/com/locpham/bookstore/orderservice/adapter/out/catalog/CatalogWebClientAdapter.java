package com.locpham.bookstore.orderservice.adapter.out.catalog;

import com.locpham.bookstore.orderservice.application.port.out.CatalogBookPort;
import com.locpham.bookstore.orderservice.domain.exception.BookNotFoundException;
import com.locpham.bookstore.orderservice.domain.exception.CatalogUnavailableException;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class CatalogWebClientAdapter implements CatalogBookPort {

    private static final Logger log = LoggerFactory.getLogger(CatalogWebClientAdapter.class);

    private final WebClient webClient;
    private final String catalogServiceUrl;

    public CatalogWebClientAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${polar.catalog-service-url}") String catalogServiceUrl) {
        this.catalogServiceUrl = catalogServiceUrl;
        this.webClient = webClientBuilder.baseUrl(catalogServiceUrl).build();
    }

    @Override
    public Mono<BookSnapshot> loadBook(String isbn) {
        Instant start = Instant.now();
        return webClient
                .get()
                .uri("/books/{isbn}", isbn)
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response -> Mono.error(new BookNotFoundException(isbn)))
                .bodyToMono(BookDto.class)
                .map(this::toBookSnapshot)
                .onErrorResume(
                        e -> {
                            long elapsed = Duration.between(start, Instant.now()).toMillis();
                            if (e instanceof BookNotFoundException) {
                                log.warn(
                                        "Book not found in catalog isbn={} catalogServiceUrl={} elapsedMs={}",
                                        isbn,
                                        catalogServiceUrl,
                                        elapsed);
                                return Mono.error(e);
                            }
                            log.error(
                                    "Catalog service error isbn={} catalogServiceUrl={} errorClass={} message={} elapsedMs={}",
                                    isbn,
                                    catalogServiceUrl,
                                    e.getClass().getSimpleName(),
                                    e.getMessage(),
                                    elapsed);
                            return Mono.error(new CatalogUnavailableException(isbn, e));
                        });
    }

    private BookSnapshot toBookSnapshot(BookDto dto) {
        return new BookSnapshot(dto.isbn(), dto.title(), dto.price());
    }
}
