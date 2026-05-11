package com.locpham.bookstore.inventoryservice.adapter.in.web;

import static org.mockito.BDDMockito.given;

import com.locpham.bookstore.inventoryservice.application.port.in.ManageStockUseCase;
import com.locpham.bookstore.inventoryservice.bootstrap.config.SecurityConfig;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(
        value = InventoryController.class,
        properties = {
            "spring.cloud.config.enabled=false",
            "spring.cloud.config.fail-fast=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test"
        })
@Import(SecurityConfig.class)
class InventoryControllerTest {

    @Autowired private WebTestClient webTestClient;

    @MockitoBean private ManageStockUseCase manageStockUseCase;

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    @Test
    void getStock_shouldReturnItem() {
        var item = new InventoryItem(1L, "123", 10, 0, 0);
        given(manageStockUseCase.queryStock("123")).willReturn(Mono.just(item));

        webTestClient
                .get()
                .uri("/inventory/123")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.isbn")
                .isEqualTo("123")
                .jsonPath("$.availableQuantity")
                .isEqualTo(10);
    }

    @Test
    void adjustStock_withoutJwtEvenWithInvalidBody_thenUnauthorized() {
        webTestClient
                .post()
                .uri("/inventory/123/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void adjustStock_withoutJwt_thenUnauthorized() {
        webTestClient
                .post()
                .uri("/inventory/123/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"delta\":1}")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
