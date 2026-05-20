package com.locpham.bookstore.inventoryservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryEventPublisher;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.application.port.out.ReservationPort;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReserveStockServiceTest {

    @Mock private InventoryPort inventoryPort;

    @Mock private ReservationPort reservationPort;

    @Mock private InventoryEventPublisher eventPublisher;

    @InjectMocks private ReserveStockService reserveStockService;

    @Test
    void reserveForOrder_shouldReserveAllItemsAndPublishReserved() {
        Long orderId = 1L;
        var request =
                new ReserveStockUseCase.OrderReserveRequest(
                        orderId,
                        List.of(
                                new ReserveStockUseCase.OrderItem("123", 2),
                                new ReserveStockUseCase.OrderItem("456", 3)));

        InventoryItem item1 = new InventoryItem(1L, "123", 10, 0, 0);
        InventoryItem item2 = new InventoryItem(2L, "456", 5, 0, 0);

        given(reservationPort.findByOrderId(orderId)).willReturn(Flux.empty());
        given(inventoryPort.findAllByIsbn(List.of("123", "456")))
                .willReturn(Flux.just(item1, item2));
        given(inventoryPort.saveAll(any()))
                .willReturn(Flux.just(item1.reserve(2), item2.reserve(3)));
        given(reservationPort.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));
        given(eventPublisher.publishInventoryDecision(any())).willReturn(Mono.empty());

        StepVerifier.create(reserveStockService.reserveForOrder(request))
                .assertNext(
                        decision -> {
                            assertThat(decision.orderId()).isEqualTo(orderId);
                            assertThat(decision.status())
                                    .isEqualTo(InventoryDecision.DecisionStatus.RESERVED);
                        })
                .verifyComplete();
    }

    @Test
    void reserveForOrder_shouldRejectWhenInsufficientStock() {
        Long orderId = 2L;
        var request =
                new ReserveStockUseCase.OrderReserveRequest(
                        orderId, List.of(new ReserveStockUseCase.OrderItem("123", 15)));

        InventoryItem item = new InventoryItem(1L, "123", 10, 0, 0);

        given(reservationPort.findByOrderId(orderId)).willReturn(Flux.empty());
        given(inventoryPort.findAllByIsbn(List.of("123"))).willReturn(Flux.just(item));
        given(eventPublisher.publishInventoryDecision(any())).willReturn(Mono.empty());

        StepVerifier.create(reserveStockService.reserveForOrder(request))
                .assertNext(
                        decision -> {
                            assertThat(decision.status())
                                    .isEqualTo(InventoryDecision.DecisionStatus.REJECTED);
                            assertThat(decision.reason()).contains("Insufficient stock");
                        })
                .verifyComplete();
    }

    @Test
    void reserveForOrder_shouldRejectWhenItemNotFound() {
        Long orderId = 3L;
        var request =
                new ReserveStockUseCase.OrderReserveRequest(
                        orderId, List.of(new ReserveStockUseCase.OrderItem("999", 1)));

        given(reservationPort.findByOrderId(orderId)).willReturn(Flux.empty());
        given(inventoryPort.findAllByIsbn(List.of("999"))).willReturn(Flux.empty());
        given(eventPublisher.publishInventoryDecision(any())).willReturn(Mono.empty());

        StepVerifier.create(reserveStockService.reserveForOrder(request))
                .assertNext(
                        decision -> {
                            assertThat(decision.status())
                                    .isEqualTo(InventoryDecision.DecisionStatus.REJECTED);
                            assertThat(decision.reason()).contains("No inventory found");
                        })
                .verifyComplete();
    }

    @Test
    void reserveForOrder_whenAlreadyReserved_shouldNotReserveAgain() {
        Long orderId = 4L;
        var request =
                new ReserveStockUseCase.OrderReserveRequest(
                        orderId, List.of(new ReserveStockUseCase.OrderItem("123", 2)));

        given(reservationPort.findByOrderId(orderId))
                .willReturn(Flux.just(Reservation.create(orderId, "123", 2)));

        StepVerifier.create(reserveStockService.reserveForOrder(request))
                .assertNext(
                        decision -> {
                            assertThat(decision.orderId()).isEqualTo(orderId);
                            assertThat(decision.status())
                                    .isEqualTo(InventoryDecision.DecisionStatus.RESERVED);
                        })
                .verifyComplete();

        verify(inventoryPort, never()).findAllByIsbn(any());
    }
}
