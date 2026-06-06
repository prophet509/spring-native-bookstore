package com.locpham.bookstore.orderservice.adapter.out.snapshot;

import static org.mockito.BDDMockito.given;

import com.locpham.bookstore.orderservice.application.port.out.CatalogBookSnapshotPort;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SnapshotBookAdapterTest {

    @Mock private CatalogBookSnapshotPort snapshotPort;

    @InjectMocks private SnapshotBookAdapter adapter;

    @Test
    void loadBookReturnsSnapshotWhenPresent() {
        var snapshot = new BookSnapshot("1234567890", "Title", 9.99);
        given(snapshotPort.findByIsbn("1234567890")).willReturn(Mono.just(snapshot));

        StepVerifier.create(adapter.loadBook("1234567890")).expectNext(snapshot).verifyComplete();
    }

    @Test
    void loadBookCompletesEmptyWhenMissing() {
        given(snapshotPort.findByIsbn("404")).willReturn(Mono.empty());

        StepVerifier.create(adapter.loadBook("404")).verifyComplete();
    }
}
