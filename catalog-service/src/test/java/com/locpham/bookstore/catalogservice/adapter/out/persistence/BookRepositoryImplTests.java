package com.locpham.bookstore.catalogservice.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class BookRepositoryImplTests {

    @Mock private CacheManager cacheManager;
    @Mock private SpringDataBookRepository springDataBookRepository;

    private BookRepositoryImpl bookRepository;

    @BeforeEach
    void setup() {
        bookRepository = new BookRepositoryImpl(cacheManager, springDataBookRepository);
    }

    @Test
    void existsByIsbnWhenCacheHasBookReturnsTrue() {
        String isbn = "1234567890";
        Cache cache = mock(Cache.class);
        Cache.ValueWrapper valueWrapper = mock(Cache.ValueWrapper.class);

        given(cacheManager.getCache("books")).willReturn(cache);
        given(cache.get(isbn)).willReturn(valueWrapper);

        boolean exists = bookRepository.existsByIsbn(isbn);

        assertThat(exists).isTrue();
        verify(springDataBookRepository, never()).existsByIsbn(isbn);
    }

    @Test
    void existsByIsbnWhenCacheMissChecksDatabase() {
        String isbn = "1234567890";
        Cache cache = mock(Cache.class);

        given(cacheManager.getCache("books")).willReturn(cache);
        given(cache.get(isbn)).willReturn(null);
        given(springDataBookRepository.existsByIsbn(isbn)).willReturn(true);

        boolean exists = bookRepository.existsByIsbn(isbn);

        assertThat(exists).isTrue();
        verify(springDataBookRepository).existsByIsbn(isbn);
    }

    @Test
    void existsByIsbnWhenCacheNullChecksDatabase() {
        String isbn = "1234567890";

        given(cacheManager.getCache("books")).willReturn(null);
        given(springDataBookRepository.existsByIsbn(isbn)).willReturn(false);

        boolean exists = bookRepository.existsByIsbn(isbn);

        assertThat(exists).isFalse();
        verify(springDataBookRepository).existsByIsbn(isbn);
    }

    @Test
    void findByIsbnMapsEntityToDomain() {
        String isbn = "1234567890";
        var entity = new BookEntity(1L, isbn, "Title", "Author", 9.99, "Pub", null, null, 0);
        given(springDataBookRepository.findByIsbn(isbn)).willReturn(java.util.Optional.of(entity));

        var book = bookRepository.findByIsbn(isbn);

        assertThat(book).isPresent();
        assertThat(book.get().isbn()).isEqualTo(isbn);
        assertThat(book.get().title()).isEqualTo("Title");
    }

    @Test
    void findAllMapsAllEntities() {
        var entity =
                new BookEntity(1L, "1234567890", "Title", "Author", 9.99, "Pub", null, null, 0);
        given(springDataBookRepository.findAll()).willReturn(java.util.List.of(entity));

        var books = bookRepository.findAll();

        assertThat(books).hasSize(1);
        assertThat(books.iterator().next().isbn()).isEqualTo("1234567890");
    }

    @Test
    void saveReturnsPersistedDomain() {
        var book =
                com.locpham.bookstore.catalogservice.domain.book.Book.build(
                        "1234567890", "Title", "Author", 9.99, "Pub");
        var savedEntity =
                new BookEntity(1L, "1234567890", "Title", "Author", 9.99, "Pub", null, null, 0);
        given(springDataBookRepository.save(org.mockito.ArgumentMatchers.any()))
                .willReturn(savedEntity);

        var saved = bookRepository.save(book);

        assertThat(saved.id()).isEqualTo(1L);
        assertThat(saved.isbn()).isEqualTo("1234567890");
        verify(springDataBookRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteByIsbnDelegatesToRepository() {
        bookRepository.deleteByIsbn("1234567890");

        verify(springDataBookRepository).deleteByIsbn("1234567890");
    }
}
