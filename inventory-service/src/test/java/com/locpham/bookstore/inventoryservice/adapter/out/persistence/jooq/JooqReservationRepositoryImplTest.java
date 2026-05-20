package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.inventoryservice.TestcontainersConfiguration;
import com.locpham.bookstore.inventoryservice.domain.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.config.enabled=false", "spring.cloud.stream.enabled=false"})
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class JooqReservationRepositoryImplTest {

    @Autowired private JooqReservationRepositoryImpl reservationRepository;

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    @Test
    void saveAndFindByOrderId() {
        var orderId = 1L;
        var reservation = Reservation.create(orderId, "123", 2);

        StepVerifier.create(
                        reservationRepository
                                .save(reservation)
                                .thenMany(reservationRepository.findByOrderId(orderId)))
                .assertNext(
                        found -> {
                            assertThat(found.orderId()).isEqualTo(orderId);
                            assertThat(found.isbn()).isEqualTo("123");
                            assertThat(found.quantity()).isEqualTo(2);
                        })
                .verifyComplete();
    }

    @Test
    void save_shouldFailWhenDuplicateOrderIdAndIsbn() {
        var orderId = 2L;
        var reservation = Reservation.create(orderId, "DUP", 1);

        Mono<Void> flow =
                reservationRepository
                        .save(reservation)
                        .then(reservationRepository.save(Reservation.create(orderId, "DUP", 1)))
                        .then();

        StepVerifier.create(flow)
                .expectErrorSatisfies(
                        ex -> assertThat(ex).isInstanceOf(DataIntegrityViolationException.class))
                .verify();
    }
}
