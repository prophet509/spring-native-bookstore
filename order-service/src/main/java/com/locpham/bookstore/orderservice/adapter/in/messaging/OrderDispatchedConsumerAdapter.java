package com.locpham.bookstore.orderservice.adapter.in.messaging;

import com.locpham.bookstore.orderservice.application.command.MarkOrderDispatchedCommand;
import com.locpham.bookstore.orderservice.application.port.in.MarkOrderDispatchedUseCase;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class OrderDispatchedConsumerAdapter {
    private static final Logger logger =
            LoggerFactory.getLogger(OrderDispatchedConsumerAdapter.class);

    @Bean
    public Consumer<Flux<OrderDispatchedMessage>> dispatchOrder(
            MarkOrderDispatchedUseCase markOrderDispatchedUseCase) {
        return flux ->
                flux.flatMap(
                                message -> {
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
                                                    });
                                },
                                8) // max 8 concurrent to avoid pool exhaustion
                        .doOnNext(
                                order ->
                                        logger.info(
                                                "The order with id {} is dispatched", order.id()))
                        .subscribe();
    }
}
