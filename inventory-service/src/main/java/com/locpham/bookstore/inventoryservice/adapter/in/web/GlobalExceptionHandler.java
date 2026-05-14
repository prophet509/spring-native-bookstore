package com.locpham.bookstore.inventoryservice.adapter.in.web;

import com.locpham.bookstore.inventoryservice.domain.InsufficientStockException;
import com.locpham.bookstore.inventoryservice.domain.InventoryException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {
    private void enrichProblemDetail(
            ProblemDetail problemDetail,
            org.springframework.web.server.ServerWebExchange exchange) {
        problemDetail.setProperty("timestamp", Instant.now());
        String traceId = exchange.getRequest().getHeaders().getFirst("X-B3-TraceId");
        if (traceId == null) {
            traceId = exchange.getRequest().getHeaders().getFirst("traceparent");
        }
        if (traceId != null) {
            problemDetail.setProperty("traceId", traceId);
        }
    }

    @ExceptionHandler(InsufficientStockException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleInsufficientStockException(
            InsufficientStockException ex,
            org.springframework.web.server.ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/insufficient-stock"));
        problemDetail.setTitle("Insufficient Stock");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail));
    }

    @ExceptionHandler(InventoryException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleInventoryException(
            InventoryException ex, org.springframework.web.server.ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/inventory-error"));
        problemDetail.setTitle("Inventory Error");
        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleValidationException(
            WebExchangeBindException ex,
            org.springframework.web.server.ServerWebExchange exchange) {
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
            ConstraintViolationException ex,
            org.springframework.web.server.ServerWebExchange exchange) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setType(URI.create("https://bookstore.api/errors/validation-failed"));
        problemDetail.setTitle("Validation Failed");

        java.util.Map<String, String> errors = new java.util.HashMap<>();
        ex.getConstraintViolations()
                .forEach(
                        violation -> {
                            String path = violation.getPropertyPath().toString();
                            errors.put(path, violation.getMessage());
                        });
        problemDetail.setProperty("errors", errors);

        enrichProblemDetail(problemDetail, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ProblemDetail>> handleGenericException(
            Exception ex, org.springframework.web.server.ServerWebExchange exchange) {
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
