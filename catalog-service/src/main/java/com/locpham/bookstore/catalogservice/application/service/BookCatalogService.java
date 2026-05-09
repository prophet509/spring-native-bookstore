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
import org.springframework.stereotype.Service;

@Service
public class BookCatalogService
        implements ViewListBookUseCase, ViewBookDetailUseCase, AddBookUseCase, EditBookUseCase {
    private final BookRepository bookRepository;
    private final BookEventPublisher publisher;

    public BookCatalogService(BookRepository bookRepository, BookEventPublisher publisher) {
        this.bookRepository = bookRepository;
        this.publisher = publisher;
    }

    @Override
    public Book editBookDetails(Book book) {
        var isbn = book.isbn();

        if (!bookRepository.existsByIsbn(isbn)) {
            throw new BookNotFoundException(isbn);
        }

        var existing = bookRepository.findByIsbn(isbn).orElseThrow();
        var updated = existing.updateWith(book);

        var saved = bookRepository.save(updated);
        publisher.publishBookUpdated(saved).block();
        return saved;
    }

    @Override
    public void deleteBook(String isbn) {
        if (!bookRepository.existsByIsbn(isbn)) {
            throw new BookNotFoundException(isbn);
        }

        bookRepository.deleteByIsbn(isbn);
        publisher.publishBookDeleted(isbn).block();
    }

    @Override
    public Book viewBookDetail(String isbn) throws BookNotFoundException {
        return bookRepository.findByIsbn(isbn).orElseThrow(() -> new BookNotFoundException(isbn));
    }

    @Override
    public Iterable<Book> viewBookList() {
        return bookRepository.findAll();
    }

    @Override
    public Book addBookToCatalog(Book book) {
        if (bookRepository.existsByIsbn(book.isbn())) {
            throw new BookAlreadyExistsException(book.isbn());
        }

        var saved = bookRepository.save(book);
        publisher.publishBookCreated(saved).block();
        return saved;
    }
}
