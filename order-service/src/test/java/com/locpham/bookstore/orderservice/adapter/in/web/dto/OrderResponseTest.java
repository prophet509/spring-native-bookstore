package com.locpham.bookstore.orderservice.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.orderservice.domain.model.Order;
import org.junit.jupiter.api.Test;

class OrderResponseTest {

    @Test
    void fromDomainMapsAllFields() {
        var order = Order.createPending("1234567890", "Clean Code", 42.5, 2, "alice");

        var response = OrderResponse.fromDomain(order);

        assertThat(response.isbn()).isEqualTo("1234567890");
        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.price()).isEqualTo(42.5);
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.createdBy()).isEqualTo("alice");
    }
}
