package com.locpham.bookstore.catalogservice.config;

import com.locpham.bookstore.catalogservice.adapter.out.persistence.JsonbPayloadWritingConverter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

@Configuration
@EnableJdbcAuditing
public class DataConfig {

    /**
     * Registers the {@link JsonbPayloadWritingConverter} so the {@code outbox_event.payload} column
     * is written as Postgres {@code jsonb} instead of {@code varchar}.
     */
    @Bean
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(List.of(new JsonbPayloadWritingConverter()));
    }
}
