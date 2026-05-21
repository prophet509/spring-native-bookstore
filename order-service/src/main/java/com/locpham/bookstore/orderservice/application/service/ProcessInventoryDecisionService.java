package com.locpham.bookstore.orderservice.application.service;

import com.locpham.bookstore.orderservice.application.port.in.ProcessInventoryDecisionUseCase;
import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ProcessInventoryDecisionService implements ProcessInventoryDecisionUseCase {

    private static final Logger log =
            LoggerFactory.getLogger(ProcessInventoryDecisionService.class);

    private final OrderQueryPort orderQueryPort;
    private final OrderCommandPort orderCommandPort;
    private final OrderEventPublisherPort orderEventPublisherPort;

    public ProcessInventoryDecisionService(
            OrderQueryPort orderQueryPort,
            OrderCommandPort orderCommandPort,
            OrderEventPublisherPort orderEventPublisherPort) {
        this.orderQueryPort = orderQueryPort;
        this.orderCommandPort = orderCommandPort;
        this.orderEventPublisherPort = orderEventPublisherPort;
    }

    @Override
    public Mono<Void> processDecision(Long orderId, DecisionStatus status) {
        log.debug("Processing inventory decision orderId={} status={}", orderId, status);
        return orderQueryPort
                .findById(orderId)
                .switchIfEmpty(
                        Mono.fromRunnable(
                                        () ->
                                                log.warn(
                                                        "Inventory decision for missing order ignored orderId={}",
                                                        orderId))
                                .then(Mono.empty()))
                .flatMap(
                        order -> {
                            if (order.status() != OrderStatus.PENDING) {
                                log.info(
                                        "Order already processed, ignoring duplicate orderId={} currentStatus={}",
                                        orderId,
                                        order.status());
                                return Mono.empty();
                            }

                            return switch (status) {
                                case RESERVED -> orderCommandPort
                                        .save(order.accept())
                                        .doOnSuccess(
                                                o ->
                                                        log.info(
                                                                "Order accepted orderId={} status={}",
                                                                orderId,
                                                                o.status()))
                                        .flatMap(orderEventPublisherPort::publishOrderAccepted)
                                        .then();
                                case REJECTED -> orderCommandPort
                                        .save(order.reject())
                                        .doOnSuccess(
                                                o ->
                                                        log.info(
                                                                "Order rejected by inventory orderId={}",
                                                                orderId))
                                        .then();
                            };
                        })
                .doOnError(
                        e ->
                                log.error(
                                        "Failed to process inventory decision orderId={} error={}",
                                        orderId,
                                        e.getMessage(),
                                        e));
    }
}
