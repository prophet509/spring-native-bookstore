package com.locpham.bookstore.inventoryservice.application.service;

import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryEventPublisher;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.application.port.out.ReservationPort;
import com.locpham.bookstore.inventoryservice.domain.InsufficientStockException;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ReserveStockService implements ReserveStockUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ReserveStockService.class);

    private final InventoryPort inventoryPort;
    private final ReservationPort reservationPort;
    private final InventoryEventPublisher eventPublisher;

    public ReserveStockService(
            InventoryPort inventoryPort,
            ReservationPort reservationPort,
            InventoryEventPublisher eventPublisher) {
        this.inventoryPort = inventoryPort;
        this.reservationPort = reservationPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<InventoryDecision> reserveForOrder(OrderReserveRequest request) {
        List<String> isbns =
                request.items().stream().map(OrderItem::isbn).collect(Collectors.toList());

        logger.info(
                "Processing reservation for orderId={} items={}",
                request.orderId(),
                request.items().stream()
                        .map(i -> i.isbn() + "x" + i.quantity())
                        .collect(Collectors.joining(",")));

        return reservationPort
                .findByOrderId(request.orderId())
                .hasElements()
                .flatMap(
                        alreadyReserved -> {
                            if (alreadyReserved) {
                                logger.info(
                                        "Reservation already exists for orderId={} — idempotent return",
                                        request.orderId());
                                return Mono.just(InventoryDecision.reserved(request.orderId()));
                            }
                            return reserveAvailableStock(request, isbns);
                        })
                .onErrorResume(
                        InsufficientStockException.class,
                        e -> {
                            logger.warn(
                                    "Stock reservation REJECTED for orderId={}: {}",
                                    request.orderId(),
                                    e.getMessage());
                            InventoryDecision rejected =
                                    InventoryDecision.rejected(request.orderId(), e.getMessage());
                            return eventPublisher
                                    .publishInventoryDecision(rejected)
                                    .thenReturn(rejected);
                        });
    }

    private Mono<InventoryDecision> reserveAvailableStock(
            OrderReserveRequest request, List<String> isbns) {
        logger.debug(
                "Attempting stock reservation for orderId={} isbns={}", request.orderId(), isbns);
        return inventoryPort
                .findAllByIsbn(isbns)
                .collectMap(InventoryItem::isbn)
                .flatMap(
                        inventoryMap -> {
                            try {
                                List<InventoryItem> reservedItems =
                                        request.items().stream()
                                                .map(
                                                        item -> {
                                                            InventoryItem inventory =
                                                                    inventoryMap.get(item.isbn());
                                                            if (inventory == null) {
                                                                throw new InsufficientStockException(
                                                                        "No inventory found for ISBN: "
                                                                                + item.isbn());
                                                            }
                                                            logger.debug(
                                                                    "Reserving isbn={} requested={} available={} version={}",
                                                                    item.isbn(),
                                                                    item.quantity(),
                                                                    inventory.availableQuantity(),
                                                                    inventory.version());
                                                            return inventory.reserve(
                                                                    item.quantity());
                                                        })
                                                .collect(Collectors.toList());
                                return Mono.just(reservedItems);
                            } catch (InsufficientStockException e) {
                                return Mono.error(e);
                            }
                        })
                .flatMap(
                        reservedItems ->
                                inventoryPort
                                        .saveAll(reservedItems)
                                        .collectList()
                                        .doOnSuccess(
                                                savedItems ->
                                                        logger.info(
                                                                "Inventory updated for orderId={} — {} items reserved",
                                                                request.orderId(),
                                                                savedItems.size()))
                                        .flatMap(
                                                savedItems -> {
                                                    List<Mono<Reservation>> reservationMonos =
                                                            request.items().stream()
                                                                    .map(
                                                                            item ->
                                                                                    reservationPort
                                                                                            .save(
                                                                                                    Reservation
                                                                                                            .create(
                                                                                                                    request
                                                                                                                            .orderId(),
                                                                                                                    item
                                                                                                                            .isbn(),
                                                                                                                    item
                                                                                                                            .quantity())))
                                                                    .collect(Collectors.toList());
                                                    return Mono.zip(
                                                                    reservationMonos,
                                                                    objects ->
                                                                            InventoryDecision
                                                                                    .reserved(
                                                                                            request
                                                                                                    .orderId()))
                                                            .doOnSuccess(
                                                                    decision ->
                                                                            logger.info(
                                                                                    "Reservation CONFIRMED for orderId={}",
                                                                                    request
                                                                                            .orderId()))
                                                            .flatMap(
                                                                    decision ->
                                                                            eventPublisher
                                                                                    .publishInventoryDecision(
                                                                                            decision)
                                                                                    .thenReturn(
                                                                                            decision));
                                                }));
    }
}
