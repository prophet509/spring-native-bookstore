package com.locpham.bookstore.orderservice.application.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.orderservice.application.command.SubmitOrderCommand;
import com.locpham.bookstore.orderservice.application.port.out.CatalogBookPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import com.locpham.bookstore.orderservice.domain.model.Order;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class SubmitOrderServiceTest {

    @Mock private CatalogBookPort catalogBookPort;
    @Mock private OrderCommandPort orderCommandPort;
    @Mock private OrderEventPublisherPort eventPublisher;
    @Mock private TransactionalOperator transactionalOperator;

    @InjectMocks private SubmitOrderService submitOrderService;

    @SuppressWarnings("unchecked")
    private void passThroughTransaction() {
        given(transactionalOperator.transactional(any(Mono.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void whenBookSnapshotExists_shouldSubmitOrder() {
        var isbn = "1234567890";
        var createdBy = "isabelle";
        var book = new BookSnapshot(isbn, "Title", 9.99);
        var command = new SubmitOrderCommand(isbn, 2, createdBy);

        passThroughTransaction();
        given(catalogBookPort.loadBook(isbn)).willReturn(Mono.just(book));
        given(orderCommandPort.save(any(Order.class)))
                .willAnswer(inv -> Mono.just(inv.getArgument(0)));
        given(eventPublisher.publishOrderCreated(any(Order.class))).willReturn(Mono.empty());

        StepVerifier.create(submitOrderService.submitOrder(command))
                .assertNext(
                        order -> {
                            assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
                            assertThat(order.quantity()).isEqualTo(command.quantity());
                            assertThat(order.book().isbn()).isEqualTo(command.isbn());
                            assertThat(order.audit().createdBy()).isEqualTo(createdBy);
                        })
                .verifyComplete();

        verify(eventPublisher).publishOrderCreated(any(Order.class));
    }

    @Test
    void whenSnapshotReturnsEmpty_shouldBuildRejectedOrder() {
        var isbn = "1234567890";
        var createdBy = "isabelle";
        var command = new SubmitOrderCommand(isbn, 2, createdBy);

        passThroughTransaction();
        given(catalogBookPort.loadBook(isbn)).willReturn(Mono.empty());
        given(orderCommandPort.save(any(Order.class)))
                .willAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(submitOrderService.submitOrder(command))
                .assertNext(
                        order -> {
                            assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
                            assertThat(order.book().isbn()).isEqualTo(isbn);
                        })
                .verifyComplete();

        verify(eventPublisher, never()).publishOrderCreated(any(Order.class));
    }

    @Test
    void whenSnapshotReturnsEmpty_shouldNotCallHttpCatalog() {
        var isbn = "1234567890";
        var createdBy = "isabelle";
        var command = new SubmitOrderCommand(isbn, 2, createdBy);

        passThroughTransaction();
        given(catalogBookPort.loadBook(isbn)).willReturn(Mono.empty());
        given(orderCommandPort.save(any(Order.class)))
                .willAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(submitOrderService.submitOrder(command))
                .expectNextCount(1)
                .verifyComplete();
    }
}
