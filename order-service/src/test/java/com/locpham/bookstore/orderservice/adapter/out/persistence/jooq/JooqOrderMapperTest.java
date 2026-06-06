package com.locpham.bookstore.orderservice.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.orderservice.adapter.out.persistence.jooq.generated.tables.records.OrdersRecord;
import com.locpham.bookstore.orderservice.domain.model.AuditMetadata;
import com.locpham.bookstore.orderservice.domain.model.BookInfo;
import com.locpham.bookstore.orderservice.domain.model.Order;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JooqOrderMapperTest {

    private final Instant now = Instant.now();

    private Order sampleOrder() {
        return new Order(
                1L,
                new BookInfo("1234567890", "Title", 9.99),
                2,
                OrderStatus.ACCEPTED,
                new AuditMetadata(now, now, "alice", "bob"),
                3);
    }

    @Test
    void toRecordMapsAllFields() {
        OrdersRecord record = JooqOrderMapper.toRecord(sampleOrder());

        assertThat(record.getId()).isEqualTo(1L);
        assertThat(record.getBookIsbn()).isEqualTo("1234567890");
        assertThat(record.getBookName()).isEqualTo("Title");
        assertThat(record.getBookPrice()).isEqualTo(9.99f);
        assertThat(record.getQuantity()).isEqualTo(2);
        assertThat(record.getStatus()).isEqualTo("ACCEPTED");
        assertThat(record.getCreatedBy()).isEqualTo("alice");
        assertThat(record.getLastModifiedBy()).isEqualTo("bob");
        assertThat(record.getVersion()).isEqualTo(3);
    }

    @Test
    void toDomainMapsAllFields() {
        OrdersRecord record = JooqOrderMapper.toRecord(sampleOrder());

        Order order = JooqOrderMapper.toDomain(record);

        assertThat(order.id()).isEqualTo(1L);
        assertThat(order.book().isbn()).isEqualTo("1234567890");
        assertThat(order.book().title()).isEqualTo("Title");
        assertThat(order.quantity()).isEqualTo(2);
        assertThat(order.status()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.audit().createdBy()).isEqualTo("alice");
        assertThat(order.audit().lastModifiedBy()).isEqualTo("bob");
        assertThat(order.version()).isEqualTo(3);
    }

    @Test
    void toDomainHandlesNullDates() {
        var record =
                new OrdersRecord()
                        .setId(5L)
                        .setBookIsbn("1234567890")
                        .setBookName("Title")
                        .setBookPrice(1.0f)
                        .setQuantity(1)
                        .setStatus("PENDING")
                        .setCreatedBy("alice")
                        .setLastModifiedBy("alice")
                        .setVersion(0);

        Order order = JooqOrderMapper.toDomain(record);

        assertThat(order.audit().createdDate()).isNull();
        assertThat(order.audit().lastModifiedDate()).isNull();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    }
}
