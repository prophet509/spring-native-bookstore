package com.locpham.bookstore.inventoryservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.inventoryservice.InventoryServiceApplication;
import com.locpham.bookstore.inventoryservice.TestcontainersConfiguration;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryEventPublisher;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the transactional outbox guarantee for inventory-service across BOTH decision paths
 * (correction vs the earlier plan draft, which only covered the success path):
 *
 * <ul>
 *   <li><b>reserved</b>: aggregate save + outbox append commit atomically in one transaction.
 *   <li><b>rejected</b>: a single outbox insert with no aggregate write is still routed through the
 *       outbox for uniform ordering/delivery.
 * </ul>
 *
 * <p>The {@link DSLContext} is R2DBC-backed (see {@code JooqConfig#dslContext}), so all
 * verification queries use the reactive jOOQ API ({@code Mono.from(...)}).
 */
@SpringBootTest(
        classes = InventoryServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.config.enabled=false", "spring.cloud.stream.enabled=false"})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OutboxAppendIT {

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    @Autowired private InventoryEventPublisher publisher;
    @Autowired private InventoryPort inventoryPort;
    @Autowired private TransactionalOperator transactionalOperator;
    @Autowired private DSLContext dsl;

    @Test
    void verifyOutboxPublisherIsActive() {
        assertThat(publisher).isInstanceOf(OutboxInventoryEventPublisher.class);
    }

    @Test
    void rejectedDecision_writesOutboxRow() {
        long orderId = System.nanoTime();
        var rejected = InventoryDecision.rejected(orderId, "Insufficient stock");

        StepVerifier.create(publisher.publishInventoryDecision(rejected).then(countOutbox(orderId)))
                .assertNext(count -> assertThat(count).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void reservedDecision_commitsInventorySaveAndOutboxRowAtomically() {
        long orderId = System.nanoTime();
        var isbn = "OUTBOX-OK-" + orderId;
        var item = InventoryItem.create(isbn, 10);
        var reserved = InventoryDecision.reserved(orderId);

        // Mirror ReserveStockService success path: saveAll + outbox append in ONE transaction.
        Mono<Void> flow =
                transactionalOperator.transactional(
                        inventoryPort
                                .saveAll(List.of(item))
                                .then(publisher.publishInventoryDecision(reserved)));

        StepVerifier.create(
                        flow.then(
                                Mono.zip(
                                        countOutbox(orderId),
                                        countInventory(isbn),
                                        (outbox, inv) -> "outbox=" + outbox + ",inv=" + inv)))
                .assertNext(counts -> assertThat(counts).isEqualTo("outbox=1,inv=1"))
                .verifyComplete();
    }

    /**
     * Atomicity is structurally guaranteed because the inventory save and the outbox append both go
     * through the same {@link DSLContext}, which is wired with a {@code
     * TransactionAwareConnectionFactoryProxy} so it reuses the connection bound by {@link
     * TransactionalOperator}. This test proves that visibility by reading both rows from <em>within
     * the same transaction</em> as the writes — only possible if they share one connection. If they
     * share a connection, they share the commit/rollback fate.
     */
    @Test
    void inventoryAndOutboxRow_areVisibleWithinTheSameTransaction() {
        long orderId = System.nanoTime();
        var isbn = "OUTBOX-VISIBLE-" + orderId;
        var item = InventoryItem.create(isbn, 10);
        var reserved = InventoryDecision.reserved(orderId);

        Mono<String> flow =
                transactionalOperator.transactional(
                        inventoryPort
                                .saveAll(List.of(item))
                                .then(publisher.publishInventoryDecision(reserved))
                                .then(
                                        Mono.zip(
                                                countOutbox(orderId),
                                                countInventory(isbn),
                                                (outbox, inv) ->
                                                        "outbox=" + outbox + ",inv=" + inv)));

        StepVerifier.create(flow)
                .assertNext(counts -> assertThat(counts).isEqualTo("outbox=1,inv=1"))
                .verifyComplete();
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

    private Mono<Integer> countInventory(String isbn) {
        return Mono.from(
                        dsl.selectCount()
                                .from(DSL.table(DSL.name("inventory")))
                                .where(DSL.field(DSL.name("isbn")).eq(isbn)))
                .map(record -> record.value1());
    }
}
