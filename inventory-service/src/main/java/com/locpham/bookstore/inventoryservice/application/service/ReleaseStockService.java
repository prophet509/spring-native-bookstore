package com.locpham.bookstore.inventoryservice.application.service;

import com.locpham.bookstore.inventoryservice.application.port.in.ReleaseStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.application.port.out.ReservationPort;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import com.locpham.bookstore.inventoryservice.domain.ReservationStatus;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class ReleaseStockService implements ReleaseStockUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ReleaseStockService.class);

    private final InventoryPort inventoryPort;
    private final ReservationPort reservationPort;

    public ReleaseStockService(InventoryPort inventoryPort, ReservationPort reservationPort) {
        this.inventoryPort = inventoryPort;
        this.reservationPort = reservationPort;
    }

    @Transactional
    @Override
    public Mono<Void> releaseForOrder(Long orderId) {
        return reservationPort
                .findByOrderId(orderId)
                .filter(r -> r.status() == ReservationStatus.RESERVED)
                .collectList()
                .flatMap(
                        reservations -> {
                            if (reservations.isEmpty()) {
                                logger.info(
                                        "No active reservations found for order {} — idempotent no-op",
                                        orderId);
                                return Mono.empty();
                            }

                            List<String> isbns =
                                    reservations.stream()
                                            .map(Reservation::isbn)
                                            .collect(Collectors.toList());

                            return inventoryPort
                                    .findAllByIsbn(isbns)
                                    .collectMap(InventoryItem::isbn)
                                    .flatMap(
                                            inventoryMap -> {
                                                List<InventoryItem> releasedItems =
                                                        reservations.stream()
                                                                .map(
                                                                        r -> {
                                                                            InventoryItem item =
                                                                                    inventoryMap
                                                                                            .get(
                                                                                                    r
                                                                                                            .isbn());
                                                                            if (item == null) {
                                                                                throw new IllegalStateException(
                                                                                        "Inventory not found for ISBN: "
                                                                                                + r
                                                                                                        .isbn());
                                                                            }
                                                                            return item.release(
                                                                                    r.quantity());
                                                                        })
                                                                .collect(Collectors.toList());
                                                return inventoryPort
                                                        .saveAll(releasedItems)
                                                        .collectList();
                                            })
                                    .then(
                                            Mono.defer(
                                                    () -> {
                                                        List<Mono<Reservation>>
                                                                updatedReservations =
                                                                        reservations.stream()
                                                                                .map(
                                                                                        r ->
                                                                                                reservationPort
                                                                                                        .save(
                                                                                                                r
                                                                                                                        .markReleased()))
                                                                                .collect(
                                                                                        Collectors
                                                                                                .toList());
                                                        return Mono.zip(
                                                                        updatedReservations,
                                                                        objects -> objects)
                                                                .then();
                                                    }));
                        })
                .then();
    }
}
