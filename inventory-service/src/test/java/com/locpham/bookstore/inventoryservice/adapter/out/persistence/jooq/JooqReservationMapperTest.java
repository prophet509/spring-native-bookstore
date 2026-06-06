package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.generated.tables.records.ReservationRecord;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import com.locpham.bookstore.inventoryservice.domain.ReservationStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JooqReservationMapperTest {

    @Test
    void toRecordMapsAllFields() {
        var id = UUID.randomUUID();
        var reservation = new Reservation(id, 5L, "1234567890", 2, ReservationStatus.RESERVED);

        ReservationRecord record = JooqReservationMapper.toRecord(reservation);

        assertThat(record.getId()).isEqualTo(id);
        assertThat(record.getOrderId()).isEqualTo(5L);
        assertThat(record.getIsbn()).isEqualTo("1234567890");
        assertThat(record.getQuantity()).isEqualTo(2);
        assertThat(record.getStatus()).isEqualTo("RESERVED");
    }

    @Test
    void toDomainMapsAllFields() {
        var id = UUID.randomUUID();
        var record =
                new ReservationRecord()
                        .setId(id)
                        .setOrderId(5L)
                        .setIsbn("1234567890")
                        .setQuantity(2)
                        .setStatus("RELEASED");

        Reservation reservation = JooqReservationMapper.toDomain(record);

        assertThat(reservation.reservationId()).isEqualTo(id);
        assertThat(reservation.orderId()).isEqualTo(5L);
        assertThat(reservation.isbn()).isEqualTo("1234567890");
        assertThat(reservation.quantity()).isEqualTo(2);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED);
    }
}
