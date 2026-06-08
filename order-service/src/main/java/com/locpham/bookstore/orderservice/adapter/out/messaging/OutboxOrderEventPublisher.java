package com.locpham.bookstore.orderservice.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.orderservice.adapter.out.persistence.jooq.JooqOutboxRepository;
import com.locpham.bookstore.orderservice.adapter.out.persistence.jooq.OutboxRecord;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.domain.model.Order;
import io.micrometer.tracing.Tracer;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Transactional outbox publisher. Builds one {@code outbox_event} row per domain event and
 * delegates the insert to {@link JooqOutboxRepository}, which runs on the same R2DBC connection as
 * the aggregate save (wrap caller in a transaction). Debezium tails the row and publishes to the
 * topic named in {@code destination}. Payload reuses the legacy message DTOs so the Kafka wire
 * format is unchanged.
 */
@Component
public class OutboxOrderEventPublisher implements OrderEventPublisherPort {

    private static final String AGGREGATE_TYPE = "order";

    private final JooqOutboxRepository outboxRepository;
    private final Tracer tracer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxOrderEventPublisher(JooqOutboxRepository outboxRepository, Tracer tracer) {
        this.outboxRepository = outboxRepository;
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
        String traceparent = currentTraceparent();
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(message))
                .flatMap(
                        json ->
                                outboxRepository.append(
                                        new OutboxRecord(
                                                AGGREGATE_TYPE,
                                                String.valueOf(aggregateId),
                                                type,
                                                destination,
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
