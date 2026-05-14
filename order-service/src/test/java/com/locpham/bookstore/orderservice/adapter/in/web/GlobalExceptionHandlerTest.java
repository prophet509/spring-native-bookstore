package com.locpham.bookstore.orderservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;

import com.locpham.bookstore.orderservice.domain.exception.BookNotFoundException;
import com.locpham.bookstore.orderservice.domain.exception.IllegalOrderException;
import com.locpham.bookstore.orderservice.domain.exception.InsufficientStockException;
import com.locpham.bookstore.orderservice.domain.exception.OrderAlreadyProcessedException;
import com.locpham.bookstore.orderservice.domain.exception.OrderNotFoundException;
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
        MockServerHttpRequest request = MockServerHttpRequest.get("/").build();
        exchange = MockServerWebExchange.from(request);
    }

    @Test
    void handleBookNotFoundException() {
        var exception = new BookNotFoundException("1234567890");
        var response = handler.handleBookNotFoundException(exception, exchange);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.NOT_FOUND, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(HttpStatus.NOT_FOUND.value(), problemDetail.getStatus());
                            assertEquals(
                                    "Book with ISBN '1234567890' not found in catalog",
                                    problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/book-not-found"),
                                    problemDetail.getType());
                            assertEquals("Book Not Found", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }

    @Test
    void handleOrderNotFoundException() {
        var exception = new OrderNotFoundException(1L);
        var response = handler.handleOrderNotFoundException(exception, exchange);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.NOT_FOUND, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(HttpStatus.NOT_FOUND.value(), problemDetail.getStatus());
                            assertEquals(
                                    "The order with id 1 was not found.",
                                    problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/order-not-found"),
                                    problemDetail.getType());
                            assertEquals("Order Not Found", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }

    @Test
    void handleIllegalOrderException() {
        var exception = new IllegalOrderException("Quantity must be positive");
        var response = handler.handleIllegalOrderException(exception, exchange);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(
                                    HttpStatus.UNPROCESSABLE_ENTITY.value(),
                                    problemDetail.getStatus());
                            assertEquals("Quantity must be positive", problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/order-rejected"),
                                    problemDetail.getType());
                            assertEquals("Order Rejected", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }

    @Test
    void handleInsufficientStockException() {
        var exception = new InsufficientStockException("1234567890", 5, 2);
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
                                    "Insufficient stock for book ISBN '1234567890': requested 5, available 2",
                                    problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/insufficient-stock"),
                                    problemDetail.getType());
                            assertEquals("Insufficient Stock", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }

    @Test
    void handleOrderAlreadyProcessedException() {
        var exception = new OrderAlreadyProcessedException(1L);
        var response = handler.handleOrderAlreadyProcessedException(exception, exchange);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.CONFLICT, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(HttpStatus.CONFLICT.value(), problemDetail.getStatus());
                            assertEquals(
                                    "Order with ID '1' has already been processed",
                                    problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/order-state-conflict"),
                                    problemDetail.getType());
                            assertEquals("Order State Conflict", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
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
                            assert problemDetail.getProperties() != null;
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }
}
