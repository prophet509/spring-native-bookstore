package com.locpham.bookstore.catalogservice.adapter.out.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locpham.bookstore.catalogservice.domain.book.Book;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pure unit test that verifies the outbox publisher writes the active span's W3C traceparent
 * ({@code 00-<traceId>-<spanId>-01}) into the {@code trace_id} column. The Debezium outbox router
 * is configured to promote that column to a {@code traceparent} Kafka header, so this test guards
 * the producer side of trace propagation.
 */
class OutboxBookEventPublisherTraceTest {

    @Test
    void traceparent_isWrittenIntoOutboxRow_whenSpanIsActive() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var tracer = mock(Tracer.class);
        var span = mock(Span.class);
        var ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(ctx.spanId()).thenReturn("0123456789abcdef");

        var publisher = new OutboxBookEventPublisher(jdbcTemplate, tracer);
        var book = Book.build("ISBN-TRACE-1", "T", "A", 1.0, "P");

        publisher.publishBookCreated(book).block();

        // INSERT params: id (UUID), aggregateId (isbn), type, destination, payload (json), trace_id
        verify(jdbcTemplate)
                .update(
                        any(String.class),
                        any(java.util.UUID.class),
                        eq("ISBN-TRACE-1"),
                        eq("BookCreated"),
                        eq("book.created"),
                        any(String.class),
                        eq("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"));
    }

    @Test
    void traceparent_isNull_whenNoSpanIsActive() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        var publisher = new OutboxBookEventPublisher(jdbcTemplate, tracer);
        var book = Book.build("ISBN-TRACE-2", "T", "A", 1.0, "P");

        publisher.publishBookCreated(book).block();

        verify(jdbcTemplate)
                .update(
                        any(String.class),
                        any(java.util.UUID.class),
                        eq("ISBN-TRACE-2"),
                        eq("BookCreated"),
                        eq("book.created"),
                        any(String.class),
                        eq((String) null));
    }
}
