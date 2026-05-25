package com.locpham.bookstore.inventoryservice.bootstrap.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Configuration
public class ObservabilityConfig {

    private final OpenTelemetry openTelemetry;

    public ObservabilityConfig(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @PostConstruct
    public void registerOpenTelemetryAppender() {
        OpenTelemetryAppender.install(openTelemetry);
        Hooks.enableAutomaticContextPropagation();
    }
}
