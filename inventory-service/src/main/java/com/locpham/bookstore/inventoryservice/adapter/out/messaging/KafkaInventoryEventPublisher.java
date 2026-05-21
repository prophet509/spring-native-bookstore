package com.locpham.bookstore.inventoryservice.adapter.out.messaging;

import com.locpham.bookstore.inventoryservice.adapter.out.messaging.messages.InventoryDecisionMessage;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryEventPublisher;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Primary
public class KafkaInventoryEventPublisher implements InventoryEventPublisher {

    private static final Logger logger =
            LoggerFactory.getLogger(KafkaInventoryEventPublisher.class);
    private final StreamBridge streamBridge;

    public KafkaInventoryEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public Mono<Void> publishInventoryDecision(InventoryDecision decision) {
        return Mono.fromRunnable(
                () -> {
                    logger.info(
                            "Publishing inventory decision orderId={} status={} reason={}",
                            decision.orderId(),
                            decision.status(),
                            decision.reason());
                    var message =
                            new InventoryDecisionMessage(
                                    decision.orderId(),
                                    decision.status().name(),
                                    decision.reason());
                    boolean sent = streamBridge.send("inventoryDecision-out-0", message);
                    if (sent) {
                        logger.info(
                                "Inventory decision published successfully orderId={}",
                                decision.orderId());
                    } else {
                        logger.error(
                                "Failed to publish inventory decision orderId={}",
                                decision.orderId());
                        throw new IllegalStateException(
                                "Failed to publish inventory decision for orderId="
                                        + decision.orderId());
                    }
                });
    }
}
