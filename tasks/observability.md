# Observability Plan — Structured Logging & Distributed Tracing

> Goal: Production-grade observability for all services using `spring-boot-starter-opentelemetry` + Grafana stack. One trace spans the full request flow across HTTP and Kafka boundaries. Structured JSON logs with trace correlation. Grafana dashboards with log↔trace navigation.

> Architecture: Spring Boot OTel Starter (Micrometer + OTLP) → Grafana Alloy → Tempo/Loki/Prometheus → Grafana

## Recommended Reading (Before Implementation)

| # | Document | Why |
|---|---|---|
| 1 | [OpenTelemetry with Spring Boot (Spring.io)](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot/) | Official Spring Boot 4 + OTel integration guide — the `spring-boot-starter-opentelemetry` approach |
| 2 | [Structured Logging in Spring Boot 3.4 (Spring.io)](https://spring.io/blog/2024/08/23/structured-logging-in-spring-boot-3-4) | Native JSON structured logging with ECS format — zero external dependencies |
| 3 | [Context Propagation with Project Reactor (Spring.io)](https://spring.io/blog/2023/03/28/context-propagation-with-project-reactor-1-the-basics) | Critical for WebFlux services — how trace context flows in reactive chains |
| 4 | [Grafana: Emit contextualized JSON logs with Java](https://grafana.com/docs/opentelemetry/collector/opentelemetry-collector/java-json-logs/) | Grafana's official guide for Java JSON logs with OTEL MDC fields |
| 5 | [Grafana Alloy: Collect OpenTelemetry data and forward to LGTM](https://grafana.com/docs/alloy/latest/collect/opentelemetry-to-lgtm-stack/) | Alloy pipeline config for routing OTLP to Tempo/Loki/Prometheus |
| 6 | [Micrometer Context Propagation docs](https://docs.micrometer.io/context-propagation/reference/purpose.html) | How ContextPropagatingTaskDecorator and Reactor context bridge work |
| 7 | [Spring Boot 4 OTel Guide (community)](https://gist.github.com/pramath01/32934e87ba927175d99236f5bad705ad) | Pitfalls, WebFlux compatibility, why NOT to mix Java Agent with starter |

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│  Spring Boot Services                               │
│  (spring-boot-starter-opentelemetry)                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐           │
│  │ catalog  │ │  order   │ │inventory │ ...        │
│  │  (MVC)   │ │(WebFlux) │ │(WebFlux) │           │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘           │
│       │             │             │                  │
│       └─────────────┼─────────────┘                  │
│                     ↓                                │
│         OTLP (gRPC/HTTP :4317/:4318)                 │
└─────────────────────┬───────────────────────────────┘
                      ↓
         ┌────────────────────────┐
         │    Grafana Alloy       │
         │  (central collector)   │
         └───┬────────┬────────┬──┘
             ↓        ↓        ↓
         ┌──────┐ ┌──────┐ ┌──────────┐
         │Tempo │ │ Loki │ │Prometheus│
         └──────┘ └──────┘ └──────────┘
                      ↓
                  ┌────────┐
                  │Grafana │
                  └────────┘
```

## Current State

| Service | Type | DB | OTel Starter | Structured Logging | OTLP Config |
|---|---|---|---|---|---|
| config-service | MVC | none | ❌ (has agent JAR only) | ❌ | partial (docker-compose) |
| catalog-service | MVC | PostgreSQL (JDBC) | ❌ (has agent JAR only) | ❌ | ❌ |
| order-service | WebFlux | PostgreSQL (R2DBC/jOOQ) | ❌ (has agent JAR only) | ❌ | ❌ |
| inventory-service | WebFlux | PostgreSQL (R2DBC/jOOQ) | ❌ (has agent JAR only) | ❌ | ❌ |
| dispatcher-service | Spring Cloud Function | none | ❌ (has agent JAR only) | ❌ | ❌ |
| search-service | WebFlux | Elasticsearch | ❌ | ❌ | ❌ |
| edge-service | WebFlux (Gateway) | Redis | ❌ (has agent JAR only) | ❌ | ❌ |

## Key Design Decisions

1. **`spring-boot-starter-opentelemetry` (not Java Agent)** — native Spring Boot 4 integration, full WebFlux/Reactor support, Micrometer-based, no bytecode manipulation, no agent version conflicts, works with GraalVM/AOT
2. **Do NOT mix Java Agent with starter** — causes duplicate spans and instrumentation conflicts
3. **Spring Boot native `logging.structured.format.console=ecs`** — built-in, no external dependency
4. **Grafana Alloy as central collector** — decouples services from backends, enables batching/retry
5. **Replace Fluent Bit** — logs flow via OTLP instead of fluentd driver
6. **Keep Prometheus scrape as fallback** — coexists with OTLP remote write
7. **Micrometer Tracing bridge** — auto-instruments WebClient, RestClient, RestTemplate, R2DBC, JDBC, Kafka via Micrometer Observation API

---

## Task 1: Replace OTel Java Agent with `spring-boot-starter-opentelemetry`

- [ ] **Remove** `otelAgentVersion` ext property and `runtimeOnly "io.opentelemetry.javaagent:opentelemetry-javaagent:..."` from:
  - [ ] `catalog-service/build.gradle`
  - [ ] `order-service/build.gradle`
  - [ ] `inventory-service/build.gradle`
  - [ ] `dispatcher-service/build.gradle`
  - [ ] `edge-service/build.gradle`
  - [ ] `config-service/build.gradle`
- [ ] **Add** `spring-boot-starter-opentelemetry` + required dependencies to ALL 7 services:
  ```groovy
  dependencies {
      // Spring Boot 4 native OTel starter — exports metrics, traces via OTLP
      implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'

      // Micrometer tracing bridge for OpenTelemetry (creates spans from observations)
      implementation 'io.micrometer:micrometer-tracing-bridge-otel'

      // OTLP log export via Logback appender
      implementation 'io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.12.0-alpha'
  }
  ```
- [ ] For **WebFlux services** (order, inventory, search, edge), also add context propagation:
  ```groovy
  dependencies {
      // Reactor context propagation (auto-propagates trace context in reactive chains)
      implementation 'io.micrometer:context-propagation'
  }
  ```
- [ ] For **Kafka/Spring Cloud Stream services** (order, inventory, dispatcher, catalog), ensure observation is enabled:
  ```groovy
  dependencies {
      // Spring Cloud Stream already includes Kafka observation support
      // No extra dependency needed — just enable via config
  }
  ```
- [ ] Add `logback-spring.xml` to each service's `src/main/resources/` for OTLP log export:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <configuration>
      <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
      <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

      <appender name="OTEL"
                class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
      </appender>

      <root level="INFO">
          <appender-ref ref="CONSOLE"/>
          <appender-ref ref="OTEL"/>
      </root>
  </configuration>
  ```
- [ ] Add `InstallOpenTelemetryAppender` bean to each service (or a shared config class):
  ```java
  @Component
  class InstallOpenTelemetryAppender implements InitializingBean {
      private final OpenTelemetry openTelemetry;

      InstallOpenTelemetryAppender(OpenTelemetry openTelemetry) {
          this.openTelemetry = openTelemetry;
      }

      @Override
      public void afterPropertiesSet() {
          OpenTelemetryAppender.install(this.openTelemetry);
      }
  }
  ```
- [ ] Add OTLP export configuration to `application.yml` (or centralized config):
  ```yaml
  management:
    tracing:
      sampling:
        probability: 1.0  # 100% for dev, lower for prod
    otlp:
      metrics:
        export:
          url: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/metrics
    opentelemetry:
      resource-attributes:
        service.name: ${spring.application.name}
        service.namespace: bookstore
        deployment.environment: ${SPRING_PROFILES_ACTIVE:default}
      tracing:
        export:
          otlp:
            endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
      logging:
        export:
          otlp:
            endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/logs
  ```
- [ ] For **WebFlux services**, add `ContextPropagatingTaskDecorator` bean:
  ```java
  @Configuration(proxyBeanMethods = false)
  class ContextPropagationConfig {
      @Bean
      ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
          return new ContextPropagatingTaskDecorator();
      }
  }
  ```
- [ ] Run `./gradlew build` on each service to verify no conflicts

### What gets auto-instrumented (no code changes)
- HTTP server requests (MVC controllers, WebFlux handlers)
- HTTP client requests (RestTemplate, RestClient, WebClient)
- JDBC database calls (catalog-service)
- R2DBC database calls (order, inventory)
- Trace ID + Span ID automatically added to log MDC
- W3C trace context propagated via HTTP headers automatically

### Verify
```bash
cd catalog-service && ./gradlew build
cd order-service && ./gradlew build
cd edge-service && ./gradlew build
# Start a service and verify traces are exported
cd catalog-service && ./gradlew bootRun &
sleep 5 && curl http://localhost:9001/books
# Check logs: should contain trace_id and span_id in MDC
```

### Demo
All 7 services compile and produce traces/metrics/logs via OTLP without Java Agent.

---

## Task 2: Configure structured JSON logging (ECS format)

- [ ] Add structured logging config to each service's prod profile.
- [ ] For centralized config services, add to `config/<service>-prod.yml`:
  ```yaml
  logging:
    structured:
      format:
        console: ecs
      ecs:
        service:
          name: ${spring.application.name}
          version: 0.0.1-SNAPSHOT
          environment: ${SPRING_PROFILES_ACTIVE:default}
  ```
- [ ] Files to update:
  - [ ] `config/catalog-service-prod.yml`
  - [ ] `config/order-service-prod.yml`
  - [ ] `config/inventory-service-prod.yml`
  - [ ] `config/dispatcher-service-prod.yml`
  - [ ] `config/edge-service-prod.yml`
  - [ ] `config/search-service-prod.yml`
  - [ ] `config-service/src/main/resources/application.yml` (add prod profile section)
- [ ] Remove old `logging.pattern.level` from `catalog-service` and `order-service` application.yml (superseded by ECS)
- [ ] Keep local dev profile with default human-readable format (no change needed)

### Verify
```bash
cd catalog-service && ./gradlew bootRun --args='--spring.profiles.active=prod' &
sleep 5 && curl http://localhost:9001/books
# stdout should show ECS JSON with trace_id, span_id
```

### Demo
Service stdout shows ECS JSON with `trace_id` and `span_id` populated on every request.

---

## Task 3: Add Grafana Alloy to docker-compose

- [ ] Create `polar-deployment/docker/platform/alloy/config.alloy`:
  ```alloy
  otelcol.receiver.otlp "default" {
    grpc { endpoint = "0.0.0.0:4317" }
    http { endpoint = "0.0.0.0:4318" }
    output {
      metrics = [otelcol.processor.batch.default.input]
      logs    = [otelcol.processor.batch.default.input]
      traces  = [otelcol.processor.batch.default.input]
    }
  }

  otelcol.processor.batch "default" {
    output {
      metrics = [otelcol.exporter.prometheus.default.input]
      logs    = [otelcol.exporter.loki.default.input]
      traces  = [otelcol.exporter.otlp.tempo.input]
    }
  }

  otelcol.exporter.otlp "tempo" {
    client {
      endpoint = "tempo:4317"
      tls { insecure = true }
    }
  }

  otelcol.exporter.loki "default" {
    forward_to = [loki.write.default.receiver]
  }

  loki.write "default" {
    endpoint {
      url = "http://loki:3100/loki/api/v1/push"
    }
  }

  otelcol.exporter.prometheus "default" {
    forward_to = [prometheus.remote_write.default.receiver]
  }

  prometheus.remote_write "default" {
    endpoint {
      url = "http://prometheus:9090/api/v1/write"
    }
  }
  ```
- [ ] Add Alloy service to `docker-compose.yml`:
  ```yaml
  alloy:
    image: grafana/alloy:latest
    container_name: alloy
    command: ["run", "/etc/alloy/config.alloy", "--server.http.listen-addr=0.0.0.0:12345"]
    ports:
      - "4317:4317"
      - "4318:4318"
      - "12345:12345"
    volumes:
      - ./platform/alloy/config.alloy:/etc/alloy/config.alloy
    depends_on:
      - tempo
      - loki
      - prometheus
    networks:
      - polar-network
  ```
- [ ] Update Prometheus to accept remote write:
  ```yaml
  prometheus:
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--web.enable-remote-write-receiver'
  ```

### Verify
```bash
docker compose up alloy -d
curl http://localhost:12345/ready  # should return 200
# Open http://localhost:12345/graph to see pipeline
```

### Demo
Alloy running with healthy pipeline graph at localhost:12345.

---

## Task 4: Configure all services to send OTLP to Alloy

- [ ] Update docker-compose environment for each service (NO Java Agent needed):
  ```yaml
  environment:
    - SPRING_PROFILES_ACTIVE=prod
    - OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4318
    - OTEL_RESOURCE_ATTRIBUTES=service.namespace=bookstore,deployment.environment=docker
  ```
- [ ] Remove old Java Agent env vars from config-service:
  - Remove `JAVA_TOOL_OPTIONS=-javaagent:...`
  - Remove `OTEL_SERVICE_NAME=...` (now set via `spring.application.name`)
  - Remove `OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317` (now points to Alloy)
  - Remove `OTEL_METRICS_EXPORTER=none`
- [ ] Services to configure:
  - [ ] config-service
  - [ ] catalog-service
  - [ ] order-service
  - [ ] inventory-service
  - [ ] dispatcher-service
  - [ ] search-service
  - [ ] edge-service
- [ ] Remove `logging.driver: fluentd` from config-service
- [ ] Add `depends_on: alloy` to each service
- [ ] The starter auto-configures OTLP export using the `OTEL_EXPORTER_OTLP_ENDPOINT` env var
- [ ] Spring Boot uses HTTP/protobuf (port 4318) by default — matches Alloy's HTTP receiver

### Verify
```bash
docker compose up -d
sleep 30
curl http://localhost:9000/books  # through edge-service
# Check Grafana → Tempo → should see trace
# Check Grafana → Loki → should see JSON logs with trace_id
```

### Demo
Single curl → trace in Tempo, correlated JSON logs in Loki, metrics in Prometheus.

---

## Task 5: Enable Kafka trace context propagation

- [ ] Spring Cloud Stream + Micrometer Tracing supports observation-based Kafka instrumentation
- [ ] Enable Kafka observation in config (per service that uses Kafka):
  ```yaml
  spring:
    kafka:
      listener:
        observation-enabled: true
      template:
        observation-enabled: true
  ```
- [ ] For Spring Cloud Stream binder, enable observation:
  ```yaml
  spring:
    cloud:
      stream:
        kafka:
          binder:
            enable-observation: true
  ```
- [ ] W3C `traceparent` header will be injected/extracted automatically via Micrometer Tracing bridge
- [ ] Add config to:
  - [ ] `config/catalog-service.yml` (produces book events)
  - [ ] `config/order-service.yml` (produces/consumes order events)
  - [ ] `config/inventory-service.yml` (consumes/produces inventory events)
  - [ ] `config/dispatcher-service.yml` (consumes/produces dispatch events)
- [ ] Verify full trace flow:
  - edge-service (HTTP) → order-service (HTTP) → Kafka produce
  - Kafka consume → inventory-service → Kafka produce
  - Kafka consume → dispatcher-service → Kafka produce
  - Kafka consume → order-service (state update)

### Verify
```bash
# Submit order through edge-service
curl -X POST http://localhost:9000/orders -H "Content-Type: application/json" -d '{"isbn":"1234567890","quantity":1}'
# Open Grafana → Tempo → search by service "order-service"
# Trace should show spans across: edge → order → kafka → inventory → dispatcher
```

### Demo
One trace in Tempo shows the complete order flow spanning HTTP + Kafka across 4+ services.

---

## Task 6: Add business context to logs (MDC enrichment)

- [ ] **MVC services** (catalog-service): Add `OncePerRequestFilter` to extract userId from JWT and put in MDC
- [ ] **WebFlux services** (order, inventory, search, edge): Use Reactor Context with `contextWrite` for MDC propagation
- [ ] Add business MDC in key methods:
  - [ ] `order-service`: `MDC.put("orderId", orderId)` in order submission
  - [ ] `catalog-service`: `MDC.put("isbn", isbn)` in book CRUD
  - [ ] `inventory-service`: `MDC.put("orderId", orderId)` in reservation
  - [ ] `search-service`: `MDC.put("query", query)` in search operations
- [ ] Use SLF4J fluent API where cleaner:
  ```java
  logger.atInfo().addKeyValue("orderId", orderId).log("Order submitted");
  ```
- [ ] ECS format automatically includes all MDC/keyValue fields in JSON output

### Verify
```bash
# Query Loki for business context
# {service_name="order-service"} | json | orderId="<specific-id>"
```

### Demo
Loki query by `orderId` returns all logs for that specific order across its lifecycle.

---

## Task 7: Update Grafana datasources for log↔trace correlation

- [ ] Update `polar-deployment/docker/platform/grafana/datasources/datasource.yml`:
  ```yaml
  apiVersion: 1

  deleteDatasources:
    - name: Prometheus
    - name: Tempo
    - name: Loki

  datasources:
    - name: Prometheus
      type: prometheus
      uid: prometheus-pxloc-vitale
      access: proxy
      orgId: 1
      url: http://prometheus:9090
      isDefault: false
      version: 1
      editable: true

    - name: Tempo
      type: tempo
      uid: tempo-pxloc-vitale
      access: proxy
      orgId: 1
      url: http://tempo:3100
      isDefault: false
      version: 1
      editable: true
      jsonData:
        tracesToLogsV2:
          datasourceUid: loki-pxloc-vitale
          filterByTraceID: true
          filterBySpanID: false
          spanStartTimeShift: "-1h"
          spanEndTimeShift: "1h"
        tracesToMetrics:
          datasourceUid: prometheus-pxloc-vitale
        serviceMap:
          datasourceUid: prometheus-pxloc-vitale
        nodeGraph:
          enabled: true

    - name: Loki
      type: loki
      uid: loki-pxloc-vitale
      access: proxy
      orgId: 1
      url: http://loki:3100
      isDefault: true
      version: 1
      editable: true
      jsonData:
        derivedFields:
          - datasourceUid: tempo-pxloc-vitale
            matcherRegex: '"trace_id":"([a-f0-9]+)"'
            matcherType: label
            name: TraceID
            url: "$${__value.raw}"
            urlDisplayLabel: "View Trace"
  ```

### Verify
- Open Grafana → Explore → Loki → query logs → "View Trace" link appears
- Click → opens trace in Tempo
- Open Tempo trace → "Logs for this span" link works

### Demo
Bidirectional navigation between logs and traces in Grafana UI.

---

## Task 8: Create Grafana observability dashboard

- [ ] Create `polar-deployment/docker/platform/grafana/dashboards/observability.json`
- [ ] Dashboard panels:
  - [ ] Service Map (Tempo service graph)
  - [ ] Request Rate by service (Prometheus: `http_server_request_duration_seconds_count`)
  - [ ] Error Rate by service (5xx responses)
  - [ ] P95 Latency by service (histogram quantile)
  - [ ] Recent Traces table (Tempo)
  - [ ] Log Volume by service (Loki)
  - [ ] Error Logs with trace links (Loki, level=ERROR)
- [ ] Add template variable: `$service` dropdown
- [ ] Use OTel semantic convention metric names (agent v2.x: `http.server.request.duration`)

### Verify
```bash
# Open Grafana → Dashboards → Observability
# All panels should show data after making requests
```

### Demo
Live dashboard showing service map, request rates, latencies, and clickable trace/log links.

---

## Task 9: Update Prometheus for OTLP remote write

- [ ] Add command flags to Prometheus in docker-compose:
  ```yaml
  prometheus:
    image: quay.io/prometheus/prometheus:v2.52.0
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--web.enable-remote-write-receiver'
  ```
- [ ] Keep existing scrape configs as fallback
- [ ] Alloy pushes OTLP-converted metrics via remote write
- [ ] OTel metrics use semantic conventions: `http.server.request.duration`, `jvm.memory.used`

### Verify
```bash
# Check Prometheus UI → Status → TSDB Stats
# Should show both scraped and remote-written metrics
curl http://localhost:9090/api/v1/query?query=http_server_request_duration_seconds_count
```

### Demo
Both scraped Micrometer metrics and OTLP-pushed metrics visible in Prometheus.

---

## Task 10: Remove Fluent Bit and clean up legacy logging config

- [ ] Remove `fluent-bit` service from `docker-compose.yml`
- [ ] Remove `logging.driver: fluentd` from config-service in docker-compose
- [ ] Remove `polar-deployment/docker/platform/fluent-bit/` directory
- [ ] Update Loki: remove `depends_on: fluent-bit`
- [ ] Remove old `logging.pattern.level` from:
  - [ ] `catalog-service/src/main/resources/application.yml`
  - [ ] `order-service/src/main/resources/application.yml`
- [ ] Loki now receives logs from Alloy (OTLP path) instead of Fluent Bit

### Verify
```bash
docker compose up -d
docker compose ps  # no fluent-bit container
# Logs still appear in Grafana → Loki
```

### Demo
Cleaner stack — no Fluent Bit, logs flowing via OTLP → Alloy → Loki.

---

## Task 11: End-to-end verification and documentation

- [ ] Create `docs/observability.md` with:
  - [ ] Architecture overview
  - [ ] How to start the stack
  - [ ] How to verify traces/logs/metrics
  - [ ] How to add custom spans in application code
  - [ ] Environment variables reference for production deployment
  - [ ] Troubleshooting guide
- [ ] Verification checklist:
  - [ ] Start stack: `docker compose up -d`
  - [ ] Submit order: `curl -X POST http://localhost:9000/orders -H "Content-Type: application/json" -d '{"isbn":"1234567890","quantity":1}'`
  - [ ] Tempo: full trace spanning edge → order → Kafka → inventory → dispatcher
  - [ ] Loki: JSON logs with trace_id, searchable by orderId
  - [ ] Prometheus: metrics from all 7 services
  - [ ] Dashboard: service map shows connections
  - [ ] Log→Trace: click log line → opens trace
  - [ ] Trace→Log: click trace → shows related logs

### Demo
Follow the doc from scratch → complete observability working in under 5 minutes.

---

## Definition of Done

- [ ] All 7 services emit ECS JSON logs with `trace_id` and `span_id` in prod profile
- [ ] All 7 services send traces/logs/metrics via OTLP to Alloy
- [ ] One trace spans full order flow: edge → order → Kafka → inventory → dispatcher
- [ ] Grafana: log→trace and trace→log correlation works bidirectionally
- [ ] Grafana dashboard shows service map, RED metrics, and error logs
- [ ] No Fluent Bit dependency
- [ ] Local dev profile still shows human-readable logs
- [ ] `docker compose up` starts the full observability stack
- [ ] Documentation in `docs/observability.md`

---

## Risk Register

| Risk | Mitigation |
|---|---|
| `spring-boot-starter-opentelemetry` missing instrumentation vs Java Agent | Starter covers HTTP, JDBC, R2DBC, WebClient, Kafka via Micrometer Observation; add manual `@Observed` for gaps |
| Mixing Java Agent with starter causes duplicate spans | Remove ALL agent JARs and `JAVA_TOOL_OPTIONS` env vars; use ONLY the starter |
| Alloy fails to connect to backends | Health checks + Alloy UI at :12345 for debugging |
| Kafka trace propagation not working | Enable `observation-enabled: true` on Kafka binder/listener/template; verify with Tempo |
| WebFlux loses trace context in reactive chains | `context-propagation` library + `ContextPropagatingTaskDecorator` bean handles this |
| Prometheus remote write conflicts with scrape | Different metric names (OTel semantic vs Micrometer); both coexist safely |
| Loki overwhelmed by log volume | Alloy batch processor provides buffering; add rate limiting if needed |
| ECS format not available in Spring Boot 4.0.3 | Available since 3.4; Spring Boot 4.x inherits it. Fallback: logstash format |
| Logback OTLP appender is alpha version | Stable enough for production; only the version suffix is alpha per OTel versioning policy |
