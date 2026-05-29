package com.locpham.bookstore.inventoryservice.application.service;

import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryEventPublisher;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.application.port.out.ReservationPort;
import com.locpham.bookstore.inventoryservice.domain.InsufficientStockException;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ReserveStockService implements ReserveStockUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ReserveStockService.class);
    private static final String IDEM_PREFIX = "inv:idem:";

    private final InventoryPort inventoryPort;
    private final ReservationPort reservationPort;
    private final InventoryEventPublisher eventPublisher;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public ReserveStockService(
            InventoryPort inventoryPort,
            ReservationPort reservationPort,
            InventoryEventPublisher eventPublisher,
            ReactiveRedisTemplate<String, String> redisTemplate) {
        this.inventoryPort = inventoryPort;
        this.reservationPort = reservationPort;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<InventoryDecision> reserveForOrder(OrderReserveRequest request) {
        List<String> isbns =
                request.items().stream().map(OrderItem::isbn).collect(Collectors.toList());

        logger.atInfo()
                .addKeyValue("orderId", request.orderId())
                .log(
                        () ->
                                "Processing reservation for items: "
                                        + request.items().stream()
                                                .map(i -> i.isbn() + "x" + i.quantity())
                                                .collect(Collectors.joining(",")));

        return checkRedisIdempotency(request.orderId())
                .flatMap(
                        alreadyReserved -> {
                            if (alreadyReserved) {
                                return Mono.just(InventoryDecision.reserved(request.orderId()));
                            }
                            return doReserve(request, isbns);
                        });
    }

    private Mono<Boolean> checkRedisIdempotency(Long orderId) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(IDEM_PREFIX + orderId, "1", Duration.ofHours(24))
                .onErrorResume(
                        e -> {
                            logger.warn(
                                    "Redis idempotency check failed for orderId={}, fallback to DB",
                                    orderId);
                            return Mono.just(false);
                        });
    }

    private Mono<InventoryDecision> doReserve(OrderReserveRequest request, List<String> isbns) {
        return reservationPort
                .findByOrderId(request.orderId())
                .hasElements()
                .flatMap(
                        alreadyReserved -> {
                            if (alreadyReserved) {
                                logger.atInfo()
                                        .addKeyValue("orderId", request.orderId())
                                        .log("Reservation already exists — idempotent return");
                                return Mono.just(InventoryDecision.reserved(request.orderId()));
                            }
                            return reserveAvailableStock(request, isbns);
                        })
                .onErrorResume(
                        InsufficientStockException.class,
                        e -> {
                            logger.atWarn()
                                    .addKeyValue("orderId", request.orderId())
                                    .log(() -> "Stock reservation REJECTED: " + e.getMessage());
                            InventoryDecision rejected =
                                    InventoryDecision.rejected(request.orderId(), e.getMessage());
                            return eventPublisher
                                    .publishInventoryDecision(rejected)
                                    .thenReturn(rejected);
                        });
    }

    private Mono<InventoryDecision> reserveAvailableStock(
            OrderReserveRequest request, List<String> isbns) {
        logger.atDebug()
                .addKeyValue("orderId", request.orderId())
                .log(() -> "Attempting stock reservation for isbns: " + isbns);
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
                                                            logger.atDebug()
                                                                    .addKeyValue(
                                                                            "isbn", item.isbn())
                                                                    .addKeyValue(
                                                                            "requested",
                                                                            item.quantity())
                                                                    .addKeyValue(
                                                                            "available",
                                                                            inventory
                                                                                    .availableQuantity())
                                                                    .addKeyValue(
                                                                            "version",
                                                                            inventory.version())
                                                                    .log("Reserving inventory");
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
                                                        logger.atInfo()
                                                                .addKeyValue(
                                                                        "orderId",
                                                                        request.orderId())
                                                                .log(
                                                                        () ->
                                                                                "Inventory updated — "
                                                                                        + savedItems
                                                                                                .size()
                                                                                        + " items reserved"))
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
                                                                            logger.atInfo()
                                                                                    .addKeyValue(
                                                                                            "orderId",
                                                                                            request
                                                                                                    .orderId())
                                                                                    .log(
                                                                                            "Reservation CONFIRMED"))
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
