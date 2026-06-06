package com.locpham.bookstore.orderservice.application.service;

import static org.mockito.BDDMockito.given;

import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.application.query.GetOrdersQuery;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GetOrdersServiceTest {

    @Mock private OrderQueryPort orderQueryPort;

    @InjectMocks private GetOrdersService service;

    @Test
    void getOrdersReturnsOrdersForUser() {
        var order = Order.createPending("1234567890", "Title", 9.99, 1, "bob");
        given(orderQueryPort.findByCreatedBy("bob")).willReturn(Flux.just(order));

        StepVerifier.create(service.getOrders(new GetOrdersQuery("bob")))
                .expectNext(order)
                .verifyComplete();
    }

    @Test
    void getOrdersPropagatesError() {
        given(orderQueryPort.findByCreatedBy("bob"))
                .willReturn(Flux.error(new RuntimeException("db down")));

        StepVerifier.create(service.getOrders(new GetOrdersQuery("bob")))
                .expectError(RuntimeException.class)
                .verify();
    }
}
