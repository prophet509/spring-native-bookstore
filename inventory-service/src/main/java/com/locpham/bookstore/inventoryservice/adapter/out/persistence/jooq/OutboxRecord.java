package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

/**
 * Immutable description of one {@code outbox_event} row to be appended. Used by the messaging
 * adapter to hand a ready-to-insert record to {@link JooqOutboxRepository} without depending on
 * jOOQ types itself.
 *
 * @param aggregateType logical aggregate name (e.g. {@code "inventory"})
 * @param aggregateId stringified aggregate id (becomes the Kafka message key)
 * @param type event type (e.g. {@code "InventoryDecision"})
 * @param destination target topic name (Debezium routes the row to this topic)
 * @param payloadJson the serialized event payload (stored as {@code jsonb})
 * @param traceparent W3C traceparent header value, or {@code null} when no span is active
 */
public record OutboxRecord(
        String aggregateType,
        String aggregateId,
        String type,
        String destination,
        String payloadJson,
        String traceparent) {}
