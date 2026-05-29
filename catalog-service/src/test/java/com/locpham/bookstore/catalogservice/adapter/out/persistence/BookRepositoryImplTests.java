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
}
