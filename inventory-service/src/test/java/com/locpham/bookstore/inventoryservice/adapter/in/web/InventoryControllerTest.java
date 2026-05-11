package com.locpham.bookstore.inventoryservice.adapter.in.web;

import static org.mockito.BDDMockito.given;

import com.locpham.bookstore.inventoryservice.application.port.in.ManageStockUseCase;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=false",
            "spring.r2dbc.enabled=false",
            "spring.cloud.config.enabled=false",
            "spring.cloud.stream.enabled=false"
        })
class InventoryControllerTest {

    @Autowired private ApplicationContext context;

    private WebTestClient webTestClient;

    @MockitoBean private ManageStockUseCase manageStockUseCase;

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        this.webTestClient =
                WebTestClient.bindToApplicationContext(context).configureClient().build();
    }

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
    void adjustStock_whenMissingDelta_thenBadRequest() {
        webTestClient
                .post()
                .uri("/inventory/123/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
