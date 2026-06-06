package com.locpham.bookstore.orderservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.orderservice.adapter.in.web.dto.OrderRequest;
import org.junit.jupiter.api.Test;

class OrderWebMapperTest {

    @Test
    void toCommandMapsFields() {
        var command = OrderWebMapper.toCommand(new OrderRequest("1234567890", 3), "user-1");

        assertThat(command.isbn()).isEqualTo("1234567890");
        assertThat(command.quantity()).isEqualTo(3);
        assertThat(command.createdBy()).isEqualTo("user-1");
    }

    @Test
    void toQueryMapsUserId() {
        var query = OrderWebMapper.toQuery("user-2");

        assertThat(query.userId()).isEqualTo("user-2");
    }
}
