package com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka;

import com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka.message.BookCreatedMessage;
import com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka.message.BookDeletedMessage;
import com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka.message.BookUpdatedMessage;
import com.locpham.bookstore.searchservice.application.out.persistence.BookIndexRepository;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class KafkaBookEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaBookEventPublisher.class);

    @Bean
    public Function<Flux<BookCreatedMessage>, Mono<Void>> handleBookCreated(
            BookIndexRepository bookIndexRepository) {
        return flux ->
                flux.doOnNext(m -> LOGGER.info("book.created: {}", m.isbn()))
                        .flatMap(
                                m ->
                                        bookIndexRepository
                                                .save(m.toDomain())
                                                .onErrorMap(
                                                        e -> {
                                                            LOGGER.error(
                                                                    "Failed to save book: {}",
                                                                    m.isbn(),
                                                                    e);
                                                            return e;
                                                        }))
                        .doOnError(e -> LOGGER.error("Error in handleBookCreated", e))
                        .then();
    }

    @Bean
    public Function<Flux<BookUpdatedMessage>, Mono<Void>> handleBookUpdated(
            BookIndexRepository bookIndexRepository) {
        return flux ->
                flux.doOnNext(m -> LOGGER.info("book.updated: {}", m.isbn()))
                        .flatMap(
                                m ->
                                        bookIndexRepository
                                                .save(m.toDomain())
                                                .onErrorMap(
                                                        e -> {
                                                            LOGGER.error(
                                                                    "Failed to update book: {}",
                                                                    m.isbn(),
                                                                    e);
                                                            return e;
                                                        }))
                        .doOnError(e -> LOGGER.error("Error in handleBookUpdated", e))
                        .then();
    }

    @Bean
    public Function<Flux<BookDeletedMessage>, Mono<Void>> handleBookDeleted(
            BookIndexRepository bookIndexRepository) {
        return flux ->
                flux.doOnNext(m -> LOGGER.info("book.deleted: {}", m.isbn()))
                        .flatMap(
                                m ->
                                        bookIndexRepository
                                                .deleteByIsbn(m.isbn())
                                                .onErrorMap(
                                                        e -> {
                                                            LOGGER.error(
                                                                    "Failed to delete book: {}",
                                                                    m.isbn(),
                                                                    e);
                                                            return e;
                                                        }))
                        .doOnError(e -> LOGGER.error("Error in handleBookDeleted", e))
                        .then();
    }
}
