package com.locpham.bookstore.orderservice.adapter.out.persistence.jooq;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.locpham.bookstore.orderservice.TestcontainersConfiguration;
import com.locpham.bookstore.orderservice.domain.model.Order;
import com.locpham.bookstore.orderservice.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class JooqOrderRepositoryImplTest {

    @MockitoBean private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired private JooqOrderRepositoryImpl jooqOrderRepository;

    @Test
    void saveAndFindById() {
        var order = Order.createAccepted("1234567890", "Book", 9.99, 2);

        StepVerifier.create(
                        jooqOrderRepository
                                .save(order)
                                .flatMap(saved -> jooqOrderRepository.findById(saved.id())))
                .assertNext(
                        found -> {
                            assertThat(found.book().isbn()).isEqualTo("1234567890");
                            assertThat(found.status()).isEqualTo(OrderStatus.ACCEPTED);
                        })
                .verifyComplete();
    }
}
