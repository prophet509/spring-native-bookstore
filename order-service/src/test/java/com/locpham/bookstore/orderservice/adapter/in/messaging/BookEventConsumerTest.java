package com.locpham.bookstore.orderservice.adapter.in.messaging;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookCreatedMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookDeletedMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.message.BookUpdatedMessage;
import com.locpham.bookstore.orderservice.application.port.out.CatalogBookSnapshotPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class BookEventConsumerTest {

    @Mock private CatalogBookSnapshotPort snapshotPort;

    @InjectMocks private BookEventConsumer consumer;

    @Test
    void handleBookCreatedUpsertsSnapshot() {
        given(snapshotPort.upsert("isbn-1", "Title", 9.99)).willReturn(Mono.just(1L));

        consumer.handleBookCreated(new BookCreatedMessage("isbn-1", "Title", "Author", 9.99, "Pub"))
                .block();

        verify(snapshotPort).upsert("isbn-1", "Title", 9.99);
    }

    @Test
    void handleBookUpdatedUpsertsSnapshot() {
        given(snapshotPort.upsert("isbn-2", "New", 5.0)).willReturn(Mono.just(1L));

        consumer.handleBookUpdated(new BookUpdatedMessage("isbn-2", "New", "Author", 5.0, "Pub"))
                .block();

        verify(snapshotPort).upsert("isbn-2", "New", 5.0);
    }

    @Test
    void handleBookDeletedRemovesSnapshot() {
        given(snapshotPort.deleteByIsbn("isbn-3")).willReturn(Mono.just(1L));

        consumer.handleBookDeleted(new BookDeletedMessage("isbn-3")).block();

        verify(snapshotPort).deleteByIsbn("isbn-3");
    }
}
