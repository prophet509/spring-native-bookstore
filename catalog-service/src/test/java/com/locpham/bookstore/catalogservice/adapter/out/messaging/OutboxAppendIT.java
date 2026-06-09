package com.locpham.bookstore.catalogservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.catalogservice.TestcontainersConfiguration;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the default (outbox) publisher writes a well-formed {@code outbox_event} row that
 * Debezium will route. Runs against a Testcontainers PostgreSQL instance because the {@code
 * payload} column is {@code jsonb} — a Postgres-specific type the {@code
 * JsonbPayloadWritingConverter} binds via {@code PGobject}, which H2 cannot represent.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OutboxAppendIT {

    @Autowired private OutboxBookEventPublisher publisher;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void publishBookCreated_writesOutboxRowWithDestinationAndPayload() {
        var isbn = "OUTBOX-" + System.nanoTime();
        var book = Book.build(isbn, "Title", "Author", 9.90, "Polarsophia");

        publisher.publishBookCreated(book);

        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT aggregate_type, aggregate_id, type, destination,"
                                + " CAST(payload AS VARCHAR) AS payload"
                                + " FROM outbox_event WHERE aggregate_id = ?",
                        isbn);

        assertThat(row.get("aggregate_type")).isEqualTo("book");
        assertThat(row.get("type")).isEqualTo("BookCreated");
        assertThat(row.get("destination")).isEqualTo("book.created");
        assertThat(row.get("payload").toString())
                .contains(isbn)
                .contains("Title")
                .contains("Author");
    }
}
