package com.locpham.bookstore.orderservice.application.service;

import com.locpham.bookstore.orderservice.application.command.MarkOrderDispatchedCommand;
import com.locpham.bookstore.orderservice.application.port.in.MarkOrderDispatchedUseCase;
import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class MarkOrderDispatchedService implements MarkOrderDispatchedUseCase {

    private static final Logger log = LoggerFactory.getLogger(MarkOrderDispatchedService.class);

    private final OrderQueryPort orderQueryPort;
    private final OrderCommandPort orderCommandPort;

    public MarkOrderDispatchedService(
            OrderQueryPort orderQueryPort, OrderCommandPort orderCommandPort) {
        this.orderQueryPort = orderQueryPort;
        this.orderCommandPort = orderCommandPort;
    }

    @Transactional
    @Override
    public Mono<Order> markOrderDispatched(MarkOrderDispatchedCommand command) {
        log.debug("Marking order dispatched orderId={}", command.orderId());
        return orderQueryPort
                .findById(command.orderId())
                .map(Order::markDispatched)
                .flatMap(orderCommandPort::save)
                .doOnSuccess(
                        order -> {
                            if (order != null) {
                                log.info("Order dispatched orderId={}", order.id());
                            } else {
                                log.warn(
                                        "Order not found for dispatch orderId={}",
                                        command.orderId());
                            }
                        })
                .doOnError(
                        e ->
                                log.error(
                                        "Failed to mark order dispatched orderId={} error={}",
                                        command.orderId(),
                                        e.getMessage(),
                                        e));
    }
}
