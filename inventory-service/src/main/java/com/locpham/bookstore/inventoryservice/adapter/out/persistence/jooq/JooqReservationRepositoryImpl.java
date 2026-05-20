package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

import static com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.generated.tables.Reservation.RESERVATION;

import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.generated.tables.records.ReservationRecord;
import com.locpham.bookstore.inventoryservice.application.port.out.ReservationPort;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class JooqReservationRepositoryImpl implements ReservationPort {

    private static final Logger log = LoggerFactory.getLogger(JooqReservationRepositoryImpl.class);

    private final DSLContext dsl;

    public JooqReservationRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Flux<Reservation> findByOrderId(Long orderId) {
        log.debug("findByOrderId orderId={}", orderId);
        return Flux.from(dsl.selectFrom(RESERVATION).where(RESERVATION.ORDER_ID.eq(orderId)))
                .map(JooqReservationMapper::toDomain);
    }

    @Override
    public Mono<Reservation> save(Reservation reservation) {
        ReservationRecord record = JooqReservationMapper.toRecord(reservation);
        if (reservation.reservationId() == null) {
            log.debug(
                    "Inserting reservation orderId={} isbn={} quantity={} status={}",
                    reservation.orderId(),
                    reservation.isbn(),
                    reservation.quantity(),
                    reservation.status());
            return Mono.from(
                            dsl.insertInto(RESERVATION)
                                    .set(RESERVATION.ORDER_ID, record.getOrderId())
                                    .set(RESERVATION.ISBN, record.getIsbn())
                                    .set(RESERVATION.QUANTITY, record.getQuantity())
                                    .set(RESERVATION.STATUS, record.getStatus())
                                    .returning(RESERVATION.fields()))
                    .map(JooqReservationMapper::toDomain)
                    // jOOQ exceptions from R2DBC don't go through Spring's usual exception
                    // translation.
                    // We surface a Spring DAO exception so higher layers can handle duplicates
                    // consistently.
                    .onErrorMap(
                            DataAccessException.class,
                            e -> {
                                log.warn(
                                        "Data access error saving reservation orderId={} isbn={}: {}",
                                        reservation.orderId(),
                                        reservation.isbn(),
                                        e.getMessage());
                                return new DataIntegrityViolationException(e.getMessage(), e);
                            });
        } else {
            log.debug(
                    "Updating reservation id={} orderId={} status={}",
                    reservation.reservationId(),
                    reservation.orderId(),
                    reservation.status());
            return Mono.from(
                            dsl.update(RESERVATION)
                                    .set(RESERVATION.STATUS, record.getStatus())
                                    .where(RESERVATION.ID.eq(reservation.reservationId()))
                                    .returning(RESERVATION.fields()))
                    .map(JooqReservationMapper::toDomain);
        }
    }
}
