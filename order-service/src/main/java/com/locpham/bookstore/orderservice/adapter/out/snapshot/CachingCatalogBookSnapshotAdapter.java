package com.locpham.bookstore.orderservice.adapter.out.snapshot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.locpham.bookstore.orderservice.application.port.out.CatalogBookSnapshotPort;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import java.time.Duration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * In-process Caffeine cache in front of {@link R2dbcCatalogBookSnapshotAdapter}.
 *
 * <p>Each {@code POST /orders} reads the book snapshot. Without a cache, every request hits
 * Postgres for the same ISBN — under concurrent load this dominates p50. The cache keeps the
 * snapshot read off the DB hot-path while invalidating on every mutation that the catalog book
 * event consumer applies, so consistency is preserved. The 5-minute TTL is a safety net in case an
 * event is ever missed.
 */
@Component
@Primary
class CachingCatalogBookSnapshotAdapter implements CatalogBookSnapshotPort {

    private final CatalogBookSnapshotPort delegate;
    private final Cache<String, BookSnapshot> cache;

    CachingCatalogBookSnapshotAdapter(R2dbcCatalogBookSnapshotAdapter delegate) {
        this.delegate = delegate;
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .build();
    }

    @Override
    public Mono<BookSnapshot> findByIsbn(String isbn) {
        BookSnapshot cached = cache.getIfPresent(isbn);
        if (cached != null) {
            return Mono.just(cached);
        }
        return delegate.findByIsbn(isbn).doOnNext(snapshot -> cache.put(isbn, snapshot));
    }

    @Override
    public Mono<Long> upsert(String isbn, String title, Double price) {
        return delegate.upsert(isbn, title, price).doOnSuccess(rows -> cache.invalidate(isbn));
    }

    @Override
    public Mono<Long> deleteByIsbn(String isbn) {
        return delegate.deleteByIsbn(isbn).doOnSuccess(rows -> cache.invalidate(isbn));
    }
}
