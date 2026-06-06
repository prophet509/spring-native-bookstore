package com.locpham.bookstore.inventoryservice.adapter.in.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCancelledMessage;
import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCreatedMessage;
import com.locpham.bookstore.inventoryservice.application.port.in.ReleaseStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerBeanTest {

    @Mock private ReserveStockUseCase reserveStockUseCase;
    @Mock private ReleaseStockUseCase releaseStockUseCase;

    private final OrderEventConsumer consumer = new OrderEventConsumer();

    @Test
    void reserveStockDelegatesToUseCase() {
        given(reserveStockUseCase.reserveForOrder(any()))
                .willReturn(Mono.just(InventoryDecision.reserved(1L)));

        consumer.reserveStock(reserveStockUseCase)
                .accept(
                        reactor.core.publisher.Flux.just(
                                new OrderCreatedMessage(
                                        1L,
                                        List.of(new OrderCreatedMessage.OrderItem("isbn", 2)))));

        verify(reserveStockUseCase).reserveForOrder(any());
    }

    @Test
    void reserveStockSwallowsErrors() {
        given(reserveStockUseCase.reserveForOrder(any()))
                .willReturn(Mono.error(new RuntimeException("boom")));

        consumer.reserveStock(reserveStockUseCase)
                .accept(
                        reactor.core.publisher.Flux.just(
                                new OrderCreatedMessage(
                                        2L,
                                        List.of(new OrderCreatedMessage.OrderItem("isbn", 1)))));

        verify(reserveStockUseCase).reserveForOrder(any());
    }

    @Test
    void releaseStockDelegatesToUseCase() {
        given(releaseStockUseCase.releaseForOrder(3L)).willReturn(Mono.empty());

        consumer.releaseStock(releaseStockUseCase)
                .accept(reactor.core.publisher.Flux.just(new OrderCancelledMessage(3L)));

        verify(releaseStockUseCase).releaseForOrder(3L);
    }

    @Test
    void releaseStockSwallowsErrors() {
        given(releaseStockUseCase.releaseForOrder(4L))
                .willReturn(Mono.error(new RuntimeException("boom")));

        consumer.releaseStock(releaseStockUseCase)
                .accept(reactor.core.publisher.Flux.just(new OrderCancelledMessage(4L)));

        verify(releaseStockUseCase).releaseForOrder(4L);
    }
}
