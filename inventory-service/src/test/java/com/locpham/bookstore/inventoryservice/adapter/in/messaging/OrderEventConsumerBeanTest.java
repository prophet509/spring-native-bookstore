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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerBeanTest {

    @Mock private ReserveStockUseCase reserveStockUseCase;
    @Mock private ReleaseStockUseCase releaseStockUseCase;

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(reserveStockUseCase, releaseStockUseCase);
    }

    @Test
    void reserveStockDelegatesToUseCase() {
        given(reserveStockUseCase.reserveForOrder(any()))
                .willReturn(Mono.just(InventoryDecision.reserved(1L)));

        consumer.reserveStock(
                        new OrderCreatedMessage(
                                1L, List.of(new OrderCreatedMessage.OrderItem("isbn", 2))))
                .block();

        verify(reserveStockUseCase).reserveForOrder(any());
    }

    @Test
    void reserveStockSwallowsErrors() {
        given(reserveStockUseCase.reserveForOrder(any()))
                .willReturn(Mono.error(new RuntimeException("boom")));

        consumer.reserveStock(
                        new OrderCreatedMessage(
                                2L, List.of(new OrderCreatedMessage.OrderItem("isbn", 1))))
                .block();

        verify(reserveStockUseCase).reserveForOrder(any());
    }

    @Test
    void releaseStockDelegatesToUseCase() {
        given(releaseStockUseCase.releaseForOrder(3L)).willReturn(Mono.empty());

        consumer.releaseStock(new OrderCancelledMessage(3L)).block();

        verify(releaseStockUseCase).releaseForOrder(3L);
    }

    @Test
    void releaseStockSwallowsErrors() {
        given(releaseStockUseCase.releaseForOrder(4L))
                .willReturn(Mono.error(new RuntimeException("boom")));

        consumer.releaseStock(new OrderCancelledMessage(4L)).block();

        verify(releaseStockUseCase).releaseForOrder(4L);
    }
}
