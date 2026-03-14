package com.locpham.bookstore.catalogservice.domain;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BookService {
    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Iterable<Book> viewBookList() {
        return bookRepository.findAll();
    }

    public Book viewBookDetail(String isbn) {
        return bookRepository.findByIsbn(isbn).orElseThrow(() -> new BookNotFoundException(isbn));
    }

    public Book addBookToCatalog(Book book) {
        if (bookRepository.existsByIsbn(book.isbn())) {
            throw new BookAlreadyException(book.isbn());
        }
        return bookRepository.save(book);
    }

    public void removeBookFromCatalog(String isbn) {
        if (!bookRepository.existsByIsbn(isbn)) {
            throw new BookNotFoundException(isbn);
        }

        bookRepository.deleteByIsbn(isbn);
    }

    public Book editBookDetails(Book book) {
        Optional<Book> existingBook = bookRepository.findByIsbn(book.isbn());
        if (existingBook.isEmpty()) {
            throw new BookNotFoundException(book.isbn());
        }

        var existing = existingBook.get();
        var updatedPublisher = book.publisher() != null ? book.publisher() : existing.publisher();
        var bookToUpdate =
                new Book(
                        existing.id(),
                        existing.isbn(),
                        book.title(),
                        book.author(),
                        book.price(),
                        updatedPublisher,
                        existing.createdDate(),
                        existing.lastModifiedDate(),
                        existing.version());

        return bookRepository.save(bookToUpdate);
    }
}
