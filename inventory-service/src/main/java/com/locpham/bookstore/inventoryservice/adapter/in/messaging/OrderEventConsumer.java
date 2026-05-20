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
                                                "Received order.created for order {}",
                                                message.orderId()))
                        .map(OrderEventConsumer::toReserveRequest)
                        .concatMap(
                                request ->
                                        reserveStockUseCase
                                                .reserveForOrder(request)
                                                .retryWhen(OPTIMISTIC_LOCK_RETRY)
                                                .onErrorResume(
                                                        DataIntegrityViolationException.class,
                                                        e ->
                                                                handleDuplicateReservation(
                                                                        request.orderId(), e)))
                        .doOnNext(
                                decision ->
                                        logger.info(
                                                "Reservation decision for order {}: {}",
                                                decision.orderId(),
                                                decision.status()))
                        .subscribe();
    }

    @Bean
    public Consumer<Flux<OrderCancelledMessage>> releaseStock(
            ReleaseStockUseCase releaseStockUseCase) {
        return flux ->
                flux.flatMap(
                                message -> {
                                    logger.info(
                                            "Received order.cancelled for order {}",
                                            message.orderId());
                                    return releaseStockUseCase
                                            .releaseForOrder(message.orderId())
                                            .thenReturn(message.orderId());
                                })
                        .doOnNext(orderId -> logger.info("Stock released for order {}", orderId))
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
