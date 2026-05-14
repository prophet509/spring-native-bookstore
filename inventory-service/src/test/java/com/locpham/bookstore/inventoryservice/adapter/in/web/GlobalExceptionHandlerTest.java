package com.locpham.bookstore.inventoryservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;

import com.locpham.bookstore.inventoryservice.domain.InsufficientStockException;
import com.locpham.bookstore.inventoryservice.domain.InventoryException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private MockServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/")
                                .header("X-B3-TraceId", "test-trace-id")
                                .build());
    }

    @Test
    void handleInsufficientStockException() {
        var exception =
                new InsufficientStockException("Insufficient stock for book ISBN '1234567890'");
        var response = handler.handleInsufficientStockException(exception, exchange);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(
                                    HttpStatus.UNPROCESSABLE_ENTITY.value(),
                                    problemDetail.getStatus());
                            assertEquals(
                                    "Insufficient stock for book ISBN '1234567890'",
                                    problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/insufficient-stock"),
                                    problemDetail.getType());
                            assertEquals("Insufficient Stock", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                            assertEquals(
                                    "test-trace-id", problemDetail.getProperties().get("traceId"));
                        })
                .verifyComplete();
    }

    @Test
    void handleInventoryException() {
        var exception = new InventoryException("Inventory error occurred");
        var response = handler.handleInventoryException(exception, exchange);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(HttpStatus.BAD_REQUEST.value(), problemDetail.getStatus());
                            assertEquals("Inventory error occurred", problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/inventory-error"),
                                    problemDetail.getType());
                            assertEquals("Inventory Error", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                            assertEquals(
                                    "test-trace-id", problemDetail.getProperties().get("traceId"));
                        })
                .verifyComplete();
    }

    @Test
    void handleGenericException() {
        var exception = new RuntimeException("Unexpected error");
        var response = handler.handleGenericException(exception, exchange);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(
                                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                    problemDetail.getStatus());
                            assertEquals("An unexpected error occurred", problemDetail.getDetail());
                            assertEquals(
                                    URI.create(
                                            "https://bookstore.api/errors/internal-server-error"),
                                    problemDetail.getType());
                            assertEquals("Internal Server Error", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                            assertEquals(
                                    "test-trace-id", problemDetail.getProperties().get("traceId"));
                        })
                .verifyComplete();
    }
}
