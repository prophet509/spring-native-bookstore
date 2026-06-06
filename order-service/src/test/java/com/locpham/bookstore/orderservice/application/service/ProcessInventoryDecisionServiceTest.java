package com.locpham.bookstore.orderservice.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.orderservice.application.port.in.ProcessInventoryDecisionUseCase;
import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProcessInventoryDecisionServiceTest {

    @Mock private OrderQueryPort orderQueryPort;
    @Mock private OrderCommandPort orderCommandPort;
    @Mock private OrderEventPublisherPort orderEventPublisherPort;
    @Mock private TransactionalOperator transactionalOperator;

    @InjectMocks private ProcessInventoryDecisionService service;

    @Test
    void reserved_shouldAcceptAndPublishOrderAccepted() {
        var pending = pendingOrder();

        given(orderQueryPort.findById(10L)).willReturn(Mono.just(pending));
        given(transactionalOperator.transactional(any(Mono.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(orderCommandPort.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));
        given(orderEventPublisherPort.publishOrderAccepted(any())).willReturn(Mono.empty());

        StepVerifier.create(
                        service.processDecision(
                                10L, ProcessInventoryDecisionUseCase.DecisionStatus.RESERVED))
                .verifyComplete();

        verify(orderEventPublisherPort).publishOrderAccepted(any());
    }

    @Test
    void rejected_shouldRejectAndNotPublishOrderAccepted() {
        var pending = pendingOrder();

        given(orderQueryPort.findById(11L)).willReturn(Mono.just(pending));
        given(orderCommandPort.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(
                        service.processDecision(
                                11L, ProcessInventoryDecisionUseCase.DecisionStatus.REJECTED))
                .verifyComplete();

        verify(orderEventPublisherPort, never()).publishOrderAccepted(any());
    }

    private static Order pendingOrder() {
        return Order.createPending("123", "Book", 9.99, 1);
    }
}
