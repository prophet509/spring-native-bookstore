package com.locpham.bookstore.inventoryservice.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.application.port.out.ReservationPort;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import com.locpham.bookstore.inventoryservice.domain.ReservationStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReleaseStockServiceTest {

    @Mock private InventoryPort inventoryPort;

    @Mock private ReservationPort reservationPort;

    @InjectMocks private ReleaseStockService releaseStockService;

    @Test
    void releaseForOrder_shouldReleaseWhenReservationsExist() {
        Long orderId = 1L;
        Reservation reservation =
                new Reservation(UUID.randomUUID(), orderId, "123", 2, ReservationStatus.RESERVED);
        InventoryItem item = new InventoryItem(1L, "123", 8, 2, 0);

        given(reservationPort.findByOrderId(orderId)).willReturn(Flux.just(reservation));
        given(inventoryPort.findAllByIsbn(List.of("123"))).willReturn(Flux.just(item));
        given(inventoryPort.saveAll(any())).willReturn(Flux.just(item.release(2)));
        given(reservationPort.save(any())).willAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(releaseStockService.releaseForOrder(orderId)).verifyComplete();
    }

    @Test
    void releaseForOrder_shouldBeIdempotentWhenNoReservations() {
        Long orderId = 2L;

        given(reservationPort.findByOrderId(orderId)).willReturn(Flux.empty());

        StepVerifier.create(releaseStockService.releaseForOrder(orderId)).verifyComplete();

        verifyNoInteractions(inventoryPort);
    }
}
