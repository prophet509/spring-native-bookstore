package com.locpham.bookstore.catalogservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.locpham.bookstore.catalogservice.adapter.out.persistence.OutboxEvent;
import com.locpham.bookstore.catalogservice.adapter.out.persistence.SpringDataOutboxRepository;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Pure unit test that verifies the outbox publisher writes the active span's W3C traceparent
 * ({@code 00-<traceId>-<spanId>-01}) into the {@code trace_id} column. The Debezium outbox router
 * is configured to promote that column to a {@code traceparent} Kafka header, so this test guards
 * the producer side of trace propagation.
 */
class OutboxBookEventPublisherTraceTest {

    @Test
    void traceparent_isWrittenIntoOutboxRow_whenSpanIsActive() {
        var outboxRepository = mock(SpringDataOutboxRepository.class);
        var tracer = mock(Tracer.class);
        var span = mock(Span.class);
        var ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(ctx.spanId()).thenReturn("0123456789abcdef");

        var publisher = new OutboxBookEventPublisher(outboxRepository, tracer);
        var book = Book.build("ISBN-TRACE-1", "T", "A", 1.0, "P");

        publisher.publishBookCreated(book).block();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.aggregateType()).isEqualTo("book");
        assertThat(saved.aggregateId()).isEqualTo("ISBN-TRACE-1");
        assertThat(saved.type()).isEqualTo("BookCreated");
        assertThat(saved.destination()).isEqualTo("book.created");
        assertThat(saved.payload()).isNotNull();
        assertThat(saved.payload().json()).contains("ISBN-TRACE-1");
        assertThat(saved.traceId())
                .isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    }

    @Test
    void traceparent_isNull_whenNoSpanIsActive() {
        var outboxRepository = mock(SpringDataOutboxRepository.class);
        var tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        var publisher = new OutboxBookEventPublisher(outboxRepository, tracer);
        var book = Book.build("ISBN-TRACE-2", "T", "A", 1.0, "P");

        publisher.publishBookCreated(book).block();

        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.aggregateId()).isEqualTo("ISBN-TRACE-2");
        assertThat(saved.type()).isEqualTo("BookCreated");
        assertThat(saved.traceId()).isNull();
    }
}
