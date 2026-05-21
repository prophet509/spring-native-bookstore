package com.locpham.bookstore.orderservice.adapter.in.messaging;

import com.locpham.bookstore.orderservice.application.port.in.ProcessInventoryDecisionUseCase;
import java.util.Locale;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class InventoryDecisionConsumerAdapter {
    private static final Logger logger =
            LoggerFactory.getLogger(InventoryDecisionConsumerAdapter.class);

    @Bean
    public Consumer<Flux<InventoryDecisionMessage>> handleInventoryDecision(
            ProcessInventoryDecisionUseCase processInventoryDecisionUseCase) {
        return flux ->
                flux.flatMap(
                                message -> {
                                    logger.info(
                                            "Received inventory decision for order {}: {}",
                                            message.orderId(),
                                            message.status());
                                    return processInventoryDecisionUseCase
                                            .processDecision(
                                                    message.orderId(), toStatus(message.status()))
                                            .onErrorResume(
                                                    e -> {
                                                        logger.error(
                                                                "Failed to process inventory decision for order {}: {}",
                                                                message.orderId(),
                                                                message.status(),
                                                                e);
                                                        return Mono.empty();
                                                    });
                                },
                                8) // max 8 concurrent to avoid pool exhaustion
                        .subscribe();
    }

    private static ProcessInventoryDecisionUseCase.DecisionStatus toStatus(String rawStatus) {
        return ProcessInventoryDecisionUseCase.DecisionStatus.valueOf(
                rawStatus.toUpperCase(Locale.ROOT));
    }
}
