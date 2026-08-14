package com.locpham.bookstore.orderservice.adapter.in.messaging;

import com.locpham.bookstore.orderservice.application.command.MarkOrderDispatchedCommand;
import com.locpham.bookstore.orderservice.application.port.in.MarkOrderDispatchedUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OrderDispatchedConsumerAdapter {
    private static final Logger logger =
            LoggerFactory.getLogger(OrderDispatchedConsumerAdapter.class);

    private final MarkOrderDispatchedUseCase markOrderDispatchedUseCase;

    public OrderDispatchedConsumerAdapter(MarkOrderDispatchedUseCase markOrderDispatchedUseCase) {
        this.markOrderDispatchedUseCase = markOrderDispatchedUseCase;
    }

    @KafkaListener(
            topics = "${polar.kafka.topics.order-dispatched:order-dispatched}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public Mono<Void> dispatchOrder(OrderDispatchedMessage message) {
        var command = new MarkOrderDispatchedCommand(message.orderId());
        return markOrderDispatchedUseCase
                .markOrderDispatched(command)
                .onErrorResume(
                        e -> {
                            logger.error(
                                    "Failed to mark order dispatched for orderId={}",
                                    message.orderId(),
                                    e);
                            return Mono.empty();
                        })
                .doOnNext(order -> logger.info("The order with id {} is dispatched", order.id()))
                .then();
    }
}
