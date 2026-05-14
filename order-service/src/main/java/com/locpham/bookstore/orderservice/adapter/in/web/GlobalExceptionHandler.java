package com.locpham.bookstore.orderservice.adapter.in.web;

import com.locpham.bookstore.orderservice.domain.exception.BookNotFoundException;
import com.locpham.bookstore.orderservice.domain.exception.IllegalOrderException;
import com.locpham.bookstore.orderservice.domain.exception.InsufficientStockException;
import com.locpham.bookstore.orderservice.domain.exception.OrderAlreadyProcessedException;
import com.locpham.bookstore.orderservice.domain.exception.OrderNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {

    private void enrichProblemDetail(ProblemDetail problemDetail, ServerWebExchange exchange) {
        problemDetail.setProperty("timestamp", Instant.now());
        String traceId = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");
        if (traceId == null) {
            traceId = exchange.getRequest().getHeaders().getFirst("traceparent");
        }
        if (traceId != null) {
            problemDetail.setProperty("traceId", traceId);
        }
    }

    @ExceptionHandler(BookNotFoundException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleBookNotFoundException(
            BookNotFoundException ex, ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/book-not-found"));
        problemDetail.setTitle("Book Not Found");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleOrderNotFoundException(
            OrderNotFoundException ex, ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/order-not-found"));
        problemDetail.setTitle("Order Not Found");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail));
    }

    @ExceptionHandler(IllegalOrderException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleIllegalOrderException(
            IllegalOrderException ex, ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/order-rejected"));
        problemDetail.setTitle("Order Rejected");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleValidationException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        java.util.Map<String, String> errors = new java.util.HashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setType(URI.create("https://bookstore.api/errors/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty("errors", errors);
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleConstraintViolationException(
            ConstraintViolationException ex, ServerWebExchange exchange) {
        java.util.Map<String, String> errors = new java.util.HashMap<>();
        ex.getConstraintViolations()
                .forEach(
                        violation -> {
                            String path = violation.getPropertyPath().toString();
                            errors.put(path, violation.getMessage());
                        });

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setType(URI.create("https://bookstore.api/errors/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty("errors", errors);
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleInsufficientStockException(
            InsufficientStockException ex, ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/insufficient-stock"));
        problemDetail.setTitle("Insufficient Stock");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail));
    }

    @ExceptionHandler(OrderAlreadyProcessedException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleOrderAlreadyProcessedException(
            OrderAlreadyProcessedException ex, ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/order-state-conflict"));
        problemDetail.setTitle("Order State Conflict");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ProblemDetail>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setType(URI.create("https://bookstore.api/errors/internal-server-error"));
        problemDetail.setTitle("Internal Server Error");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail));
    }
}
