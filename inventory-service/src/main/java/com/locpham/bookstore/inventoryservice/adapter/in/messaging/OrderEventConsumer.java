package com.locpham.bookstore.inventoryservice.adapter.in.messaging;

import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCancelledMessage;
import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCreatedMessage;
import com.locpham.bookstore.inventoryservice.application.port.in.ReleaseStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
public class OrderEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);
    // Under heavy concurrent load every reservation contends on the same inventory row, so
    // optimistic-lock conflicts are frequent. Retry generously with a capped backoff + jitter so
    // losers eventually win without a thundering herd.
    private static final Retry OPTIMISTIC_LOCK_RETRY =
            Retry.backoff(10, Duration.ofMillis(50))
                    .maxBackoff(Duration.ofMillis(500))
                    .jitter(0.5)
                    .filter(throwable -> throwable instanceof OptimisticLockingFailureException);

    private final ReserveStockUseCase reserveStockUseCase;
    private final ReleaseStockUseCase releaseStockUseCase;

    public OrderEventConsumer(
            ReserveStockUseCase reserveStockUseCase, ReleaseStockUseCase releaseStockUseCase) {
        this.reserveStockUseCase = reserveStockUseCase;
        this.releaseStockUseCase = releaseStockUseCase;
    }

    @KafkaListener(
            topics = "${polar.kafka.topics.order-created-events:order-created-events}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}")
    public Mono<Void> reserveStock(OrderCreatedMessage message) {
        logger.info(
                "Received order.created event orderId={} items={}",
                message.orderId(),
                message.items().size());
        return reserveStockUseCase
                .reserveForOrder(toReserveRequest(message))
                .retryWhen(OPTIMISTIC_LOCK_RETRY)
                .doOnSuccess(
                        decision ->
                                logger.info(
                                        "Reservation decision orderId={} status={}",
                                        decision.orderId(),
                                        decision.status()))
                .onErrorResume(
                        DataIntegrityViolationException.class,
                        e -> handleDuplicateReservation(message.orderId(), e))
                .onErrorResume(
                        e -> {
                            logger.error(
                                    "Failed to process reservation for orderId={}",
                                    message.orderId(),
                                    e);
                            return Mono.empty();
                        })
                .then();
    }

    @KafkaListener(
            topics = "${polar.kafka.topics.order-cancelled-events:order-cancelled-events}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}")
    public Mono<Void> releaseStock(OrderCancelledMessage message) {
        logger.info("Received order.cancelled event orderId={}", message.orderId());
        return releaseStockUseCase
                .releaseForOrder(message.orderId())
                .doOnSuccess(
                        v ->
                                logger.info(
                                        "Stock release completed for orderId={}",
                                        message.orderId()))
                .onErrorResume(
                        e -> {
                            logger.error(
                                    "Failed to release stock for orderId={}", message.orderId(), e);
                            return Mono.empty();
                        })
                .then();
    }

    private static ReserveStockUseCase.OrderReserveRequest toReserveRequest(
            OrderCreatedMessage message) {
        return new ReserveStockUseCase.OrderReserveRequest(
                message.orderId(),
                message.items().stream()
                        .map(i -> new ReserveStockUseCase.OrderItem(i.isbn(), i.quantity()))
                        .toList());
    }

    private static Mono<InventoryDecision> handleDuplicateReservation(
            Long orderId, DataIntegrityViolationException e) {
        logger.warn("Duplicate reservation attempt for order {}: {}", orderId, e.getMessage());
        return Mono.just(InventoryDecision.reserved(orderId));
    }
}
