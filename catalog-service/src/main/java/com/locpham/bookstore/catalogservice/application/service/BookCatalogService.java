package com.locpham.bookstore.catalogservice.application.service;

import com.locpham.bookstore.catalogservice.application.port.in.AddBookUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.EditBookUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.ViewBookDetailUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.ViewListBookUseCase;
import com.locpham.bookstore.catalogservice.application.port.out.BookEventPublisher;
import com.locpham.bookstore.catalogservice.application.port.out.BookRepository;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import com.locpham.bookstore.catalogservice.domain.book.exception.BookAlreadyExistsException;
import com.locpham.bookstore.catalogservice.domain.book.exception.BookNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BookCatalogService
        implements ViewListBookUseCase, ViewBookDetailUseCase, AddBookUseCase, EditBookUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookCatalogService.class);

    private final BookRepository bookRepository;
    private final BookEventPublisher publisher;

    public BookCatalogService(BookRepository bookRepository, BookEventPublisher publisher) {
        this.bookRepository = bookRepository;
        this.publisher = publisher;
    }

    @Override
    public Book editBookDetails(Book book) {
        var isbn = book.isbn();
        log.debug("Editing book details isbn={}", isbn);

        if (!bookRepository.existsByIsbn(isbn)) {
            log.warn("Book not found for edit isbn={}", isbn);
            throw new BookNotFoundException(isbn);
        }

        var existing = bookRepository.findByIsbn(isbn).orElseThrow();
        var updated = existing.updateWith(book);

        var saved = bookRepository.save(updated);
        publisher.publishBookUpdated(saved).block();
        log.info("Book updated isbn={}", isbn);
        return saved;
    }

    @Override
    public void deleteBook(String isbn) {
        log.debug("Deleting book isbn={}", isbn);

        if (!bookRepository.existsByIsbn(isbn)) {
            log.warn("Book not found for deletion isbn={}", isbn);
            throw new BookNotFoundException(isbn);
        }

        bookRepository.deleteByIsbn(isbn);
        publisher.publishBookDeleted(isbn).block();
        log.info("Book deleted isbn={}", isbn);
    }

    @Override
    public Book viewBookDetail(String isbn) throws BookNotFoundException {
        log.debug("Viewing book detail isbn={}", isbn);
        return bookRepository
                .findByIsbn(isbn)
                .orElseThrow(
                        () -> {
                            log.warn("Book not found isbn={}", isbn);
                            return new BookNotFoundException(isbn);
                        });
    }

    @Override
    public Iterable<Book> viewBookList() {
        log.debug("Viewing book list");
        return bookRepository.findAll();
    }

    @Override
    public Book addBookToCatalog(Book book) {
        var isbn = book.isbn();
        log.debug("Adding book to catalog isbn={}", isbn);

        if (bookRepository.existsByIsbn(isbn)) {
            log.warn("Book already exists isbn={}", isbn);
            throw new BookAlreadyExistsException(isbn);
        }

        var saved = bookRepository.save(book);
        publisher.publishBookCreated(saved).block();
        log.info("Book added isbn={} title={}", isbn, book.title());
        return saved;
    }
}
