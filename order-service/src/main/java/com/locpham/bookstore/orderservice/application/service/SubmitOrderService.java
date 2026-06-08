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
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Service
public class SubmitOrderService implements SubmitOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitOrderService.class);

    private final CatalogBookPort catalogBookPort;
    private final OrderCommandPort orderCommandPort;
    private final OrderEventPublisherPort eventPublisher;
    private final TransactionalOperator transactionalOperator;

    public SubmitOrderService(
            CatalogBookPort catalogBookPort,
            OrderCommandPort orderCommandPort,
            OrderEventPublisherPort eventPublisher,
            TransactionalOperator transactionalOperator) {
        this.catalogBookPort = catalogBookPort;
        this.orderCommandPort = orderCommandPort;
        this.eventPublisher = eventPublisher;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<Order> submitOrder(SubmitOrderCommand command) {
        log.atDebug()
                .addKeyValue("isbn", command.isbn())
                .addKeyValue("quantity", command.quantity())
                .addKeyValue("createdBy", command.createdBy())
                .log("Submitting order");
        return catalogBookPort
                .loadBook(command.isbn())
                .map(book -> buildPendingOrder(book, command.quantity(), command.createdBy()))
                .switchIfEmpty(
                        Mono.just(
                                buildRejectedOrder(
                                        command.isbn(), command.quantity(), command.createdBy())))
                .flatMap(
                        order -> {
                            log.atDebug()
                                    .addKeyValue("isbn", command.isbn())
                                    .addKeyValue("quantity", command.quantity())
                                    .addKeyValue("status", order.status())
                                    .addKeyValue("createdBy", command.createdBy())
                                    .log("Persisting order");
                            return transactionalOperator.transactional(
                                    orderCommandPort
                                            .save(order)
                                            .doOnNext(
                                                    saved ->
                                                            log.atDebug()
                                                                    .addKeyValue(
                                                                            "orderId", saved.id())
                                                                    .addKeyValue(
                                                                            "isbn", command.isbn())
                                                                    .addKeyValue(
                                                                            "status",
                                                                            saved.status())
                                                                    .log("Order persisted"))
                                            .flatMap(
                                                    saved ->
                                                            publishOrderCreatedIfPending(
                                                                    saved, command.isbn())));
                        })
                .doOnError(
                        e ->
                                log.atError()
                                        .addKeyValue("isbn", command.isbn())
                                        .setCause(e)
                                        .log("Failed to submit order"));
    }

    private Order buildRejectedOrder(String isbn, int quantity, String createdBy) {
        return Order.createRejected(isbn, null, 0.0, quantity, createdBy);
    }

    private Mono<Order> publishOrderCreatedIfPending(Order order, String isbn) {
        if (order.status() != OrderStatus.PENDING) {
            orderLog(log.atInfo(), order, isbn)
                    .addKeyValue("status", order.status())
                    .log("Order rejected");
            return Mono.just(order);
        }

        orderLog(log.atInfo(), order, isbn).addKeyValue("status", "PENDING").log("Order submitted");
        orderLog(log.atDebug(), order, isbn).log("Publishing order-created event");
        return eventPublisher
                .publishOrderCreated(order)
                .doOnSuccess(
                        unused ->
                                orderLog(log.atDebug(), order, isbn)
                                        .log("Published order-created event"))
                .doOnError(
                        e ->
                                orderLog(log.atError(), order, isbn)
                                        .setCause(e)
                                        .log("Failed to publish order-created event"))
                .thenReturn(order);
    }

    /**
     * Decorates a log event with the {@code orderId} and {@code isbn} key-value pair common to
     * every order-lifecycle log line, so callers only add the event-specific keys.
     */
    private static LoggingEventBuilder orderLog(
            LoggingEventBuilder builder, Order order, String isbn) {
        return builder.addKeyValue("orderId", order.id()).addKeyValue("isbn", isbn);
    }

    private Order buildPendingOrder(BookSnapshot book, int quantity, String createdBy) {
        return Order.createPending(book.isbn(), book.title(), book.price(), quantity, createdBy);
    }
}
