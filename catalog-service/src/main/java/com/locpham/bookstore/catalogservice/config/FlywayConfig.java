package com.locpham.bookstore.catalogservice.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).load();
    }
}
