package com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka;

import com.locpham.bookstore.searchservice.application.out.message.BookEventPublisher;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
public class KafkaBookEventPublisher implements BookEventPublisher {
    private Logger logger = LoggerFactory.getLogger(KafkaBookEventPublisher.class);

    private final StreamBridge streamBridge;

    public KafkaBookEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @Override
    public Mono<Void> publishBookCreated(BookDocument book) {
        return streamBridge.send("")
    }

    @Override
    public void publishBookUpdated(BookDocument book) {

    }

    @Override
    public void publishBookDeleted(String isbn) {

    }
}
