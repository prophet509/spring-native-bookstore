package com.locpham.bookstore.orderservice.adapter.out.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.orderservice.TestcontainersConfiguration;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import({TestChannelBinderConfiguration.class, TestcontainersConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
public class KafkaOrderEventPublisherTest {
    @MockitoBean private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired private OrderEventPublisherPort publisher;
    @Autowired private OutputDestination output;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishOrderAccepted() throws Exception {
        // 1. Create a test Order
        var order = Order.createAccepted("1234567890", "Test Book", 9.99, 1);

        // 2. Call the publisher
        publisher.publishOrderAccepted(order).block();

        // 3. Capture the message from OutputDestination
        var message = output.receive(1000, "acceptOrder-out-0");

        // 4. Deserialize and verify
        var payload = new String(message.getPayload());
        var orderMessage = objectMapper.readValue(payload, OrderAcceptedMessage.class);

        assertEquals(order.id(), orderMessage.orderId());
    }
}
