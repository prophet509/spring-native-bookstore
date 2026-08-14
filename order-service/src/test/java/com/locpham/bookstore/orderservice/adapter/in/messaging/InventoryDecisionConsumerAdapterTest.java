package com.locpham.bookstore.orderservice.adapter.in.messaging;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.orderservice.application.port.in.ProcessInventoryDecisionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class InventoryDecisionConsumerAdapterTest {

    @Mock private ProcessInventoryDecisionUseCase processInventoryDecisionUseCase;

    @Test
    void consumeInventoryDecision_shouldDelegateToUseCase() {
        given(
                        processInventoryDecisionUseCase.processDecision(
                                42L, ProcessInventoryDecisionUseCase.DecisionStatus.RESERVED))
                .willReturn(Mono.empty());

        var adapter = new InventoryDecisionConsumerAdapter(processInventoryDecisionUseCase);
        var payload = new InventoryDecisionMessage(42L, "RESERVED", null);

        adapter.handleInventoryDecision(payload).block();

        verify(processInventoryDecisionUseCase)
                .processDecision(42L, ProcessInventoryDecisionUseCase.DecisionStatus.RESERVED);
    }

    @Test
    void consumeInventoryDecision_swallowsErrors() {
        given(
                        processInventoryDecisionUseCase.processDecision(
                                42L, ProcessInventoryDecisionUseCase.DecisionStatus.RESERVED))
                .willReturn(Mono.error(new RuntimeException("boom")));

        var adapter = new InventoryDecisionConsumerAdapter(processInventoryDecisionUseCase);
        var payload = new InventoryDecisionMessage(42L, "RESERVED", null);

        adapter.handleInventoryDecision(payload).block();

        verify(processInventoryDecisionUseCase)
                .processDecision(42L, ProcessInventoryDecisionUseCase.DecisionStatus.RESERVED);
    }
}
