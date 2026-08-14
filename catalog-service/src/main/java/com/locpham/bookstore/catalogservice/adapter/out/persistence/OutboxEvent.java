package com.locpham.bookstore.catalogservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC entity for the transactional outbox table. The {@code id} is an
 * application-assigned {@link UUID}, so this implements {@link Persistable} and reports {@code
 * isNew() == true}; otherwise Spring Data would see a non-null id and issue an UPDATE (which
 * affects zero rows) instead of an INSERT.
 *
 * <p>{@code created_at} is intentionally omitted so the database {@code DEFAULT now()} applies.
 */
@Table("outbox_event")
public record OutboxEvent(
        @Id String id,
        String aggregateType,
        String aggregateId,
        String type,
        String destination,
        JsonbPayload payload,
        String traceId)
        implements Persistable<String> {

    @Override
    public String getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}
