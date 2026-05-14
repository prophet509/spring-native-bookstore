package com.locpham.bookstore.orderservice.adapter.in.web;

import static org.junit.jupiter.api.Assertions.*;

import com.locpham.bookstore.orderservice.domain.exception.BookNotFoundException;
import com.locpham.bookstore.orderservice.domain.exception.IllegalOrderException;
import com.locpham.bookstore.orderservice.domain.exception.InsufficientStockException;
import com.locpham.bookstore.orderservice.domain.exception.OrderAlreadyProcessedException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import reactor.test.StepVerifier;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBookNotFoundException() {
        var exception = new BookNotFoundException("1234567890");
        var response = handler.handleBookNotFoundException(exception);

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
    void handleIllegalOrderException() {
        var exception = new IllegalOrderException("Quantity must be positive");
        var response = handler.handleIllegalOrderException(exception);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(HttpStatus.BAD_REQUEST.value(), problemDetail.getStatus());
                            assertEquals("Quantity must be positive", problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/illegal-order"),
                                    problemDetail.getType());
                            assertEquals("Illegal Order", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }

    @Test
    void handleInsufficientStockException() {
        var exception = new InsufficientStockException("1234567890", 5, 2);
        var response = handler.handleInsufficientStockException(exception);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), problemDetail.getStatus());
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
        var response = handler.handleOrderAlreadyProcessedException(exception);

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
                                    URI.create("https://bookstore.api/errors/order-already-processed"),
                                    problemDetail.getType());
                            assertEquals("Order Already Processed", problemDetail.getTitle());
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }

    @Test
    void handleGenericException() {
        var exception = new RuntimeException("Unexpected error");
        var response = handler.handleGenericException(exception);

        StepVerifier.create(response)
                .assertNext(
                        entity -> {
                            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
                            ProblemDetail problemDetail = entity.getBody();
                            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemDetail.getStatus());
                            assertEquals("An unexpected error occurred", problemDetail.getDetail());
                            assertEquals(
                                    URI.create("https://bookstore.api/errors/internal-error"),
                                    problemDetail.getType());
                            assertEquals("Internal Server Error", problemDetail.getTitle());
                            assert problemDetail.getProperties() != null;
                            assertTrue(problemDetail.getProperties().containsKey("timestamp"));
                        })
                .verifyComplete();
    }
}
