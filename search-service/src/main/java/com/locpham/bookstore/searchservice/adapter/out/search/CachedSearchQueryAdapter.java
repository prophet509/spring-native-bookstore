package com.locpham.bookstore.searchservice.adapter.out.search;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.locpham.bookstore.searchservice.application.out.search.SearchQuery;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Primary
public class CachedSearchQueryAdapter implements SearchQuery {

    private static final Logger log = LoggerFactory.getLogger(CachedSearchQueryAdapter.class);

    private final SearchQuery delegate;
    private final Cache<String, Object> cache;

    public CachedSearchQueryAdapter(ElasticsearchSearchQueryAdapter delegate) {
        this.delegate = delegate;
        this.cache =
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(60))
                        .maximumSize(5_000)
                        .recordStats()
                        .build();
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByTitle(String title, Pageable pageable) {
        var key = buildKey("title", title, pageable);
        return getOrFetch(key, () -> delegate.searchByTitle(title, pageable));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByAuthor(String author, Pageable pageable) {
        var key = buildKey("author", author, pageable);
        return getOrFetch(key, () -> delegate.searchByAuthor(author, pageable));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByPublisher(String publisher, Pageable pageable) {
        var key = buildKey("publisher", publisher, pageable);
        return getOrFetch(key, () -> delegate.searchByPublisher(publisher, pageable));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchByIsbn(String isbn, Pageable pageable) {
        var key = buildKey("isbn", isbn, pageable);
        return getOrFetch(key, () -> delegate.searchByIsbn(isbn, pageable));
    }

    @Override
    public Mono<SearchPage<BookDocument>> searchAll(Pageable pageable) {
        var key = buildKey("all", "", pageable);
        return getOrFetch(key, () -> delegate.searchAll(pageable));
    }

    @Override
    public Flux<String> suggestByTitle(String prefix) {
        var key = "suggest:title:" + prefix;
        @SuppressWarnings("unchecked")
        var cached = (Flux<String>) cache.getIfPresent(key);
        if (cached != null) {
            log.debug("Suggest cache hit: {}", key);
            return cached;
        }
        log.debug("Suggest cache miss: {}", key);
        return delegate.suggestByTitle(prefix).cache();
    }

    @Override
    public Flux<String> suggestByAuthor(String prefix) {
        var key = "suggest:author:" + prefix;
        @SuppressWarnings("unchecked")
        var cached = (Flux<String>) cache.getIfPresent(key);
        if (cached != null) {
            log.debug("Suggest cache hit: {}", key);
            return cached;
        }
        log.debug("Suggest cache miss: {}", key);
        return delegate.suggestByAuthor(prefix).cache();
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<T> getOrFetch(String key, java.util.function.Supplier<Mono<T>> fetcher) {
        var cached = (T) cache.getIfPresent(key);
        if (cached != null) {
            log.debug("Query cache hit: {}", key);
            return Mono.just(cached);
        }
        log.debug("Query cache miss: {}", key);
        return fetcher.get().doOnNext(result -> cache.put(key, result));
    }

    private String buildKey(String type, String term, Pageable pageable) {
        return "search:"
                + type
                + ":"
                + term
                + ":"
                + pageable.getPageNumber()
                + ":"
                + pageable.getPageSize();
    }
}
