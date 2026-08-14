package com.locpham.bookstore.inventoryservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.inventoryservice.InventoryServiceApplication;
import com.locpham.bookstore.inventoryservice.TestcontainersConfiguration;
import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCancelledMessage;
import com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages.OrderCreatedMessage;
import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.JooqInventoryRepositoryImpl;
import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.JooqReservationRepositoryImpl;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import com.locpham.bookstore.inventoryservice.domain.ReservationStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the inventory reserve/release consumer flows after the legacy-publish cutover. The
 * outbound side (decision publish) now writes to {@code outbox_event} via the transactional outbox
 * publisher, not Kafka — so this test asserts an outbox row appears, rather than reading from a
 * test channel binder.
 *
 * <p>Idempotency on redelivery is covered separately by {@code IdempotentConsumerIT} so this test
 * focuses on (a) reserve happy path and (b) release flow.
 */
@SpringBootTest(
        classes = InventoryServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.config.enabled=false"})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OrderEventConsumerTest {

    @Autowired private JooqInventoryRepositoryImpl inventoryRepository;
    @Autowired private JooqReservationRepositoryImpl reservationRepository;
    @Autowired private DSLContext dsl;
    @Autowired private OrderEventConsumer orderEventConsumer;

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    @Test
    void orderCreated_shouldReserveAndAppendOutboxDecision() {
        var isbn = "ABC-" + UUID.randomUUID();
        inventoryRepository.save(InventoryItem.create(isbn, 10)).block();

        var orderId = System.nanoTime();
        var message =
                new OrderCreatedMessage(
                        orderId, List.of(new OrderCreatedMessage.OrderItem(isbn, 2)));

        orderEventConsumer.reserveStock(message).block();

        // Stock decremented + outbox row appended (atomic).
        StepVerifier.create(awaitStock(isbn, 8, 2))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(8);
                            assertThat(updated.reservedQuantity()).isEqualTo(2);
                        })
                .verifyComplete();

        StepVerifier.create(awaitOutbox(orderId, "InventoryDecision", "inventory-events"))
                .assertNext(count -> assertThat(count).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void orderCancelled_shouldReleaseStockAndUpdateReservationStatus() {
        var isbn = "ABC-" + UUID.randomUUID();
        inventoryRepository.save(InventoryItem.create(isbn, 10)).block();

        var orderId = System.nanoTime();
        orderEventConsumer
                .reserveStock(
                        new OrderCreatedMessage(
                                orderId, List.of(new OrderCreatedMessage.OrderItem(isbn, 2))))
                .block();

        StepVerifier.create(awaitStock(isbn, 8, 2))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(8);
                            assertThat(updated.reservedQuantity()).isEqualTo(2);
                        })
                .verifyComplete();

        orderEventConsumer.releaseStock(new OrderCancelledMessage(orderId)).block();

        StepVerifier.create(awaitStock(isbn, 10, 0))
                .assertNext(
                        updated -> {
                            assertThat(updated.availableQuantity()).isEqualTo(10);
                            assertThat(updated.reservedQuantity()).isEqualTo(0);
                        })
                .verifyComplete();

        StepVerifier.create(reservationRepository.findByOrderId(orderId))
                .assertNext(r -> assertThat(r.status()).isEqualTo(ReservationStatus.RELEASED))
                .verifyComplete();
    }

    private Mono<InventoryItem> awaitStock(String isbn, int available, int reserved) {
        return Mono.defer(() -> inventoryRepository.findByIsbn(isbn).timeout(Duration.ofSeconds(1)))
                .filter(
                        updated ->
                                updated.availableQuantity() == available
                                        && updated.reservedQuantity() == reserved)
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(200)))
                .timeout(Duration.ofSeconds(15));
    }

    private Mono<Integer> awaitOutbox(Long orderId, String type, String destination) {
        return Mono.defer(
                        () ->
                                Mono.from(
                                                dsl.selectCount()
                                                        .from(DSL.table(DSL.name("outbox_event")))
                                                        .where(DSL.field(DSL.name("type")).eq(type))
                                                        .and(
                                                                DSL.field(DSL.name("destination"))
                                                                        .eq(destination))
                                                        .and(
                                                                DSL.field(DSL.name("aggregate_id"))
                                                                        .eq(
                                                                                String.valueOf(
                                                                                        orderId))))
                                        .map(record -> record.value1())
                                        .timeout(Duration.ofSeconds(1)))
                .filter(count -> count > 0)
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(200)))
                .timeout(Duration.ofSeconds(15));
    }
}
