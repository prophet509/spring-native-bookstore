package com.locpham.bookstore.catalogservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.catalogservice.application.port.out.BookEventPublisher;
import com.locpham.bookstore.catalogservice.application.port.out.BookRepository;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import com.locpham.bookstore.catalogservice.domain.book.exception.BookAlreadyExistsException;
import com.locpham.bookstore.catalogservice.domain.book.exception.BookNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class BookCatalogServiceTests {

    @Mock private BookRepository bookRepository;
    @Mock private BookEventPublisher publisher;

    @InjectMocks private BookCatalogService service;

    private Book book;

    @BeforeEach
    void setup() {
        book = Book.build("1234567890", "Title", "Author", 9.90, "Polarsophia");
    }

    @Test
    void addBookWhenNotExistsShouldSaveAndPublishCreated() {
        given(bookRepository.existsByIsbn(book.isbn())).willReturn(false);
        given(bookRepository.save(book)).willReturn(book);
        given(publisher.publishBookCreated(any(Book.class))).willReturn(Mono.empty());

        var saved = service.addBookToCatalog(book);

        assertThat(saved).isEqualTo(book);
        verify(publisher).publishBookCreated(book);
    }

    @Test
    void addBookWhenExistsShouldThrow() {
        given(bookRepository.existsByIsbn(book.isbn())).willReturn(true);

        assertThatThrownBy(() -> service.addBookToCatalog(book))
                .isInstanceOf(BookAlreadyExistsException.class);

        verify(publisher, never()).publishBookCreated(any());
    }

    @Test
    void editBookWhenNotExistsShouldThrow() {
        given(bookRepository.existsByIsbn(book.isbn())).willReturn(false);

        assertThatThrownBy(() -> service.editBookDetails(book))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void editBookWhenExistsShouldMergeAndPublishUpdated() {
        var existing = Book.build("1234567890", "Old", "Old", 1.0, "OldPub");
        var partial = Book.build("1234567890", "NewTitle", null, null, null);
        given(bookRepository.existsByIsbn("1234567890")).willReturn(true);
        given(bookRepository.findByIsbn("1234567890")).willReturn(Optional.of(existing));
        given(bookRepository.save(any(Book.class))).willAnswer(inv -> inv.getArgument(0));
        given(publisher.publishBookUpdated(any(Book.class))).willReturn(Mono.empty());

        var saved = service.editBookDetails(partial);

        assertThat(saved.title()).isEqualTo("NewTitle");
        assertThat(saved.author()).isEqualTo("Old");
        verify(publisher).publishBookUpdated(saved);
    }

    @Test
    void deleteBookWhenNotExistsShouldThrow() {
        given(bookRepository.existsByIsbn("1234567890")).willReturn(false);

        assertThatThrownBy(() -> service.deleteBook("1234567890"))
                .isInstanceOf(BookNotFoundException.class);

        verify(publisher, never()).publishBookDeleted(any());
    }

    @Test
    void deleteBookWhenExistsShouldDeleteAndPublishDeleted() {
        given(bookRepository.existsByIsbn("1234567890")).willReturn(true);
        given(publisher.publishBookDeleted("1234567890")).willReturn(Mono.empty());

        service.deleteBook("1234567890");

        verify(bookRepository).deleteByIsbn("1234567890");
        verify(publisher).publishBookDeleted("1234567890");
    }

    @Test
    void viewBookDetailWhenNotFoundShouldThrow() {
        given(bookRepository.findByIsbn("1234567890")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.viewBookDetail("1234567890"))
                .isInstanceOf(BookNotFoundException.class);
    }
}
