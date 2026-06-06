package com.locpham.bookstore.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.orderservice.adapter.in.messaging.InventoryDecisionMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.OrderDispatchedMessage;
import com.locpham.bookstore.orderservice.adapter.in.web.dto.OrderRequest;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end test for the order saga. After the legacy-publish cutover the publish path is
 * Debezium-tailing-the-outbox, so this test asserts <em>outbox_event</em> rows instead of Kafka
 * channel output. Inbound messages still flow through Spring Cloud Stream's test binder.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestChannelBinderConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("http-fallback")
class OrderServiceApplicationTests {

    private static MockWebServer mockWebServer;

    @Autowired private ApplicationContext context;
    @Autowired private OrderQueryPort orderQueryPort;
    @Autowired private InputDestination input;
    @Autowired private DSLContext dsl;
    private ObjectMapper objectMapper;

    @MockitoBean private ReactiveJwtDecoder reactiveJwtDecoder;

    private WebTestClient webClient;

    @BeforeAll
    static void setUpServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDownServer() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("polar.catalog-service-url", () -> mockWebServer.url("/").toString());
    }

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper();
        this.webClient =
                WebTestClient.bindToApplicationContext(context)
                        .apply(springSecurity())
                        .configureClient()
                        .build();
    }

    @Test
    void submitOrderEndToEnd() throws Exception {
        var request = new OrderRequest("1234567890", 2);

        var mockResponse =
                new MockResponse()
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody(
                                """
                       {
                            "isbn": "1234567890",
                            "title": "Book",
                            "price": 9.99
                        }
                        """);
        mockWebServer.enqueue(mockResponse);

        // 1. Submit order
        webClient
                .mutateWith(
                        mockJwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_customer"))
                                .jwt(jwt -> jwt.subject("test-user")))
                .post()
                .uri("/orders")
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk();

        // 2. Verify exactly one OrderCreated row was written to the outbox (atomic with the
        // order save). The aggregate_id is the order id assigned by the DB.
        Long orderId = awaitOutboxRow("OrderCreated", "order-created-events");
        assertThat(orderId).isNotNull();

        // 3. Simulate inventory reserving stock and publishing the decision back through Kafka.
        var inventoryPayload =
                objectMapper.writeValueAsBytes(
                        new InventoryDecisionMessage(orderId, "RESERVED", null));
        input.send(
                MessageBuilder.withPayload(inventoryPayload)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                "handleInventoryDecision-in-0");

        // 4. Verify OrderAccepted outbox row appears for the same order.
        Long acceptedOrderId = awaitOutboxRowFor(orderId, "OrderAccepted", "order-accepted");
        assertThat(acceptedOrderId).isEqualTo(orderId);

        // 5. Simulate dispatch and verify the order moves to DISPATCHED.
        var jsonPayload = objectMapper.writeValueAsBytes(new OrderDispatchedMessage(orderId));
        input.send(
                MessageBuilder.withPayload(jsonPayload)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                "dispatchOrder-in-0");

        Thread.sleep(2000);

        StepVerifier.create(orderQueryPort.findById(orderId))
                .assertNext(order -> assertThat(order.status()).isEqualTo(OrderStatus.DISPATCHED))
                .verifyComplete();
    }

    /**
     * Polls the outbox until ANY row of the given (type,destination) appears, returns its order id.
     */
    private Long awaitOutboxRow(String type, String destination) {
        return Mono.defer(
                        () ->
                                Mono.from(
                                        dsl.select(
                                                        DSL.field(
                                                                DSL.name("aggregate_id"),
                                                                String.class))
                                                .from(DSL.table(DSL.name("outbox_event")))
                                                .where(DSL.field(DSL.name("type")).eq(type))
                                                .and(
                                                        DSL.field(DSL.name("destination"))
                                                                .eq(destination))
                                                .orderBy(DSL.field(DSL.name("created_at")).desc())
                                                .limit(1)))
                .map(record -> Long.valueOf(record.value1()))
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(200)))
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    /** Polls the outbox until a row of the given (type,destination) for `orderId` appears. */
    private Long awaitOutboxRowFor(Long orderId, String type, String destination) {
        return Mono.defer(
                        () ->
                                Mono.from(
                                        dsl.select(
                                                        DSL.field(
                                                                DSL.name("aggregate_id"),
                                                                String.class))
                                                .from(DSL.table(DSL.name("outbox_event")))
                                                .where(DSL.field(DSL.name("type")).eq(type))
                                                .and(
                                                        DSL.field(DSL.name("destination"))
                                                                .eq(destination))
                                                .and(
                                                        DSL.field(DSL.name("aggregate_id"))
                                                                .eq(String.valueOf(orderId)))
                                                .limit(1)))
                .map(record -> Long.valueOf(record.value1()))
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(200)))
                .timeout(Duration.ofSeconds(5))
                .block();
    }
}
