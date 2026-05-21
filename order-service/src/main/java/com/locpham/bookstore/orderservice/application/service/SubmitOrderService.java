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
                .flatMap(
                        order -> {
                            log.info(
                                    "Persisting order isbn={} quantity={} status={} user={}",
                                    command.isbn(),
                                    command.quantity(),
                                    order.status(),
                                    command.createdBy());
                            return orderCommandPort.save(order);
                        })
                .doOnNext(
                        order ->
                                log.info(
                                        "Order persisted orderId={} isbn={} status={}",
                                        order.id(),
                                        command.isbn(),
                                        order.status()))
                .flatMap(order -> publishOrderCreatedIfPending(order, command.isbn()))
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

    private Mono<Order> publishOrderCreatedIfPending(Order order, String isbn) {
        if (order.status() != OrderStatus.PENDING) {
            log.info(
                    "Order rejected orderId={} isbn={} status={}",
                    order.id(),
                    isbn,
                    order.status());
            return Mono.just(order);
        }

        log.info("Order submitted orderId={} isbn={} status=PENDING", order.id(), isbn);
        log.info("Publishing order-created event orderId={} isbn={}", order.id(), isbn);
        return eventPublisher
                .publishOrderCreated(order)
                .doOnSuccess(
                        unused ->
                                log.info(
                                        "Published order-created event orderId={} isbn={}",
                                        order.id(),
                                        isbn))
                .doOnError(
                        e ->
                                log.error(
                                        "Failed to publish order-created event orderId={} isbn={}",
                                        order.id(),
                                        isbn,
                                        e))
                .thenReturn(order);
    }

    private Order buildPendingOrder(BookSnapshot book, int quantity, String createdBy) {
        return Order.createPending(book.isbn(), book.title(), book.price(), quantity, createdBy);
    }
}
