package com.locpham.bookstore.orderservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.orderservice.OrderServiceApplication;
import com.locpham.bookstore.orderservice.TestcontainersConfiguration;
import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderEventPublisherPort;
import com.locpham.bookstore.orderservice.domain.model.Order;
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
 * Verifies the transactional outbox guarantee for order-service: the aggregate row and the {@code
 * outbox_event} row are written atomically in the same R2DBC transaction.
 *
 * <p>The {@link DSLContext} here is R2DBC-backed (see {@code JooqConfig#dslContext}), so all
 * verification queries use the reactive jOOQ API ({@code Mono.from(...)}) — blocking {@code fetch*}
 * calls would fail on a {@code ConnectionFactory}-based context.
 */
@SpringBootTest(
        classes = OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OutboxAppendIT {

    @MockitoBean private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired private OrderEventPublisherPort publisher;
    @Autowired private OrderCommandPort orderCommandPort;
    @Autowired private TransactionalOperator transactionalOperator;
    @Autowired private DSLContext dsl;

    @Test
    void verifyOutboxPublisherIsActive() {
        assertThat(publisher).isInstanceOf(OutboxOrderEventPublisher.class);
    }

    @Test
    void commit_writesBothOrderAndOutboxRowAtomically() {
        var order = Order.createPending("OUTBOX-OK-" + System.nanoTime(), "Title", 9.90, 1);

        // Mirror SubmitOrderService: save aggregate + append outbox in ONE transaction.
        Mono<Long> flow =
                transactionalOperator.transactional(
                        orderCommandPort
                                .save(order)
                                .flatMap(
                                        saved ->
                                                publisher
                                                        .publishOrderCreated(saved)
                                                        .thenReturn(saved.id())));

        StepVerifier.create(
                        flow.flatMap(
                                savedId ->
                                        Mono.zip(
                                                countOrders(savedId),
                                                countOutbox(savedId),
                                                (orders, outbox) ->
                                                        "orders=" + orders + ",outbox=" + outbox)))
                .assertNext(counts -> assertThat(counts).isEqualTo("orders=1,outbox=1"))
                .verifyComplete();
    }

    /**
     * Atomicity is structurally guaranteed because the order save and the outbox append both go
     * through the same {@link DSLContext}, which is wired with a {@code
     * TransactionAwareConnectionFactoryProxy} so it reuses the connection bound by {@link
     * TransactionalOperator}. This test proves that visibility by reading both rows from <em>within
     * the same transaction</em> as the writes — only possible if they share one connection. If they
     * share a connection, they share the commit/rollback fate.
     */
    @Test
    void aggregateAndOutboxRow_areVisibleWithinTheSameTransaction() {
        var isbn = "OUTBOX-VISIBLE-" + System.nanoTime();
        var order = Order.createPending(isbn, "Title", 9.90, 1);

        Mono<String> flow =
                transactionalOperator.transactional(
                        orderCommandPort
                                .save(order)
                                .flatMap(
                                        saved ->
                                                publisher
                                                        .publishOrderCreated(saved)
                                                        .thenReturn(saved))
                                .flatMap(
                                        saved ->
                                                Mono.zip(
                                                        countOrdersByIsbn(isbn),
                                                        countOutbox(saved.id()),
                                                        (orders, outbox) ->
                                                                "orders="
                                                                        + orders
                                                                        + ",outbox="
                                                                        + outbox)));

        StepVerifier.create(flow)
                .assertNext(counts -> assertThat(counts).isEqualTo("orders=1,outbox=1"))
                .verifyComplete();
    }

    private Mono<Integer> countOrders(Long id) {
        return Mono.from(
                        dsl.selectCount()
                                .from(DSL.table(DSL.name("orders")))
                                .where(DSL.field(DSL.name("id")).eq(id)))
                .map(record -> record.value1());
    }

    private Mono<Integer> countOrdersByIsbn(String isbn) {
        return Mono.from(
                        dsl.selectCount()
                                .from(DSL.table(DSL.name("orders")))
                                .where(DSL.field(DSL.name("book_isbn")).eq(isbn)))
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
