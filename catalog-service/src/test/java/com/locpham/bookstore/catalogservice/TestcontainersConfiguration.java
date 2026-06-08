package com.locpham.bookstore.catalogservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up a real PostgreSQL container for tests that exercise Postgres-specific behaviour (the
 * {@code outbox_event.payload} {@code jsonb} column written via {@code
 * JsonbPayloadWritingConverter}). H2 — used by the rest of the suite — cannot bind a {@code jsonb}
 * {@code PGobject}, so those tests must run against Postgres.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.12"));
    }
}
