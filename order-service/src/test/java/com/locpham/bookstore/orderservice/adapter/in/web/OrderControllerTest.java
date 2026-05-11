package com.locpham.bookstore.orderservice.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.locpham.bookstore.orderservice.TestcontainersConfiguration;
import com.locpham.bookstore.orderservice.adapter.in.web.dto.OrderRequest;
import com.locpham.bookstore.orderservice.application.port.in.GetOrdersUseCase;
import com.locpham.bookstore.orderservice.application.port.in.SubmitOrderUseCase;
import com.locpham.bookstore.orderservice.bootstrap.config.SecurityConfig;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({SecurityConfig.class, TestcontainersConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class OrderControllerTest {

    @Autowired private ApplicationContext context;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient =
                WebTestClient.bindToApplicationContext(context)
                        .apply(springSecurity())
                        .configureClient()
                        .build();
    }

    @MockitoBean private SubmitOrderUseCase submitOrderUseCase;
    @MockitoBean private GetOrdersUseCase getOrdersUseCase;
    @MockitoBean private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    void testGetAllOrders() {
        Order mockOrder = Order.createAccepted("1234567890", "Test Book", 9.99, 1);
        given(getOrdersUseCase.getOrders(any())).willReturn(Flux.just(mockOrder));

        webTestClient
                .mutateWith(mockJwt().jwt(builder -> builder.subject("user-test")))
                .get()
                .uri("/orders")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].book.isbn")
                .isEqualTo("1234567890");
    }

    @Test
    void testSubmitOrder() {
        Order mockOrder = Order.createAccepted("0987654321", "Test Book 2", 15.00, 2);
        given(submitOrderUseCase.submitOrder(any())).willReturn(Mono.just(mockOrder));

        OrderRequest orderRequest = new OrderRequest("0987654321", 2);

        webTestClient
                .mutateWith(
                        mockJwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_customer"))
                                .jwt(builder -> builder.subject("user-test")))
                .post()
                .uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.book.isbn")
                .isEqualTo("0987654321");
    }

    @Test
    void testSubmitOrderWhenNotAuthenticatedThenReturnUnauthorized() {
        OrderRequest orderRequest = new OrderRequest("0987654321", 2);

        webTestClient
                .post()
                .uri("/orders")
                .bodyValue(orderRequest)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void testSubmitOrderWhenInvalidRequestThenReturnBadRequest() {
        OrderRequest invalidOrderRequest = new OrderRequest("", 0); // Invalid ISBN and quantity

        webTestClient
                .mutateWith(
                        mockJwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_customer"))
                                .jwt(builder -> builder.subject("user-test")))
                .post()
                .uri("/orders")
                .bodyValue(invalidOrderRequest)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}
