package com.locpham.bookstore.orderservice.application.service;

import com.locpham.bookstore.orderservice.application.command.SubmitOrderCommand;
import com.locpham.bookstore.orderservice.application.port.in.SubmitOrderUseCase;
import com.locpham.bookstore.orderservice.application.port.out.CatalogBookPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.domain.model.BookSnapshot;
import com.locpham.bookstore.orderservice.domain.model.Order;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
public class SubmitOrderService implements SubmitOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitOrderService.class);

    private final CatalogBookPort catalogBookPort;
    private final OrderCommandPort orderCommandPort;
    private final OrderEventPublisherPort eventPublisher;

    public SubmitOrderService(
            CatalogBookPort catalogBookPort,
            OrderCommandPort orderCommandPort,
            OrderEventPublisherPort eventPublisher) {
        this.catalogBookPort = catalogBookPort;
        this.orderCommandPort = orderCommandPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @Override
    public Mono<Order> submitOrder(SubmitOrderCommand command) {
        log.debug(
                "Submitting order isbn={} quantity={} user={}",
                command.isbn(),
                command.quantity(),
                command.createdBy());
        return catalogBookPort
                .loadBook(command.isbn())
                .map(book -> buildPendingOrder(book, command.quantity(), command.createdBy()))
                .switchIfEmpty(
                        Mono.just(
                                buildRejectedOrder(
                                        command.isbn(), command.quantity(), command.createdBy())))
                .flatMap(orderCommandPort::save)
                .doOnNext(
                        order -> {
                            if (order.status() == OrderStatus.PENDING) {
                                log.info(
                                        "Order submitted orderId={} isbn={} status=PENDING",
                                        order.id(),
                                        command.isbn());
                                eventPublisher.publishOrderCreated(order).subscribe();
                            } else {
                                log.info(
                                        "Order rejected orderId={} isbn={} status={}",
                                        order.id(),
                                        command.isbn(),
                                        order.status());
                            }
                        })
                .doOnError(
                        e ->
                                log.error(
                                        "Failed to submit order isbn={} error={}",
                                        command.isbn(),
                                        e.getMessage(),
                                        e));
    }

    private Order buildRejectedOrder(String isbn, int quantity, String createdBy) {
        return Order.createRejected(isbn, null, 0.0, quantity, createdBy);
    }

    private Order buildPendingOrder(BookSnapshot book, int quantity, String createdBy) {
        return Order.createPending(book.isbn(), book.title(), book.price(), quantity, createdBy);
    }
}
