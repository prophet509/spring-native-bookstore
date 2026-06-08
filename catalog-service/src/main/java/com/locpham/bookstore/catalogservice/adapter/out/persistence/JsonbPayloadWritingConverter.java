package com.locpham.bookstore.catalogservice.adapter.out.persistence;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Converts a {@link JsonbPayload} into a Postgres {@link PGobject} of type {@code jsonb} so Spring
 * Data JDBC binds the {@code outbox_event.payload} column correctly. Without this, a plain {@code
 * String} would be sent as {@code varchar} and Postgres would reject the insert ("column is of type
 * jsonb but expression is of type character varying").
 */
@WritingConverter
public class JsonbPayloadWritingConverter implements Converter<JsonbPayload, PGobject> {

    @Override
    public PGobject convert(JsonbPayload source) {
        var pgObject = new PGobject();
        pgObject.setType("jsonb");
        try {
            pgObject.setValue(source.json());
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Failed to build jsonb PGobject for outbox payload", e);
        }
        return pgObject;
    }
}
