package com.locpham.bookstore.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.locpham.bookstore.orderservice.adapter.in.messaging.InventoryDecisionMessage;
import com.locpham.bookstore.orderservice.adapter.in.messaging.OrderDispatchedMessage;
import com.locpham.bookstore.orderservice.adapter.in.web.dto.OrderRequest;
import com.locpham.bookstore.orderservice.adapter.out.messaging.OrderAcceptedMessage;
import com.locpham.bookstore.orderservice.adapter.out.messaging.OrderCreatedMessage;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestChannelBinderConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceApplicationTests {

    private static MockWebServer mockWebServer;

    @Autowired private ApplicationContext context;
    @Autowired private OrderQueryPort orderQueryPort;
    @Autowired private InputDestination input;
    @Autowired private OutputDestination output;
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
        registry.add("polar.catalogServiceUrl", () -> mockWebServer.url("/").toString());
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

        // Submit order
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

        // Verify order.created published
        var createdMessage = output.receive(5000, "orderCreated-out-0");
        assertThat(createdMessage).isNotNull();

        var orderCreated =
                objectMapper.readValue(createdMessage.getPayload(), OrderCreatedMessage.class);
        var orderId = orderCreated.orderId();

        // Simulate inventory reserving stock and publishing decision
        var inventoryPayload =
                objectMapper.writeValueAsBytes(
                        new InventoryDecisionMessage(orderId, "RESERVED", null));
        input.send(
                MessageBuilder.withPayload(inventoryPayload)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                "handleInventoryDecision-in-0");

        // Verify order.accepted published after inventory RESERVED
        var acceptedMessage = output.receive(5000, "acceptOrder-out-0");
        assertThat(acceptedMessage).isNotNull();

        var orderAcceptedMessage =
                objectMapper.readValue(acceptedMessage.getPayload(), OrderAcceptedMessage.class);
        assertThat(orderAcceptedMessage.orderId()).isEqualTo(orderId);

        var jsonPayload = objectMapper.writeValueAsBytes(new OrderDispatchedMessage(orderId));
        input.send(
                MessageBuilder.withPayload(jsonPayload)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                "dispatchOrder-in-0");

        // Verify order status updated (need to give it a brief moment via reactive test)
        // give it 2 seconds max for the message to be consumed by the stream bridge
        Thread.sleep(2000);

        StepVerifier.create(orderQueryPort.findById(orderId))
                .assertNext(
                        order -> {
                            assertThat(order.status()).isEqualTo(OrderStatus.DISPATCHED);
                        })
                .verifyComplete();
    }
}
