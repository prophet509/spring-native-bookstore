package com.locpham.bookstore.inventoryservice.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.inventoryservice.adapter.out.messaging.messages.InventoryDecisionMessage;
import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.JooqOutboxRepository;
import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.OutboxRecord;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryEventPublisher;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Transactional outbox publisher. Builds one {@code outbox_event} row per decision and delegates
 * the insert to {@link JooqOutboxRepository}, which runs on the same R2DBC connection as the
 * reservation save (wrap caller in a transaction). Debezium publishes the row to {@code
 * inventory-events}. Payload reuses the legacy DTO so the Kafka wire format is unchanged.
 */
@Component
public class OutboxInventoryEventPublisher implements InventoryEventPublisher {

    private static final String AGGREGATE_TYPE = "inventory";

    private final JooqOutboxRepository outboxRepository;
    private final Tracer tracer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxInventoryEventPublisher(JooqOutboxRepository outboxRepository, Tracer tracer) {
        this.outboxRepository = outboxRepository;
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> publishInventoryDecision(InventoryDecision decision) {
        var message =
                new InventoryDecisionMessage(
                        decision.orderId(), decision.status().name(), decision.reason());
        String traceparent = currentTraceparent();
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(message))
                .flatMap(
                        json ->
                                outboxRepository.append(
                                        new OutboxRecord(
                                                AGGREGATE_TYPE,
                                                String.valueOf(decision.orderId()),
                                                "InventoryDecision",
                                                "inventory-events",
                                                json,
                                                traceparent)));
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
