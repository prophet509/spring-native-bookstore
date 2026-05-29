package com.locpham.bookstore.orderservice.adapter.out.snapshot;

import com.locpham.bookstore.orderservice.application.port.out.CatalogBookPort;
import com.locpham.bookstore.orderservice.application.port.out.CatalogBookSnapshotPort;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class SnapshotBookAdapter implements CatalogBookPort {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBookAdapter.class);

    private final CatalogBookSnapshotPort snapshotPort;

    public SnapshotBookAdapter(CatalogBookSnapshotPort snapshotPort) {
        this.snapshotPort = snapshotPort;
    }

    @Override
    public Mono<BookSnapshot> loadBook(String isbn) {
        return snapshotPort
                .findByIsbn(isbn)
                .doOnNext(book -> log.debug("Snapshot hit for isbn={}", isbn))
                .switchIfEmpty(
                        Mono.fromRunnable(
                                () -> {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Snapshot miss for isbn={}", isbn);
                                    }
                                }));
    }
}
