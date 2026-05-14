package com.locpham.bookstore.orderservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.orderservice.application.command.MarkOrderDispatchedCommand;
import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.domain.exception.IllegalOrderException;
import com.locpham.bookstore.orderservice.domain.model.Order;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class MarkOrderDispatchedServiceTest {

    private static final Long ORDER_ID = 1L;

    @Mock private OrderQueryPort orderQueryPort;

    @Mock private OrderCommandPort orderCommandPort;

    @InjectMocks private MarkOrderDispatchedService markOrderDispatchedService;

    @Nested
    class WhenOrderExists {

        @Test
        void whenOrderIsAccepted_shouldMarkAsDispatchedAndSave() {
            var command = new MarkOrderDispatchedCommand(ORDER_ID);
            var acceptedOrder = Order.createAccepted("1234567890", "Title", 9.99, 2);

            given(orderQueryPort.findById(ORDER_ID)).willReturn(Mono.just(acceptedOrder));
            given(orderCommandPort.save(any(Order.class)))
                    .willAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(markOrderDispatchedService.markOrderDispatched(command))
                    .assertNext(
                            order -> {
                                assertThat(order.status()).isEqualTo(OrderStatus.DISPATCHED);
                                assertThat(order.version()).isEqualTo(1);
                                assertThat(order.id()).isEqualTo(acceptedOrder.id());
                            })
                    .verifyComplete();

            verify(orderCommandPort).save(any(Order.class));
        }
    }

    @Nested
    class WhenOrderDoesNotExist {

        @Test
        void shouldReturnEmpty() {
            var command = new MarkOrderDispatchedCommand(ORDER_ID);

            given(orderQueryPort.findById(ORDER_ID)).willReturn(Mono.empty());

            StepVerifier.create(markOrderDispatchedService.markOrderDispatched(command))
                    .verifyComplete();
        }
    }

    @Nested
    class WhenOrderIsNotInAcceptedState {

        @Test
        void whenOrderIsPending_shouldPropagateError() {
            var command = new MarkOrderDispatchedCommand(ORDER_ID);
            var pendingOrder = Order.createPending("1234567890", "Title", 9.99, 2);

            given(orderQueryPort.findById(ORDER_ID)).willReturn(Mono.just(pendingOrder));

            StepVerifier.create(markOrderDispatchedService.markOrderDispatched(command))
                    .expectErrorMatches(
                            throwable ->
                                    throwable instanceof IllegalOrderException
                                            && throwable
                                                    .getMessage()
                                                    .equals(
                                                            "Order must be ACCEPTED to mark as dispatched"))
                    .verify();
        }

        @Test
        void whenOrderIsRejected_shouldPropagateError() {
            var command = new MarkOrderDispatchedCommand(ORDER_ID);
            var rejectedOrder = Order.createRejected("1234567890", "Title", 9.99, 2);

            given(orderQueryPort.findById(ORDER_ID)).willReturn(Mono.just(rejectedOrder));

            StepVerifier.create(markOrderDispatchedService.markOrderDispatched(command))
                    .expectErrorMatches(
                            throwable ->
                                    throwable instanceof IllegalOrderException
                                            && throwable
                                                    .getMessage()
                                                    .equals(
                                                            "Order must be ACCEPTED to mark as dispatched"))
                    .verify();
        }

        @Test
        void whenOrderIsAlreadyDispatched_shouldPropagateError() {
            var command = new MarkOrderDispatchedCommand(ORDER_ID);
            var acceptedOrder = Order.createAccepted("1234567890", "Title", 9.99, 2);
            var dispatchedOrder = acceptedOrder.markDispatched();

            given(orderQueryPort.findById(ORDER_ID)).willReturn(Mono.just(dispatchedOrder));

            StepVerifier.create(markOrderDispatchedService.markOrderDispatched(command))
                    .expectErrorMatches(
                            throwable ->
                                    throwable instanceof IllegalOrderException
                                            && throwable
                                                    .getMessage()
                                                    .equals(
                                                            "Order must be ACCEPTED to mark as dispatched"))
                    .verify();
        }
    }
}
