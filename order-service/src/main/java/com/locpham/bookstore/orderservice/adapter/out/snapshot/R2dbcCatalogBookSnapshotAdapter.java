package com.locpham.bookstore.orderservice.adapter.out.snapshot;

import com.locpham.bookstore.orderservice.application.port.out.CatalogBookSnapshotPort;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
class R2dbcCatalogBookSnapshotAdapter implements CatalogBookSnapshotPort {

    private static final Logger log =
            LoggerFactory.getLogger(R2dbcCatalogBookSnapshotAdapter.class);

    private final DatabaseClient databaseClient;

    R2dbcCatalogBookSnapshotAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<BookSnapshot> findByIsbn(String isbn) {
        return databaseClient
                .sql("SELECT isbn, title, price FROM catalog_book_snapshot WHERE isbn = :isbn")
                .bind("isbn", isbn)
                .map((row, metadata) -> toBookSnapshot(row))
                .one()
                .doOnNext(book -> log.debug("Snapshot hit for isbn={}", isbn))
                .switchIfEmpty(Mono.empty())
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "Failed to read snapshot for isbn={}: {}",
                                    isbn,
                                    e.getMessage());
                            return Mono.empty();
                        });
    }

    @Override
    public Mono<Long> upsert(String isbn, String title, Double price) {
        return databaseClient
                .sql(
                        """
                        INSERT INTO catalog_book_snapshot (isbn, title, price, updated_at)
                        VALUES (:isbn, :title, :price, now())
                        ON CONFLICT (isbn) DO UPDATE SET
                            title = EXCLUDED.title,
                            price = EXCLUDED.price,
                            updated_at = EXCLUDED.updated_at
                        """)
                .bind("isbn", isbn)
                .bind("title", title)
                .bind("price", price != null ? price : 0.0)
                .fetch()
                .rowsUpdated()
                .doOnSuccess(
                        rows -> log.debug("Upserted snapshot isbn={} rowsAffected={}", isbn, rows))
                .onErrorResume(
                        e -> {
                            log.error(
                                    "Failed to upsert snapshot isbn={}: {}", isbn, e.getMessage());
                            return Mono.empty();
                        });
    }

    @Override
    public Mono<Long> deleteByIsbn(String isbn) {
        return databaseClient
                .sql("DELETE FROM catalog_book_snapshot WHERE isbn = :isbn")
                .bind("isbn", isbn)
                .fetch()
                .rowsUpdated()
                .doOnSuccess(
                        rows -> log.info("Deleted snapshot isbn={} rowsAffected={}", isbn, rows))
                .onErrorResume(
                        e -> {
                            log.error(
                                    "Failed to delete snapshot isbn={}: {}", isbn, e.getMessage());
                            return Mono.empty();
                        });
    }

    private BookSnapshot toBookSnapshot(Row row) {
        var isbn = row.get("isbn", String.class);
        var title = row.get("title", String.class);
        var price = row.get("price", BigDecimal.class);
        return new BookSnapshot(isbn, title, price != null ? price.doubleValue() : 0.0);
    }
}
