package com.locpham.bookstore.catalogservice.adapter.out.messaging;

import com.locpham.bookstore.catalogservice.application.port.out.BookEventPublisher;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Component
public class KafkaBookEventPublisher implements BookEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaBookEventPublisher.class);

    private final Sinks.Many<BookCreatedMessage> bookCreatedSink;
    private final Sinks.Many<BookUpdatedMessage> bookUpdatedSink;
    private final Sinks.Many<BookDeletedMessage> bookDeletedSink;

    public KafkaBookEventPublisher(
            Sinks.Many<BookCreatedMessage> bookCreatedSink,
            Sinks.Many<BookUpdatedMessage> bookUpdatedSink,
            Sinks.Many<BookDeletedMessage> bookDeletedSink) {
        this.bookCreatedSink = bookCreatedSink;
        this.bookUpdatedSink = bookUpdatedSink;
        this.bookDeletedSink = bookDeletedSink;
    }

    @Override
    public Mono<Void> publishBookCreated(Book book) {
        return emit(
                bookCreatedSink,
                "book.created",
                new BookCreatedMessage(
                        book.isbn(), book.title(), book.author(), book.price(), book.publisher()));
    }

    @Override
    public Mono<Void> publishBookUpdated(Book book) {
        return emit(
                bookUpdatedSink,
                "book.updated",
                new BookUpdatedMessage(
                        book.isbn(), book.title(), book.author(), book.price(), book.publisher()));
    }

    @Override
    public Mono<Void> publishBookDeleted(String isbn) {
        return emit(bookDeletedSink, "book.deleted", new BookDeletedMessage(isbn));
    }

    private <T> Mono<Void> emit(Sinks.Many<T> sink, String destination, T event) {
        return Mono.fromRunnable(
                () -> {
                    Sinks.EmitResult result = sink.tryEmitNext(event);
                    if (result.isFailure()) {
                        log.error("Failed to emit event to {}: {}", destination, result);
                        throw new IllegalStateException(
                                "emit failed for " + destination + ": " + result);
                    }
                });
    }
}
