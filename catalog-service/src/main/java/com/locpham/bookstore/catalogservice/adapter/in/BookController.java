package com.locpham.bookstore.catalogservice.adapter.in;

import com.locpham.bookstore.catalogservice.adapter.in.dto.BookRequest;
import com.locpham.bookstore.catalogservice.adapter.in.dto.BookResponse;
import com.locpham.bookstore.catalogservice.application.port.in.AddBookUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.EditBookUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.ViewBookDetailUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.ViewListBookUseCase;
import jakarta.validation.Valid;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books")
public class BookController {
    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    private final ViewBookDetailUseCase viewBookDetailUseCase;
    private final ViewListBookUseCase viewListBookUseCase;
    private final AddBookUseCase addBookUseCase;
    private final EditBookUseCase editBookUseCase;

    public BookController(
            ViewBookDetailUseCase viewBookDetailUseCase,
            ViewListBookUseCase viewListBookUseCase,
            AddBookUseCase addBookUseCase,
            EditBookUseCase editBookUseCase) {
        this.viewBookDetailUseCase = viewBookDetailUseCase;
        this.viewListBookUseCase = viewListBookUseCase;
        this.addBookUseCase = addBookUseCase;
        this.editBookUseCase = editBookUseCase;
    }

    @GetMapping
    public Iterable<BookResponse> get() {
        log.info("Fetching the list of books in the catalog");
        var books = viewListBookUseCase.viewBookList();
        return StreamSupport.stream(books.spliterator(), false)
                .map(BookResponse::fromDomain)
                .toList();
    }

    @GetMapping("{isbn}")
    public BookResponse getByIsbn(@PathVariable String isbn) {
        log.atInfo().addKeyValue("isbn", isbn).log("Fetching book details");
        var book = viewBookDetailUseCase.viewBookDetail(isbn);
        return BookResponse.fromDomain(book);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('employee')")
    public BookResponse post(@Valid @RequestBody BookRequest request) {
        log.atInfo().addKeyValue("isbn", request.isbn()).log("Adding book to catalog: {}", request);
        var book = addBookUseCase.addBookToCatalog(request.toDomain());
        return BookResponse.fromDomain(book);
    }

    @DeleteMapping("{isbn}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('employee')")
    public void delete(@PathVariable String isbn) {
        log.atInfo().addKeyValue("isbn", isbn).log("Deleting book from catalog");
        editBookUseCase.deleteBook(isbn);
    }

    @PutMapping("{isbn}")
    @PreAuthorize("hasRole('employee')")
    public BookResponse put(@PathVariable String isbn, @Valid @RequestBody BookRequest request) {
        log.atInfo().addKeyValue("isbn", isbn).log("Updating book details: {}", request);
        var book = editBookUseCase.editBookDetails(request.toDomain());
        return BookResponse.fromDomain(book);
    }
}
