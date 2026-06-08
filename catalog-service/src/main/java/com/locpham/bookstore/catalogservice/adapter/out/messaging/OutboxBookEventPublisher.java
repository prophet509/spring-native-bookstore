package com.locpham.bookstore.catalogservice.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.catalogservice.adapter.out.persistence.JsonbPayload;
import com.locpham.bookstore.catalogservice.adapter.out.persistence.OutboxEvent;
import com.locpham.bookstore.catalogservice.adapter.out.persistence.SpringDataOutboxRepository;
import com.locpham.bookstore.catalogservice.application.port.out.BookEventPublisher;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Transactional outbox publisher. Inserts one {@code outbox_event} row per mutation via Spring Data
 * JDBC; because the caller ({@code BookCatalogService}) runs the save and this insert inside the
 * same {@code @Transactional} method, both commit atomically. Debezium publishes the row to the
 * topic named in {@code destination}. Payload reuses the legacy DTOs so the Kafka wire format is
 * unchanged.
 */
@Component
public class OutboxBookEventPublisher implements BookEventPublisher {

    private static final String AGGREGATE_TYPE = "book";

    private final SpringDataOutboxRepository outboxRepository;
    private final Tracer tracer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxBookEventPublisher(SpringDataOutboxRepository outboxRepository, Tracer tracer) {
        this.outboxRepository = outboxRepository;
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> publishBookCreated(Book book) {
        return append(
                book.isbn(),
                "BookCreated",
                "book.created",
                new BookCreatedMessage(
                        book.isbn(), book.title(), book.author(), book.price(), book.publisher()));
    }

    @Override
    public Mono<Void> publishBookUpdated(Book book) {
        return append(
                book.isbn(),
                "BookUpdated",
                "book.updated",
                new BookUpdatedMessage(
                        book.isbn(), book.title(), book.author(), book.price(), book.publisher()));
    }

    @Override
    public Mono<Void> publishBookDeleted(String isbn) {
        return append(isbn, "BookDeleted", "book.deleted", new BookDeletedMessage(isbn));
    }

    private Mono<Void> append(String isbn, String type, String destination, Object message) {
        return Mono.fromRunnable(
                () -> {
                    String json;
                    try {
                        json = objectMapper.writeValueAsString(message);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Failed to serialize outbox payload", e);
                    }
                    outboxRepository.save(
                            new OutboxEvent(
                                    UUID.randomUUID(),
                                    AGGREGATE_TYPE,
                                    isbn,
                                    type,
                                    destination,
                                    new JsonbPayload(json),
                                    currentTraceparent()));
                });
    }

    /**
     * Builds a W3C {@code traceparent} header value ({@code 00-<traceId>-<spanId>-01}) from the
     * active span so Debezium can promote it to a Kafka header and downstream consumers continue
     * the same trace. Returns {@code null} when no span is active.
     */
    private String currentTraceparent() {
        var span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        var ctx = span.context();
        return "00-" + ctx.traceId() + "-" + ctx.spanId() + "-01";
    }
}
