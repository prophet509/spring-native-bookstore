package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.inventoryservice.TestcontainersConfiguration;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
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
class JooqInventoryRepositoryImplTest {

    @Autowired private JooqInventoryRepositoryImpl inventoryRepository;

    @MockitoBean private ReactiveJwtDecoder jwtDecoder;

    @Test
    void saveAndFindByIsbn() {
        var item = InventoryItem.create("123", 10);

        StepVerifier.create(
                        inventoryRepository
                                .save(item)
                                .flatMap(saved -> inventoryRepository.findByIsbn(saved.isbn())))
                .assertNext(
                        found -> {
                            assertThat(found.isbn()).isEqualTo("123");
                            assertThat(found.availableQuantity()).isEqualTo(10);
                        })
                .verifyComplete();
    }

    @Test
    void save_shouldFailWithOptimisticLockWhenVersionStale() {
        var item = InventoryItem.create("999", 5);

        Mono<Void> flow =
                inventoryRepository
                        .save(item)
                        .flatMap(
                                saved -> {
                                    // First update bumps the version in the DB.
                                    return inventoryRepository
                                            .save(saved.adjust(1))
                                            .thenReturn(saved);
                                })
                        .flatMap(
                                stale -> {
                                    // Attempt an update using the old version (stale).
                                    var staleUpdate =
                                            new InventoryItem(
                                                    stale.id(),
                                                    stale.isbn(),
                                                    stale.availableQuantity() - 1,
                                                    stale.reservedQuantity(),
                                                    stale.version());
                                    return inventoryRepository.save(staleUpdate).then();
                                });

        StepVerifier.create(flow)
                .expectErrorSatisfies(
                        ex -> assertThat(ex).isInstanceOf(OptimisticLockingFailureException.class))
                .verify();
    }
}
