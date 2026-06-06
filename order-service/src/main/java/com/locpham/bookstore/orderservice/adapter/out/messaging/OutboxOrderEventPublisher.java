package com.locpham.bookstore.orderservice.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.domain.model.Order;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Transactional outbox publisher. Writes one {@code outbox_event} row per domain event using the
 * same R2DBC connection as the aggregate save (wrap caller in a transaction). Debezium tails the
 * row and publishes to the topic named in {@code destination}. Payload reuses the legacy message
 * DTOs so the Kafka wire format is unchanged.
 */
@Component
public class OutboxOrderEventPublisher implements OrderEventPublisherPort {

    private final DSLContext dsl;
    private final Tracer tracer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxOrderEventPublisher(DSLContext dsl, Tracer tracer) {
        this.dsl = dsl;
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> publishOrderCreated(Order order) {
        var message =
                new OrderCreatedMessage(
                        order.id(),
                        List.of(
                                new OrderCreatedMessage.OrderItem(
                                        order.book().isbn(), order.quantity())));
        return append(order.id(), "OrderCreated", "order-created-events", message);
    }

    @Override
    public Mono<Void> publishOrderAccepted(Order order) {
        return append(
                order.id(),
                "OrderAccepted",
                "order-accepted",
                new OrderAcceptedMessage(order.id()));
    }

    @Override
    public Mono<Void> publishOrderCancelled(Long orderId) {
        return append(
                orderId,
                "OrderCancelled",
                "order-cancelled-events",
                new OrderCancelledMessage(orderId));
    }

    private Mono<Void> append(Long aggregateId, String type, String destination, Object message) {
        // Flux.from(...).then() (not Mono.from) lets the jOOQ insert complete without an early
        // cancel that would reclaim the shared transactional connection.
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
                                                                "order",
                                                                String.valueOf(aggregateId),
                                                                type,
                                                                destination,
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
