package com.locpham.bookstore.catalogservice.adapter.out.persistence;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Converts a {@link JsonbPayload} into a JSON {@link String} so Spring Data JDBC binds the {@code
 * outbox_event.payload} column correctly.
 */
@WritingConverter
public class JsonbPayloadWritingConverter implements Converter<JsonbPayload, String> {

    @Override
    public String convert(JsonbPayload source) {
        return source.json();
    }
}
