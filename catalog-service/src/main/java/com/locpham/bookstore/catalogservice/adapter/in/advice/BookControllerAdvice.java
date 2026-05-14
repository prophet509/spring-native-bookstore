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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BookControllerAdvice {

    @ExceptionHandler(BookNotFoundException.class)
    ResponseEntity<ProblemDetail> bookNotFoundHandler(BookNotFoundException ex) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/book-not-found"));
        problemDetail.setTitle("Book Not Found");
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(BookAlreadyExistsException.class)
    ResponseEntity<ProblemDetail> bookAlreadyHandler(BookAlreadyExistsException ex) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setType(URI.create("https://bookstore.api/errors/book-already-exists"));
        problemDetail.setTitle("Book Already Exists");
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex) {
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
        problemDetail.setType(URI.create("https://bookstore.api/errors/validation-failure"));
        problemDetail.setTitle("Validation Failed");
        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }
}
