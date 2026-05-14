package com.locpham.bookstore.inventoryservice.adapter.in.web;

import com.locpham.bookstore.inventoryservice.domain.InsufficientStockException;
import com.locpham.bookstore.inventoryservice.domain.InventoryException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleInsufficientStockException(
            InsufficientStockException ex) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/insufficient-stock"));
        problemDetail.setTitle("Insufficient Stock");
        problemDetail.setProperty("timestamp", Instant.now());
        return Mono.just(ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail));
    }

    @ExceptionHandler(InventoryException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleInventoryException(InventoryException ex) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/inventory-error"));
        problemDetail.setTitle("Inventory Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleValidationException(WebExchangeBindException ex) {
        String errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "));
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        problemDetail.setType(URI.create("https://bookstore.api/errors/validation-failure"));
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty("timestamp", Instant.now());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ProblemDetail>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        String errors =
                ex.getConstraintViolations().stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.joining(", "));
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors);
        problemDetail.setType(URI.create("https://bookstore.api/errors/constraint-violation"));
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setProperty("timestamp", Instant.now());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ProblemDetail>> handleGenericException(Exception ex) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setType(URI.create("https://bookstore.api/errors/internal-error"));
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail));
    }
}
