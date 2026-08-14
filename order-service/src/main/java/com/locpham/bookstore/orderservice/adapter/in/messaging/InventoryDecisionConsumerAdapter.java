package com.locpham.bookstore.orderservice.adapter.in.messaging;

import com.locpham.bookstore.orderservice.application.port.in.ProcessInventoryDecisionUseCase;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class InventoryDecisionConsumerAdapter {
    private static final Logger logger =
            LoggerFactory.getLogger(InventoryDecisionConsumerAdapter.class);

    private final ProcessInventoryDecisionUseCase processInventoryDecisionUseCase;

    public InventoryDecisionConsumerAdapter(
            ProcessInventoryDecisionUseCase processInventoryDecisionUseCase) {
        this.processInventoryDecisionUseCase = processInventoryDecisionUseCase;
    }

    @KafkaListener(
            topics = "${polar.kafka.topics.inventory-events:inventory-events}",
            groupId = "${spring.kafka.consumer.group-id:order-service}")
    public Mono<Void> handleInventoryDecision(InventoryDecisionMessage message) {
        logger.info(
                "Received inventory decision for order {}: {}",
                message.orderId(),
                message.status());
        return processInventoryDecisionUseCase
                .processDecision(message.orderId(), toStatus(message.status()))
                .onErrorResume(
                        e -> {
                            logger.error(
                                    "Failed to process inventory decision for order {}: {}",
                                    message.orderId(),
                                    message.status(),
                                    e);
                            return Mono.empty();
                        })
                .then();
    }

    private static ProcessInventoryDecisionUseCase.DecisionStatus toStatus(String rawStatus) {
        return ProcessInventoryDecisionUseCase.DecisionStatus.valueOf(
                rawStatus.toUpperCase(Locale.ROOT));
    }
}
