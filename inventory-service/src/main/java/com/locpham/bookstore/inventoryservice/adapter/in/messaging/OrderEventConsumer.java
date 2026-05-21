package com.locpham.bookstore.inventoryservice.adapter.in.messaging;

import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCancelledMessage;
import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCreatedMessage;
import com.locpham.bookstore.inventoryservice.application.port.in.ReleaseStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import java.time.Duration;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Configuration
public class OrderEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);
    private static final Retry OPTIMISTIC_LOCK_RETRY =
            Retry.backoff(3, Duration.ofMillis(100))
                    .filter(throwable -> throwable instanceof OptimisticLockingFailureException);

    @Bean
    public Consumer<Flux<OrderCreatedMessage>> reserveStock(
            ReserveStockUseCase reserveStockUseCase) {
        return flux ->
                flux.doOnNext(
                                message ->
                                        logger.info(
                                                "Received order.created event orderId={} items={}",
                                                message.orderId(),
                                                message.items().size()))
                        .map(OrderEventConsumer::toReserveRequest)
                        .concatMap(
                                request ->
                                        reserveStockUseCase
                                                .reserveForOrder(request)
                                                .doOnSubscribe(
                                                        subscription ->
                                                                logger.info(
                                                                        "Reservation processing started orderId={} items={}",
                                                                        request.orderId(),
                                                                        request.items().size()))
                                                .doOnSuccess(
                                                        decision ->
                                                                logger.info(
                                                                        "Reservation processing completed orderId={} status={}",
                                                                        decision.orderId(),
                                                                        decision.status()))
                                                .retryWhen(OPTIMISTIC_LOCK_RETRY)
                                                .doOnError(
                                                        OptimisticLockingFailureException.class,
                                                        e ->
                                                                logger.error(
                                                                        "Optimistic lock retries exhausted for orderId={}",
                                                                        request.orderId(),
                                                                        e))
                                                .onErrorResume(
                                                        DataIntegrityViolationException.class,
                                                        e ->
                                                                handleDuplicateReservation(
                                                                        request.orderId(), e)))
                        .doOnNext(
                                decision ->
                                        logger.info(
                                                "Reservation decision orderId={} status={}",
                                                decision.orderId(),
                                                decision.status()))
                        .doOnError(
                                e -> logger.error("Unexpected error in reserveStock consumer", e))
                        .subscribe();
    }

    @Bean
    public Consumer<Flux<OrderCancelledMessage>> releaseStock(
            ReleaseStockUseCase releaseStockUseCase) {
        return flux ->
                flux.flatMap(
                                message -> {
                                    logger.info(
                                            "Received order.cancelled event orderId={}",
                                            message.orderId());
                                    return releaseStockUseCase
                                            .releaseForOrder(message.orderId())
                                            .thenReturn(message.orderId());
                                })
                        .doOnNext(
                                orderId ->
                                        logger.info(
                                                "Stock release completed for orderId={}", orderId))
                        .doOnError(
                                e -> logger.error("Unexpected error in releaseStock consumer", e))
                        .subscribe();
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
