package com.locpham.bookstore.searchservice.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.locpham.bookstore.searchservice.application.in.SearchBookUseCase;
import com.locpham.bookstore.searchservice.config.SecurityConfig;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(
        value = SearchController.class,
        properties = {
            "spring.cloud.config.enabled=false",
            "spring.cloud.config.fail-fast=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test"
        })
@Import(SecurityConfig.class)
class SearchControllerTests {

    @Autowired private WebTestClient webClient;

    @MockitoBean private SearchBookUseCase searchBookUseCase;

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    private final BookDocument book =
            new BookDocument("1234567890", "Spring Boot", "Author", 9.9, "Pub");

    @Test
    void searchByQueryReturnsPage() {
        var page = SearchPage.of(List.of(book), 0, 10, 1L);
        given(searchBookUseCase.search(eq("spring"), any(Pageable.class)))
                .willReturn(Mono.just(page));

        webClient
                .get()
                .uri("/search?q=spring")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[0].isbn")
                .isEqualTo("1234567890")
                .jsonPath("$.totalElements")
                .isEqualTo(1);
    }

    @Test
    void searchAllWhenNoFiltersGiven() {
        var page = SearchPage.of(List.of(book), 0, 10, 1L);
        given(searchBookUseCase.searchAll(any(Pageable.class))).willReturn(Mono.just(page));

        webClient.get().uri("/search").exchange().expectStatus().isOk();
    }

    @Test
    void suggestReturnsTitles() {
        given(searchBookUseCase.suggestByTitle("spr")).willReturn(Flux.just("Spring Boot"));

        webClient
                .get()
                .uri("/search/suggest?q=spr")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(String.class)
                .contains("Spring Boot");
    }

    @Test
    void postSearchWithoutJwtReturnsUnauthorized() {
        webClient.post().uri("/search").exchange().expectStatus().isUnauthorized();
    }
}
