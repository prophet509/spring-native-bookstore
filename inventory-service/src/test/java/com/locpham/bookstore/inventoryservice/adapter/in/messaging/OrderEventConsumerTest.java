package com.locpham.bookstore.inventoryservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.inventoryservice.TestcontainersConfiguration;
import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCancelledMessage;
import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCreatedMessage;
import com.locpham.bookstore.inventoryservice.adapter.out.messaging.messages.InventoryDecisionMessage;
import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.JooqInventoryRepositoryImpl;
import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.JooqReservationRepositoryImpl;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import com.locpham.bookstore.inventoryservice.domain.ReservationStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, TestChannelBinderConfiguration.class})
@Testcontainers
class OrderEventConsumerTest {

    @Autowired private InputDestination input;
    @Autowired private OutputDestination output;
    @Autowired private JooqInventoryRepositoryImpl inventoryRepository;
    @Autowired private JooqReservationRepositoryImpl reservationRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper();
        drainOutput("inventory-events");
        drainOutput("order-events");
    }

    private void drainOutput(String bindingName) {
        Message<byte[]> message;
        do {
            message = output.receive(0, bindingName);
        } while (message != null);
    }

    private Mono<InventoryItem> awaitStock(String isbn, int available, int reserved) {
        // Guard each DB read so a transient stalled connection doesn't hang the whole await.
        return Mono.defer(() -> inventoryRepository.findByIsbn(isbn).timeout(Duration.ofSeconds(1)))
                .filter(
                        updated ->
                                updated.availableQuantity() == available
                                        && updated.reservedQuantity() == reserved)
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(200)))
                .timeout(Duration.ofSeconds(30));
    }

    @Test
    void orderCreated_shouldReserveAndPublishDecision() throws Exception {
        inventoryRepository.save(InventoryItem.create("ABC", 10)).block();

        var orderId = UUID.randomUUID();
        var message =
                new OrderCreatedMessage(
                        orderId, List.of(new OrderCreatedMessage.OrderItem("ABC", 2)));

        input.send(
                MessageBuilder.withPayload(objectMapper.writeValueAsBytes(message))
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                "order-events");

        var out = output.receive(5000, "inventory-events");
        assertThat(out).isNotNull();

        var decisionMessage =
                objectMapper.readValue(out.getPayload(), InventoryDecisionMessage.class);
        assertThat(decisionMessage.orderId()).isEqualTo(orderId);
        assertThat(decisionMessage.status()).isEqualTo("RESERVED");

        StepVerifier.create(inventoryRepository.findByIsbn("ABC"))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(8);
                            assertThat(updated.reservedQuantity()).isEqualTo(2);
                        })
                .verifyComplete();
    }

    @Test
    void orderCreated_duplicate_shouldNotDoubleDecrementStock() throws Exception {
        inventoryRepository.save(InventoryItem.create("DUP", 10)).block();

        var orderId = UUID.randomUUID();
        var message =
                new OrderCreatedMessage(
                        orderId, List.of(new OrderCreatedMessage.OrderItem("DUP", 2)));

        var payload =
                MessageBuilder.withPayload(objectMapper.writeValueAsBytes(message))
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();

        input.send(payload, "order-events");
        var first = output.receive(5000, "inventory-events");
        assertThat(first).isNotNull();
        drainOutput("inventory-events");

        var firstDecision =
                objectMapper.readValue(first.getPayload(), InventoryDecisionMessage.class);
        assertThat(firstDecision.orderId()).isEqualTo(orderId);
        assertThat(firstDecision.status()).isEqualTo("RESERVED");

        StepVerifier.create(awaitStock("DUP", 8, 2))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(8);
                            assertThat(updated.reservedQuantity()).isEqualTo(2);
                        })
                .verifyComplete();

        input.send(payload, "order-events");

        // Duplicate events must be idempotent at the state level (inventory must not decrement
        // twice). Re-publishing the same decision event is acceptable depending on retries.
        var maybeDuplicateDecision = output.receive(1500, "inventory-events");
        if (maybeDuplicateDecision != null) {
            var decision =
                    objectMapper.readValue(
                            maybeDuplicateDecision.getPayload(), InventoryDecisionMessage.class);
            assertThat(decision.orderId()).isEqualTo(orderId);
            assertThat(decision.status()).isEqualTo("RESERVED");
        }

        StepVerifier.create(awaitStock("DUP", 8, 2))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(8);
                            assertThat(updated.reservedQuantity()).isEqualTo(2);
                        })
                .verifyComplete();
    }

    @Test
    void orderCancelled_shouldReleaseStockAndUpdateReservationStatus() throws Exception {
        inventoryRepository.save(InventoryItem.create("ABC", 10)).block();

        var orderId = UUID.randomUUID();

        // Act: Send order-created event
        var createMessage =
                new OrderCreatedMessage(
                        orderId, List.of(new OrderCreatedMessage.OrderItem("ABC", 2)));
        input.send(
                MessageBuilder.withPayload(objectMapper.writeValueAsBytes(createMessage))
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                "order-events");

        // Verify stock đã được reserve
        StepVerifier.create(awaitStock("ABC", 8, 2))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(8);
                            assertThat(updated.reservedQuantity()).isEqualTo(2);
                        })
                .verifyComplete();

        drainOutput("inventory-events"); // clean decision

        // Act: Send order-cancelled event
        var cancelMessage = new OrderCancelledMessage(orderId);
        input.send(
                MessageBuilder.withPayload(objectMapper.writeValueAsBytes(cancelMessage))
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                "order-events");

        // Assert wait for invetory release
        StepVerifier.create(awaitStock("ABC", 10, 0))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(10);
                            assertThat(updated.reservedQuantity()).isEqualTo(0);
                        })
                .verifyComplete();

        // Assert is released
        StepVerifier.create(reservationRepository.findByOrderId(orderId))
                .assertNext(
                        r -> {
                            assertThat(r.status()).isEqualTo(ReservationStatus.RELEASED);
                        })
                .verifyComplete();
    }
}
