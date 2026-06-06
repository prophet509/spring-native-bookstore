package com.locpham.bookstore.inventoryservice.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.inventoryservice.adapter.out.messaging.messages.InventoryDecisionMessage;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryEventPublisher;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Transactional outbox publisher. Writes one {@code outbox_event} row per decision on the same
 * R2DBC connection as the reservation save (wrap caller in a transaction). Debezium publishes the
 * row to {@code inventory-events}. Payload reuses the legacy DTO so the Kafka wire format is
 * unchanged.
 */
@Component
public class OutboxInventoryEventPublisher implements InventoryEventPublisher {

    private final DSLContext dsl;
    private final Tracer tracer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxInventoryEventPublisher(DSLContext dsl, Tracer tracer) {
        this.dsl = dsl;
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
                                Flux.from(
                                                dsl.insertInto(DSL.table(DSL.name("outbox_event")))
                                                        .columns(
                                                                DSL.field(
                                                                        DSL.name("id"), UUID.class),
                                                                DSL.field(
                                                                        DSL.name("aggregate_type"),
                                                                        String.class),
                                                                DSL.field(
                                                                        DSL.name("aggregate_id"),
                                                                        String.class),
                                                                DSL.field(
                                                                        DSL.name("type"),
                                                                        String.class),
                                                                DSL.field(
                                                                        DSL.name("destination"),
                                                                        String.class),
                                                                DSL.field(
                                                                        DSL.name("payload"),
                                                                        JSONB.class),
                                                                DSL.field(
                                                                        DSL.name("trace_id"),
                                                                        String.class))
                                                        .values(
                                                                UUID.randomUUID(),
                                                                "inventory",
                                                                String.valueOf(decision.orderId()),
                                                                "InventoryDecision",
                                                                "inventory-events",
                                                                JSONB.valueOf(json),
                                                                traceparent))
                                        .then());
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
