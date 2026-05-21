package com.locpham.bookstore.orderservice.adapter.in.messaging;

import com.locpham.bookstore.orderservice.application.port.in.ProcessInventoryDecisionUseCase;
import java.util.Locale;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

@Configuration
public class InventoryDecisionConsumerAdapter {
    private static final Logger logger =
            LoggerFactory.getLogger(InventoryDecisionConsumerAdapter.class);

    @Bean
    public Consumer<Flux<InventoryDecisionMessage>> handleInventoryDecision(
            ProcessInventoryDecisionUseCase processInventoryDecisionUseCase) {
        return flux ->
                flux.concatMap(
                                message -> {
                                    logger.info(
                                            "Received inventory decision for order {}: {}",
                                            message.orderId(),
                                            message.status());
                                    return processInventoryDecisionUseCase.processDecision(
                                            message.orderId(), toStatus(message.status()));
                                })
                        .doOnError(
                                e ->
                                        logger.error(
                                                "Unexpected error in inventory decision consumer",
                                                e))
                        .subscribe();
    }

    private static ProcessInventoryDecisionUseCase.DecisionStatus toStatus(String rawStatus) {
        return ProcessInventoryDecisionUseCase.DecisionStatus.valueOf(
                rawStatus.toUpperCase(Locale.ROOT));
    }
}
