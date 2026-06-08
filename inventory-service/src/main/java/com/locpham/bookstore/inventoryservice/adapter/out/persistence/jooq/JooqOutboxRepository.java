package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

import static com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.generated.tables.OutboxEvent.OUTBOX_EVENT;

import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Appends rows to the {@code outbox_event} table on the caller's R2DBC connection. Keeping the jOOQ
 * insert here (instead of in the messaging publisher) isolates the table contract to the
 * persistence adapter, mirroring {@link JooqInventoryRepositoryImpl}.
 */
@Repository
public class JooqOutboxRepository {

    private final DSLContext dsl;

    public JooqOutboxRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts a single outbox row. Uses {@code Flux.from(...).then()} (not {@code Mono.from}) so
     * the jOOQ insert completes without an early cancel that would reclaim the shared transactional
     * connection mid-transaction.
     */
    public Mono<Void> append(OutboxRecord record) {
        return Flux.from(
                        dsl.insertInto(OUTBOX_EVENT)
                                .columns(
                                        OUTBOX_EVENT.ID,
                                        OUTBOX_EVENT.AGGREGATE_TYPE,
                                        OUTBOX_EVENT.AGGREGATE_ID,
                                        OUTBOX_EVENT.TYPE,
                                        OUTBOX_EVENT.DESTINATION,
                                        OUTBOX_EVENT.PAYLOAD,
                                        OUTBOX_EVENT.TRACE_ID)
                                .values(
                                        UUID.randomUUID(),
                                        record.aggregateType(),
                                        record.aggregateId(),
                                        record.type(),
                                        record.destination(),
                                        JSONB.valueOf(record.payloadJson()),
                                        record.traceparent()))
                .then();
    }
}
