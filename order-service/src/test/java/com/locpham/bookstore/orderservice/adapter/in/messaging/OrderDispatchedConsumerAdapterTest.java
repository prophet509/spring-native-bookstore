package com.locpham.bookstore.orderservice.adapter.in.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.orderservice.application.command.MarkOrderDispatchedCommand;
import com.locpham.bookstore.orderservice.application.port.in.MarkOrderDispatchedUseCase;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OrderDispatchedConsumerAdapterTest {

    @Mock private MarkOrderDispatchedUseCase markOrderDispatchedUseCase;

    @Test
    void dispatchOrderDelegatesToUseCase() {
        var order = Order.createAccepted("1234567890", "Title", 9.99, 1);
        given(markOrderDispatchedUseCase.markOrderDispatched(any())).willReturn(Mono.just(order));

        new OrderDispatchedConsumerAdapter(markOrderDispatchedUseCase)
                .dispatchOrder(new OrderDispatchedMessage(7L))
                .block();

        verify(markOrderDispatchedUseCase).markOrderDispatched(new MarkOrderDispatchedCommand(7L));
    }

    @Test
    void dispatchOrderSwallowsErrors() {
        given(markOrderDispatchedUseCase.markOrderDispatched(any()))
                .willReturn(Mono.error(new RuntimeException("boom")));

        new OrderDispatchedConsumerAdapter(markOrderDispatchedUseCase)
                .dispatchOrder(new OrderDispatchedMessage(8L))
                .block();

        verify(markOrderDispatchedUseCase).markOrderDispatched(any());
    }
}
