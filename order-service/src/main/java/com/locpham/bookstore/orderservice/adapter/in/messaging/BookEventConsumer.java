package com.locpham.bookstore.orderservice.adapter.in.messaging;

import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookCreatedMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookDeletedMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookUpdatedMessage;
import com.locpham.bookstore.orderservice.application.port.out.CatalogBookSnapshotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class BookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookEventConsumer.class);

    private final CatalogBookSnapshotPort snapshotPort;

    public BookEventConsumer(CatalogBookSnapshotPort snapshotPort) {
        this.snapshotPort = snapshotPort;
    }

    @KafkaListener(
            topics = "${polar.kafka.topics.book-created:book.created}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public Mono<Void> handleBookCreated(BookCreatedMessage message) {
        log.info("book.created: isbn={} title={}", message.isbn(), message.title());
        return snapshotPort.upsert(message.isbn(), message.title(), message.price()).then();
    }

    @KafkaListener(
            topics = "${polar.kafka.topics.book-updated:book.updated}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public Mono<Void> handleBookUpdated(BookUpdatedMessage message) {
        log.info("book.updated: isbn={} title={}", message.isbn(), message.title());
        return snapshotPort.upsert(message.isbn(), message.title(), message.price()).then();
    }

    @KafkaListener(
            topics = "${polar.kafka.topics.book-deleted:book.deleted}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public Mono<Void> handleBookDeleted(BookDeletedMessage message) {
        log.info("book.deleted: isbn={}", message.isbn());
        return snapshotPort.deleteByIsbn(message.isbn()).then();
    }
}
