package com.locpham.bookstore.catalogservice.adapter.in.advice;

import com.locpham.bookstore.catalogservice.domain.book.exception.BookAlreadyExistsException;
import com.locpham.bookstore.catalogservice.domain.book.exception.BookNotFoundException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private void enrichProblemDetail(ProblemDetail problemDetail, WebRequest request) {
        problemDetail.setProperty("timestamp", Instant.now());
        String traceId = request.getHeader("X-B3-TraceId");
        if (traceId == null) {
            traceId = request.getHeader("traceparent");
        }
        if (traceId != null) {
            problemDetail.setProperty("traceId", traceId);
        }
    }

    @ExceptionHandler(BookNotFoundException.class)
    ResponseEntity<ProblemDetail> bookNotFoundHandler(
            BookNotFoundException ex, WebRequest request) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/book-not-found"));
        problemDetail.setTitle("Book Not Found");
        enrichProblemDetail(problemDetail, request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(BookAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> bookAlreadyHandler(
            BookAlreadyExistsException ex, WebRequest request) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/book-already-exists"));
        problemDetail.setTitle("Book Already Exists");
        enrichProblemDetail(problemDetail, request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult()
                .getAllErrors()
                .forEach(
                        error ->
                                errors.put(
                                        ((FieldError) error).getField(),
                                        error.getDefaultMessage()));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setType(URI.create("https://bookstore.api/errors/validation-failed"));
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty("errors", errors);
        enrichProblemDetail(problemDetail, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, WebRequest request) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request");
        problemDetail.setType(URI.create("https://bookstore.api/errors/malformed-request"));
        problemDetail.setTitle("Malformed Request");
        enrichProblemDetail(problemDetail, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleAllUncaughtException(Exception ex, WebRequest request) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setType(URI.create("https://bookstore.api/errors/internal-server-error"));
        problemDetail.setTitle("Internal Server Error");
        enrichProblemDetail(problemDetail, request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
