package com.locpham.bookstore.catalogservice.config;

import com.locpham.bookstore.catalogservice.adapter.out.messaging.BookCreatedMessage;
import com.locpham.bookstore.catalogservice.adapter.out.messaging.BookDeletedMessage;
import com.locpham.bookstore.catalogservice.adapter.out.messaging.BookUpdatedMessage;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Configuration
public class CatalogBookEventStreamConfig {

    @Bean
    public Sinks.Many<BookCreatedMessage> bookCreatedSink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    @Bean
    public Sinks.Many<BookUpdatedMessage> bookUpdatedSink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    @Bean
    public Sinks.Many<BookDeletedMessage> bookDeletedSink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    @Bean
    public Supplier<Flux<BookCreatedMessage>> bookCreatedEvents(
            Sinks.Many<BookCreatedMessage> bookCreatedSink) {
        return bookCreatedSink::asFlux;
    }

    @Bean
    public Supplier<Flux<BookUpdatedMessage>> bookUpdatedEvents(
            Sinks.Many<BookUpdatedMessage> bookUpdatedSink) {
        return bookUpdatedSink::asFlux;
    }

    @Bean
    public Supplier<Flux<BookDeletedMessage>> bookDeletedEvents(
            Sinks.Many<BookDeletedMessage> bookDeletedSink) {
        return bookDeletedSink::asFlux;
    }
}
