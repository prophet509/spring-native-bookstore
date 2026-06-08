package com.locpham.bookstore.catalogservice.adapter.out.persistence;

/**
 * Typed wrapper around a JSON string so Spring Data JDBC can map the {@code outbox_event.payload}
 * column (Postgres {@code jsonb}) via a dedicated {@link JsonbPayloadWritingConverter}. Wrapping
 * the value keeps the converter scoped to this one column instead of coercing every {@code String}
 * field into a {@code jsonb} {@code PGobject}.
 */
public record JsonbPayload(String json) {}
