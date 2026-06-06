package com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka.message.BookCreatedMessage;
import com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka.message.BookDeletedMessage;
import com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka.message.BookUpdatedMessage;
import com.locpham.bookstore.searchservice.application.out.persistence.BookIndexRepository;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class KafkaBookEventPublisherTest {

    @Mock private BookIndexRepository repository;

    private final KafkaBookEventPublisher publisher = new KafkaBookEventPublisher();

    @Test
    void handleBookCreatedSavesDocument() {
        given(repository.save(any())).willReturn(Mono.just(mock("isbn-1")));

        var result =
                publisher
                        .handleBookCreated(repository)
                        .apply(Flux.just(new BookCreatedMessage("isbn-1", "T", "A", 1.0, "P")));

        StepVerifier.create(result).verifyComplete();
        verify(repository).save(any());
    }

    @Test
    void handleBookUpdatedSavesDocument() {
        given(repository.save(any())).willReturn(Mono.just(mock("isbn-2")));

        var result =
                publisher
                        .handleBookUpdated(repository)
                        .apply(Flux.just(new BookUpdatedMessage("isbn-2", "T", "A", 1.0, "P")));

        StepVerifier.create(result).verifyComplete();
        verify(repository).save(any());
    }

    @Test
    void handleBookDeletedRemovesDocument() {
        given(repository.deleteByIsbn("isbn-3")).willReturn(Mono.empty());

        var result =
                publisher
                        .handleBookDeleted(repository)
                        .apply(Flux.just(new BookDeletedMessage("isbn-3")));

        StepVerifier.create(result).verifyComplete();
        verify(repository).deleteByIsbn("isbn-3");
    }

    @Test
    void handleBookCreatedPropagatesError() {
        given(repository.save(any())).willReturn(Mono.error(new RuntimeException("es down")));

        var result =
                publisher
                        .handleBookCreated(repository)
                        .apply(Flux.just(new BookCreatedMessage("isbn-4", "T", "A", 1.0, "P")));

        StepVerifier.create(result).expectError(RuntimeException.class).verify();
    }

    private static BookDocument mock(String isbn) {
        return new BookDocument(isbn, "T", "A", 1.0, "P");
    }
}
