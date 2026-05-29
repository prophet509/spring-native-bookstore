package com.locpham.bookstore.orderservice.adapter.in.messaging;

import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookCreatedMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookDeletedMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookUpdatedMessage;
import com.locpham.bookstore.orderservice.application.port.out.CatalogBookSnapshotPort;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

@Configuration
public class BookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookEventConsumer.class);

    private final CatalogBookSnapshotPort snapshotPort;

    public BookEventConsumer(CatalogBookSnapshotPort snapshotPort) {
        this.snapshotPort = snapshotPort;
    }

    @Bean
    public Consumer<Flux<BookCreatedMessage>> handleBookCreated() {
        return flux ->
                flux.flatMap(
                                message -> {
                                    log.info(
                                            "book.created: isbn={} title={}",
                                            message.isbn(),
                                            message.title());
                                    return snapshotPort.upsert(
                                            message.isbn(), message.title(), message.price());
                                },
                                8)
                        .subscribe();
    }

    @Bean
    public Consumer<Flux<BookUpdatedMessage>> handleBookUpdated() {
        return flux ->
                flux.flatMap(
                                message -> {
                                    log.info(
                                            "book.updated: isbn={} title={}",
                                            message.isbn(),
                                            message.title());
                                    return snapshotPort.upsert(
                                            message.isbn(), message.title(), message.price());
                                },
                                8)
                        .subscribe();
    }

    @Bean
    public Consumer<Flux<BookDeletedMessage>> handleBookDeleted() {
        return flux ->
                flux.flatMap(
                                message -> {
                                    log.info("book.deleted: isbn={}", message.isbn());
                                    return snapshotPort.deleteByIsbn(message.isbn());
                                },
                                8)
                        .subscribe();
    }
}
