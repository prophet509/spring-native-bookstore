package com.locpham.bookstore.catalogservice.adapter.out.persistence;

import com.locpham.bookstore.catalogservice.application.port.out.BookRepository;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepositoryImpl implements BookRepository {
    private final CacheManager cacheManager;

    private final SpringDataBookRepository springDataBookRepository;

    public BookRepositoryImpl(CacheManager cacheManager, SpringDataBookRepository springDataBookRepository) {
        this.springDataBookRepository = springDataBookRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    @Cacheable(value = "books", key = "#isbn")
    public Optional<Book> findByIsbn(String isbn) {
        return springDataBookRepository.findByIsbn(isbn).map(BookEntity::toDomain);
    }

    @Override
    public boolean existsByIsbn(String isbn) {
        Cache bookCache = cacheManager.getCache("books");
        if (bookCache != null && bookCache.get(isbn) != null) {
            return true;
        }
        return springDataBookRepository.existsByIsbn(isbn);
    }

    @Override
    @CacheEvict(value = "books", key = "#isbn")
    public void deleteByIsbn(String isbn) {
        springDataBookRepository.deleteByIsbn(isbn);
    }

    @Override
    public Iterable<Book> findAll() {
        return StreamSupport.stream(springDataBookRepository.findAll().spliterator(), false)
                .map(BookEntity::toDomain)
                .toList();
    }

    @Override
    public Book save(Book book) {
        BookEntity entity = BookEntity.fromDomain(book);
        BookEntity saved = springDataBookRepository.save(entity);
        return saved.toDomain();
    }
}
