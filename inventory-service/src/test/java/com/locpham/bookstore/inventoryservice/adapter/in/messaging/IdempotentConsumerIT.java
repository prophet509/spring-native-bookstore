package com.locpham.bookstore.inventoryservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.locpham.bookstore.inventoryservice.InventoryServiceApplication;
import com.locpham.bookstore.inventoryservice.TestcontainersConfiguration;
import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase.OrderItem;
import com.locpham.bookstore.inventoryservice.application.port.in.ReserveStockUseCase.OrderReserveRequest;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import java.time.Duration;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies that redelivering the same {@code OrderCreated} message is a no-op (the non-negotiable
 * invariant in plan §2.9). The Redis fast-path (24h {@code inv:idem:<orderId>} key) is mocked to
 * always grant the claim so this test exercises the <b>durable DB-level guard</b> (matching {@code
 * reservationPort.findByOrderId} short-circuit) — the layer that survives a Redis outage. Inventory
 * is deducted exactly once and exactly one outbox event is emitted across both deliveries.
 */
@SpringBootTest(
        classes = InventoryServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.config.enabled=false", "spring.cloud.stream.enabled=false"})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class IdempotentConsumerIT {

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    /**
     * Always returns {@code true} for {@code setIfAbsent}, i.e. claim succeeds — so the second
     * delivery is forced to fall through to the DB-level idempotency check.
     */
    @MockitoBean private ReactiveRedisTemplate<String, String> redisTemplate;

    @Autowired private ReserveStockUseCase reserveStockUseCase;
    @Autowired private InventoryPort inventoryPort;
    @Autowired private DSLContext dsl;

    @Test
    void redeliveringSameOrderId_isANoOp_atTheDurableLayer() {
        long orderId = System.nanoTime();
        var isbn = "IDEMP-" + orderId;
        int initialStock = 10;
        int reserveQty = 3;

        // Force the Redis fast-path to always grant the claim, so we test the DB-level guard.
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> ops = Mockito.mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        var request = new OrderReserveRequest(orderId, List.of(new OrderItem(isbn, reserveQty)));

        // Seed inventory, then deliver the same reservation request twice.
        Mono<String> flow =
                inventoryPort
                        .save(InventoryItem.create(isbn, initialStock))
                        .then(reserveStockUseCase.reserveForOrder(request))
                        .then(reserveStockUseCase.reserveForOrder(request))
                        .then(
                                Mono.zip(
                                        reservedQty(isbn),
                                        availableQty(isbn),
                                        countReservations(orderId),
                                        countOutbox(orderId)))
                        .map(
                                t ->
                                        "reserved="
                                                + t.getT1()
                                                + ",available="
                                                + t.getT2()
                                                + ",reservations="
                                                + t.getT3()
                                                + ",outbox="
                                                + t.getT4());

        StepVerifier.create(flow)
                .assertNext(
                        summary ->
                                // After two deliveries: exactly ONE deduction, ONE reservation
                                // row, ONE outbox row.
                                assertThat(summary)
                                        .isEqualTo(
                                                "reserved="
                                                        + reserveQty
                                                        + ",available="
                                                        + (initialStock - reserveQty)
                                                        + ",reservations=1"
                                                        + ",outbox=1"))
                .verifyComplete();
    }

    private Mono<Integer> reservedQty(String isbn) {
        return Mono.from(
                        dsl.select(DSL.field(DSL.name("reserved_quantity"), Integer.class))
                                .from(DSL.table(DSL.name("inventory")))
                                .where(DSL.field(DSL.name("isbn")).eq(isbn)))
                .map(record -> record.value1());
    }

    private Mono<Integer> availableQty(String isbn) {
        return Mono.from(
                        dsl.select(DSL.field(DSL.name("available_quantity"), Integer.class))
                                .from(DSL.table(DSL.name("inventory")))
                                .where(DSL.field(DSL.name("isbn")).eq(isbn)))
                .map(record -> record.value1());
    }

    private Mono<Integer> countReservations(Long orderId) {
        return Mono.from(
                        dsl.selectCount()
                                .from(DSL.table(DSL.name("reservation")))
                                .where(DSL.field(DSL.name("order_id")).eq(orderId)))
                .map(record -> record.value1());
    }

    private Mono<Integer> countOutbox(Long aggregateId) {
        return Mono.from(
                        dsl.selectCount()
                                .from(DSL.table(DSL.name("outbox_event")))
                                .where(
                                        DSL.field(DSL.name("aggregate_id"))
                                                .eq(String.valueOf(aggregateId))))
                .map(record -> record.value1());
    }
}
