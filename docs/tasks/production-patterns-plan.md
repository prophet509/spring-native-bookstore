# Giai Doan 4 — Production Patterns Implementation Plan

> Muc tieu: bien he thong sau Security thanh he thong production-grade: API loi ro rang, event khong mat khi Kafka loi, consumer an toan voi duplicate event, service khong cascade failure, va co observability du de debug flow that.

> Nguyen tac: lam theo tung service, tung pattern, co verify command rieng. Khong sua `docs/tasks/senior-roadmap.md` trong plan nay.

## Phase Summary

| Epic | Ten | Output chinh | Services |
|---|---|---|---|
| 4.0 | Baseline Audit | Biet hien trang truoc khi code | all |
| 4.1 | RFC 7807 Error Contract | Loi API dong nhat | catalog, order, inventory, search, edge |
| 4.2 | Validation Hardening | Request fail som, dung status | catalog, order, inventory, search |
| 4.3 | Structured Logging | Log co trace/user/order/isbn | all |
| 4.4 | Observability Baseline | Metrics, traces, dashboards | all |
| 4.5 | Outbox Pattern | Khong mat event khi Kafka down | order, catalog |
| 4.6 | Idempotent Consumers | Duplicate event khong gay sai state | inventory, search, dispatcher, order |
| 4.7 | Resilience4j | Timeout, retry, circuit breaker | edge, order, search |
| 4.8 | Redis Caching | Giam DB reads co invalidation | catalog |
| 4.9 | Saga Choreography | Place order co compensation | order, inventory, dispatcher |
| 4.10 | Final Hardening | Failure tests + docs | all |

## Recommended Order

1. `4.0 Baseline Audit`: khong code, chi map hien trang.
2. `4.1 + 4.2`: error contract va validation, lam truoc de API behavior on dinh.
3. `4.3 + 4.4`: logging/tracing/metrics, lam truoc Outbox/Saga de debug duoc.
4. `4.5 + 4.6`: Outbox va idempotency, lam truoc retry/Saga.
5. `4.7`: Resilience4j, chi retry khi idempotency da ro.
6. `4.8`: cache, sau khi correctness da on.
7. `4.9`: Saga, tich hop distributed flow cuoi cung.
8. `4.10`: chaos/failure tests, docs, cleanup.

## Definition Of Done For Whole Phase

- [ ] Moi touched service pass `./gradlew test`.
- [ ] Spotless pass cho `catalog-service` va `order-service` neu co touch.
- [ ] Tat ca API loi tra ve RFC 7807-compatible body.
- [ ] Validation loi co field-level details.
- [ ] Log prod la JSON, co `service`, `traceId`, `spanId`, va context field phu hop.
- [ ] Khong log JWT, cookie, password, secret.
- [ ] `order-service` co Outbox, Kafka down khong lam mat order event.
- [ ] `catalog-service` co Outbox cho book events hoac co task follow-up ro neu chua lam trong sprint.
- [ ] Consumer duplicate event safe.
- [ ] Circuit breaker/timeout expose metric qua actuator.
- [ ] It nhat mot trace full flow: edge -> order -> Kafka -> inventory -> dispatcher.
- [ ] Saga happy path va compensation path co test.

---

## 4.0 Baseline Audit

> Goal: biet chinh xac service nao da co gi, thieu gi. Khong code truoc khi audit.

### 4.0.1 Inventory Files And Patterns

- [ ] Liet ke exception classes hien co trong tung service.
- [ ] Liet ke controller/request DTO trong tung service.
- [ ] Liet ke Kafka producer/consumer hien co.
- [ ] Liet ke synchronous HTTP calls hien co.
- [ ] Liet ke actuator endpoints dang expose.
- [ ] Liet ke logging config hien co.
- [ ] Liet ke Flyway migrations hien co.

### 4.0.2 Create Baseline Notes

- [ ] Tao section `Baseline` trong file nay hoac file phu neu can.
- [ ] Ghi ro service nao MVC, service nao WebFlux.
- [ ] Ghi ro service nao dung JDBC, R2DBC, jOOQ, Elasticsearch.
- [ ] Ghi ro test command nho nhat cho tung service.

### Verify

```bash
git status --short
```

### Deliverable

- [ ] Mot baseline table de biet thu tu implement khong bi doan mo.

---

## 4.1 RFC 7807 Problem Details

> Goal: moi loi API deu co contract dong nhat. Client chi can parse mot shape.

### 4.1.1 Define Error Catalog

**File moi de xuat:** `docs/api-error-catalog.md` hoac `docs/tasks/api-error-catalog.md`

- [ ] Dinh nghia error type URI format: `https://bookstore.api/errors/{code}`.
- [ ] Dinh nghia common fields: `type`, `title`, `status`, `detail`, `instance`, `timestamp`, `traceId`.
- [ ] Dinh nghia optional fields: `errors`, `service`, `errorCode`.
- [ ] Tao bang error codes:

| Code | Status | Meaning |
|---|---:|---|
| `validation-failed` | 400 | Request format/field invalid |
| `book-not-found` | 404 | Book does not exist |
| `order-not-found` | 404 | Order does not exist |
| `inventory-not-found` | 404 | Inventory item does not exist |
| `insufficient-stock` | 422 | Stock cannot satisfy reservation |
| `order-state-conflict` | 409 | Order state transition invalid |
| `duplicate-event` | 409 | Event already processed when surfaced through API |
| `downstream-unavailable` | 503 | Dependency unavailable |
| `unauthorized` | 401 | Authentication required |
| `forbidden` | 403 | Authenticated but no permission |

### 4.1.2 Shared Design Decision

- [ ] Quyet dinh dung native `org.springframework.http.ProblemDetail` thay vi custom `ApiError`, vi Spring Boot 4 ho tro san.
- [ ] Neu can field `errors`, dung `problemDetail.setProperty("errors", fieldErrors)`.
- [ ] Neu can `traceId`, dung `problemDetail.setProperty("traceId", traceId)`.
- [ ] Khong tao shared library luc dau, tranh over-engineering. Copy minimal handler per service truoc.

### 4.1.3 `catalog-service` Tasks

**Expected files:**

- `catalog-service/src/main/java/.../web/GlobalExceptionHandler.java`
- `catalog-service/src/test/java/.../web/GlobalExceptionHandlerTest.java` hoac controller tests hien co

Tasks:

- [ ] Map `BookNotFoundException` to `404 book-not-found`.
- [ ] Map validation exceptions to `400 validation-failed`.
- [ ] Map malformed JSON to `400 malformed-request`.
- [ ] Map unexpected exceptions to `500 internal-server-error` without stack trace.
- [ ] Add `traceId` extraction from current trace context or request header fallback.
- [ ] Add tests for GET missing ISBN.
- [ ] Add tests for POST invalid body.
- [ ] Add tests to assert `Content-Type` is `application/problem+json` if Spring emits it.

Verify:

```bash
cd catalog-service && ./gradlew test --tests '*Controller*'
```

### 4.1.4 `order-service` Tasks

**Expected files:**

- `order-service/src/main/java/.../adapter/in/web/GlobalExceptionHandler.java`
- `order-service/src/test/java/.../adapter/in/web/OrderControllerTest.java`

Tasks:

- [ ] Map `OrderNotFoundException` to `404 order-not-found`.
- [ ] Map invalid state transitions to `409 order-state-conflict`.
- [ ] Map business validation to `422 order-rejected` where appropriate.
- [ ] Handle WebFlux validation exceptions.
- [ ] Ensure reactive errors are not swallowed into generic 500.
- [ ] Add tests for invalid submit order request.
- [ ] Add tests for missing order lookup.

Verify:

```bash
cd order-service && ./gradlew test --tests '*OrderControllerTest*'
```

### 4.1.5 `inventory-service` Tasks

**Expected files:**

- `inventory-service/src/main/java/.../adapter/in/web/GlobalExceptionHandler.java`
- `inventory-service/src/test/java/.../adapter/in/web/InventoryControllerTest.java`

Tasks:

- [ ] Map `InsufficientStockException` to `422 insufficient-stock`.
- [ ] Map missing inventory to `404 inventory-not-found`.
- [ ] Map reservation conflict to `409 reservation-conflict`.
- [ ] Add tests for insufficient stock API response if endpoint exposes reservation.

Verify:

```bash
cd inventory-service && ./gradlew test --tests '*InventoryControllerTest*'
```

### 4.1.6 `search-service` Tasks

Tasks:

- [ ] Map invalid query params to `400 validation-failed`.
- [ ] Map Elasticsearch unavailable to `503 search-unavailable`.
- [ ] Map empty results to normal `200` with empty list, not `404`.
- [ ] Add tests for invalid `page`, `size`, and query length.

Verify:

```bash
cd search-service && ./gradlew test --tests '*SearchController*'
```

### Acceptance Criteria

- [ ] Same shape across all services.
- [ ] Domain exceptions are not returned as plain text.
- [ ] Stack traces never appear in response bodies.
- [ ] Tests assert `type`, `title`, `status`, and `traceId`.

---

## 4.2 Validation Hardening

> Goal: format errors fail at API boundary, business errors fail in domain/application layer.

### 4.2.1 Validation Matrix

- [ ] Create table of request DTOs and fields.
- [ ] Mark which fields require `@NotBlank`, `@NotNull`, `@Positive`, `@PositiveOrZero`, `@Size`, `@Min`, `@Max`.
- [ ] Decide if custom ISBN validator is worth doing now.

### 4.2.2 `catalog-service`

- [ ] Validate ISBN is not blank and valid ISBN-13 if custom validator exists.
- [ ] Validate title is not blank and max length.
- [ ] Validate author is not blank and max length.
- [ ] Validate price is positive.
- [ ] Validate publisher fields if exposed.
- [ ] Ensure controller method uses `@Valid`.
- [ ] Add tests for missing title, invalid ISBN, negative price.

### 4.2.3 `order-service`

- [ ] Validate submitted order has at least one line item.
- [ ] Validate quantity is positive.
- [ ] Validate ISBN per line item.
- [ ] Validate customer/user context is present if required after security phase.
- [ ] Add tests for empty order, negative quantity, blank ISBN.

### 4.2.4 `inventory-service`

- [ ] Validate stock item ISBN.
- [ ] Validate quantity cannot be negative for available stock.
- [ ] Validate reservation quantity is positive.
- [ ] Add tests for invalid stock update.

### 4.2.5 `search-service`

- [ ] Validate `q` max length to avoid expensive queries.
- [ ] Validate `page >= 0`.
- [ ] Validate `size` has safe max, for example `size <= 100`.
- [ ] Validate sort field whitelist.
- [ ] Add tests for invalid page/size/sort.

### Acceptance Criteria

- [ ] API invalid input returns `400 validation-failed`.
- [ ] Business rule violation returns `409` or `422`, not `400`.
- [ ] Tests cover every public mutation endpoint.

---

## 4.3 Structured Logging And Correlation

> Goal: debug incident by searching logs, not by guessing.

### 4.3.1 Logging Standard

JSON fields required in prod/container profile:

| Field | Required | Notes |
|---|---|---|
| `timestamp` | yes | ISO-8601 |
| `level` | yes | INFO/WARN/ERROR |
| `service` | yes | app name |
| `traceId` | yes | from OpenTelemetry/Micrometer |
| `spanId` | yes | if available |
| `userId` | when available | from auth/header/principal |
| `orderId` | when available | order flow |
| `isbn` | when available | book/inventory/search flow |
| `eventId` | when available | Kafka/outbox flow |
| `eventType` | when available | Kafka/outbox flow |
| `message` | yes | human readable |

### 4.3.2 Dependencies And Config

- [ ] Add JSON logging dependency only where needed.
- [ ] Add `logback-spring.xml` or profile-specific logback config per service.
- [ ] Keep local profile readable.
- [ ] Keep prod/container profile JSON.
- [ ] Add masking for headers: `Authorization`, `Cookie`, `Set-Cookie`, `X-CSRF-TOKEN`.

### 4.3.3 MVC MDC Filter

- [ ] Implement request filter for MVC services.
- [ ] Populate `userId` from security context or `X-User-Id`.
- [ ] Populate route/method/status/duration.
- [ ] Clear MDC in `finally`.

### 4.3.4 WebFlux MDC Bridge

- [ ] Do not use ThreadLocal-only MDC blindly in reactive services.
- [ ] Use Reactor context or Micrometer context propagation.
- [ ] Verify logs inside reactive chain keep same trace context.

### 4.3.5 Business Logs

`order-service`:

- [ ] Log `order.submitted` with `orderId`, `userId`.
- [ ] Log `order.accepted` with `orderId`.
- [ ] Log `order.rejected` with reason.
- [ ] Log outbox publish success/failure with `eventId`.

`inventory-service`:

- [ ] Log `stock.reservation.requested`.
- [ ] Log `stock.reserved`.
- [ ] Log `stock.rejected` with reason.

`catalog-service`:

- [ ] Log book created/updated/deleted with `isbn`.
- [ ] Log outbox publish success/failure for book events.

`search-service`:

- [ ] Log index event consumed.
- [ ] Log indexing failure with `isbn`, `eventId`.

### Acceptance Criteria

- [ ] One order flow can be reconstructed from logs with `orderId`.
- [ ] One request can be reconstructed from logs with `traceId`.
- [ ] Sensitive headers/tokens are not present in logs.

---

## 4.4 Observability Baseline

> Goal: co traces, metrics, dashboards truoc khi vao distributed failure.

### 4.4.1 Actuator Standardization

For each service:

- [ ] Expose `health`, `info`, `metrics`, `prometheus`.
- [ ] Hide sensitive actuator endpoints in non-local profiles.
- [ ] Add health groups if useful: readiness/liveness.
- [ ] Add database health check.
- [ ] Add Kafka binder/consumer health if available.
- [ ] Add Elasticsearch health check for `search-service`.

Config target:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
```

### 4.4.2 Prometheus Metrics

- [ ] Add Prometheus registry dependency to services.
- [ ] Verify `/actuator/prometheus` returns metrics.
- [ ] Add JVM, HTTP, DB, cache, Kafka metrics.
- [ ] Add custom business metrics.

Custom metrics:

| Metric | Service | Type | Tags |
|---|---|---|---|
| `bookstore_orders_submitted_total` | order | counter | `source` |
| `bookstore_orders_accepted_total` | order | counter | none |
| `bookstore_orders_rejected_total` | order | counter | `reason` |
| `bookstore_outbox_pending` | order/catalog | gauge | `event_type` |
| `bookstore_outbox_publish_failures_total` | order/catalog | counter | `event_type` |
| `bookstore_stock_reserved_total` | inventory | counter | none |
| `bookstore_stock_rejected_total` | inventory | counter | `reason` |
| `bookstore_search_index_failures_total` | search | counter | `event_type` |
| `bookstore_search_latency_seconds` | search | timer | `query_type` |

### 4.4.3 Distributed Tracing

- [ ] Choose local backend: Grafana Tempo or Jaeger.
- [ ] Add OpenTelemetry Java agent or Micrometer Tracing bridge.
- [ ] Propagate W3C trace context over HTTP.
- [ ] Propagate trace context through Kafka headers.
- [ ] Add spans around outbox polling and Kafka publish.
- [ ] Add spans around consumer processing.
- [ ] Add spans around Elasticsearch query/index.

### 4.4.4 Local Observability Stack

**Expected files:**

- `polar-deployment/docker/observability/` or update compose stack.
- `prometheus.yml`
- Grafana dashboard JSON.
- Tempo/Jaeger config.

Tasks:

- [ ] Add Prometheus container.
- [ ] Add Grafana container.
- [ ] Add Tempo or Jaeger container.
- [ ] Add scrape config for every service.
- [ ] Add dashboard for service RED metrics: rate, errors, duration.
- [ ] Add dashboard for order business flow.
- [ ] Add dashboard for outbox pending/failed.

### Acceptance Criteria

- [ ] `curl /actuator/prometheus` works per service.
- [ ] A full order request produces a trace visible in Tempo/Jaeger.
- [ ] Grafana shows HTTP latency/error rate and business counters.

---

## 4.5 Outbox Pattern ✅

> Goal: save DB + create event in same transaction. Kafka publish can fail without losing event.
>
> **Status: COMPLETE.** Implemented with Debezium CDC (not polling-based). See `docs/tasks/saga-outbox-plan.md` for full details. Stress test passed: 1000 req / 50 concurrency = 100% success.

### 4.5A `order-service` Outbox ✅

**Implemented:**
- `V6__create_outbox_event.sql` migration with `outbox_event` table (id, aggregate_type, aggregate_id, type, destination, payload JSONB, trace_id, created_at)
- `OutboxOrderEventPublisher` inserts outbox row in same R2DBC transaction as order save via `JooqOutboxRepository`
- Debezium connector tails WAL → Outbox Event Router SMT → publishes to Kafka topic named in `destination` column
- `trace_id` column stores W3C traceparent, Debezium maps to Kafka header
- No polling-based `OutboxPoller` — Debezium provides at-least-once delivery from WAL

### 4.5B `catalog-service` Book Event Outbox ✅

**Implemented:**
- Same `V6__create_outbox_event.sql` migration
- `OutboxBookEventPublisher` uses Spring Data JDBC (`SpringDataOutboxRepository`) — same `@Transactional` as book save
- Covers `book.created`, `book.updated`, `book.deleted` events
- Debezium connector publishes to search-service consumer topics

### 4.5C `inventory-service` Outbox ✅

**Implemented:**
- Same `V6__create_outbox_event.sql` migration
- `OutboxInventoryEventPublisher` inserts via `JooqOutboxRepository` in same R2DBC transaction
- Covers both `reserved` (save + outbox) and `rejected` (outbox only) paths

### 4.5 Acceptance Criteria ✅

- [x] Kafka down: order persists and outbox row exists (Debezium catches up when Kafka returns)
- [x] Service restart: pending WAL entries still published by Debezium
- [x] Multiple Debezium instances: connector uses replication slot, single active instance
- [x] Outbox retention: `make outbox-cleanup` (SQL-based, OUTBOX_RETENTION_DAYS=7 default)

---

## 4.6 Idempotent Consumers ✅

> Goal: duplicate delivery is expected. Code must be safe.
>
> **Status: COMPLETE.** See `docs/tasks/saga-outbox-plan.md` §2.7 for test details.

### 4.6.1 Shared Event Envelope ✅

Events use existing message DTOs (e.g. `OrderCreatedMessage`, `InventoryDecisionMessage`, `BookCreatedMessage`). Debezium Outbox Event Router adds `eventType` and `traceparent` as Kafka headers.

### 4.6.2 `inventory-service` Idempotent Consumer ✅

**Implemented:** `IdempotentConsumerIT` (Testcontainers Postgres) — mocks Redis to always grant claim, exercises DB-level guard (`reservationPort.findByOrderId` short-circuit). Asserts two deliveries of same order id produce exactly one inventory deduction, one reservation row, one outbox row.

### 4.6.3 `search-service` Idempotent Indexing ✅

**Implemented:** Uses ISBN as Elasticsearch document ID. Upsert is idempotent by nature. `book.deleted` safely deletes by ISBN even if already deleted. Redelivery is harmless — decided NOT to add `processed_event` guard (see saga-outbox-plan.md §3.4).

### 4.6.4 `dispatcher-service` Idempotent Dispatch

- [ ] Track dispatched order IDs (follow-up if not yet implemented)

### 4.6.5 `order-service` Event Handlers ✅

**Implemented:** `ProcessInventoryDecisionService` handles `reserved`/`rejected` idempotently via order state transition guards. Invalid transitions are logged and do not corrupt state.

### Acceptance Criteria ✅

- [x] Replaying same event twice does not duplicate side effects (verified by IdempotentConsumerIT)
- [x] Consumer acknowledges duplicates instead of retry-looping forever
- [x] Duplicate handling is visible in logs/metrics

---

## 4.7 Resilience4j: Timeout, Retry, Circuit Breaker

> Goal: dependency failure is contained, measured, and fast-failing.

### 4.7.1 Dependency Audit

- [ ] Identify all HTTP clients in `edge-service` route filters and service clients.
- [ ] Identify any order -> catalog validation calls if present.
- [ ] Identify search -> Elasticsearch calls.
- [ ] Identify any dispatcher external simulation calls.
- [ ] Classify each call as read or write.
- [ ] Mark whether operation is idempotent.

### 4.7.2 Config Standard

**Expected config:** `config/<service>.yml`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      catalog-service:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-calls-in-half-open-state: 3
  retry:
    instances:
      catalog-service:
        max-attempts: 3
        wait-duration: 200ms
  timelimiter:
    instances:
      catalog-service:
        timeout-duration: 2s
```

### 4.7.3 `edge-service`

- [ ] Review current circuit breaker route config.
- [ ] Ensure each downstream route has timeout.
- [ ] Add fallback routes only where response is safe and useful.
- [ ] Ensure fallback does not hide security failures.
- [ ] Add metrics exposure.

### 4.7.4 `order-service`

- [ ] Wrap catalog lookups or external calls if present.
- [ ] Retry only idempotent reads.
- [ ] Do not retry order creation unless idempotency key exists.
- [ ] Add fallback: fail order submission with clear ProblemDetail if required dependency unavailable.

### 4.7.5 `search-service`

- [ ] Configure timeout around Elasticsearch operations.
- [ ] Circuit breaker for Elasticsearch unavailable.
- [ ] Return `503 search-unavailable` for search dependency outage.
- [ ] Do not retry expensive broad queries aggressively.

### 4.7.6 Tests

- [ ] Mock dependency timeout.
- [ ] Verify response is fast and controlled.
- [ ] Verify circuit opens after threshold.
- [ ] Verify metrics exist.

### Acceptance Criteria

- [ ] Dependency outage does not cause indefinite hangs.
- [ ] Circuit breaker state visible through actuator metrics.
- [ ] Retry policy does not duplicate writes.

---

## 4.8 Redis Caching

> Goal: reduce repeated read load without stale write bugs.

### 4.8.1 `catalog-service` Read-Through Cache

- [ ] Add Spring Cache + Redis dependency if not present.
- [ ] Configure cache TTL in `config/catalog-service.yml`.
- [ ] Cache `findBookByIsbn` by ISBN.
- [ ] Consider caching paged book list only if stable and invalidation is clear.
- [ ] Add `@CacheEvict` on create/update/delete.
- [ ] Add cache metrics.
- [ ] Add tests with cache disabled for unit tests.
- [ ] Add integration smoke test with Redis if useful.

### 4.8.2 Cache Rules

- [ ] Never cache authenticated user-specific response without user-aware key.
- [ ] Never cache errors by default.
- [ ] Use TTL to limit stale data.
- [ ] Invalidate on mutation.
- [ ] Document cache keys.

### Acceptance Criteria

- [ ] First lookup hits repository, second lookup hits cache.
- [ ] Update invalidates old cached book.
- [ ] Delete invalidates cached book.
- [ ] Cache can be disabled in test profile.

---

## 4.9 Saga Pattern — Place Order Choreography ✅

> Goal: coordinate order, inventory, and dispatch through events, no distributed transaction.
>
> **Status: COMPLETE.** See `docs/tasks/saga-outbox-plan.md` for full details. Stress test: 1000 orders / 50 concurrency = 100% DISPATCHED.

### 4.9.1 State Machine

Order states:

| State | Meaning | Allowed Next |
|---|---|---|
| `SUBMITTED` | Order accepted by API, waiting for stock | `ACCEPTED`, `REJECTED`, `CANCELLED` |
| `ACCEPTED` | Stock reserved, ready to dispatch | `DISPATCHED`, `CANCELLED` |
| `REJECTED` | Business rejection, usually stock unavailable | terminal |
| `CANCELLED` | Cancelled by timeout/user/system | terminal |
| `DISPATCHED` | Dispatched successfully | terminal |

Tasks:

- [ ] Implement transition guard in domain, not controller.
- [ ] Invalid transition throws domain exception mapped to `409`.
- [ ] Add unit tests for all valid and invalid transitions.

### 4.9.2 Event Contracts

Events:

- [ ] `order.submitted`: emitted by `order-service`.
- [ ] `stock.reserved`: emitted by `inventory-service`.
- [ ] `stock.rejected`: emitted by `inventory-service`.
- [ ] `order.accepted`: emitted by `order-service` after stock reserved.
- [ ] `order.rejected`: emitted by `order-service` after stock rejected.
- [ ] `order.dispatched`: emitted by `dispatcher-service`.

For each event:

- [ ] Define payload JSON.
- [ ] Define topic/binding name.
- [ ] Define producer service.
- [ ] Define consumer service.
- [ ] Define idempotency key.
- [ ] Add contract test or serialization test.

### 4.9.3 Happy Path

```text
POST /orders
order-service saves SUBMITTED + outbox order.submitted
inventory-service consumes order.submitted
inventory-service reserves stock + emits stock.reserved
order-service consumes stock.reserved
order-service marks ACCEPTED + emits order.accepted
dispatcher-service consumes order.accepted
dispatcher-service dispatches + emits order.dispatched
order-service consumes order.dispatched
order-service marks DISPATCHED
```

Tasks:

- [ ] Implement `order.submitted` outbox publish.
- [ ] Implement inventory consumer for `order.submitted`.
- [ ] Implement inventory producer for `stock.reserved`.
- [ ] Implement order consumer for `stock.reserved`.
- [ ] Implement order producer for `order.accepted`.
- [ ] Implement dispatcher consumer for `order.accepted`.
- [ ] Implement dispatcher producer for `order.dispatched`.
- [ ] Implement order consumer for `order.dispatched`.
- [ ] Add end-to-end test or multi-service integration test if feasible.

### 4.9.4 Compensation Path

```text
POST /orders
inventory-service cannot reserve stock
inventory-service emits stock.rejected
order-service marks REJECTED
order-service emits order.rejected
```

Tasks:

- [ ] Inventory detects insufficient stock without partial reservation.
- [ ] Inventory emits `stock.rejected` with reason.
- [ ] Order consumes `stock.rejected` idempotently.
- [ ] Order marks state `REJECTED`.
- [ ] Add test: insufficient stock does not reduce available stock.
- [ ] Add test: duplicate `stock.rejected` does not change final state incorrectly.

### 4.9.5 Timeout Path

- [ ] Add configurable saga timeout, for example `5m`.
- [ ] Add scheduled checker for orders stuck in `SUBMITTED`.
- [ ] Mark stuck order `CANCELLED` or `REJECTED` with reason `SAGA_TIMEOUT`.
- [ ] Emit cancellation event if inventory needs release logic.
- [ ] Add metric `bookstore_saga_timeouts_total`.
- [ ] Add test with shortened timeout.

### 4.9.6 Observability For Saga

- [ ] Every event log includes `sagaId` or `orderId`.
- [ ] Metrics count each state transition.
- [ ] Trace context propagates through events where possible.
- [ ] Dashboard shows number of orders per state.

### Acceptance Criteria

- [ ] Happy path ends `DISPATCHED`.
- [ ] Insufficient stock path ends `REJECTED`.
- [ ] Timeout path ends deterministic terminal or recoverable state.
- [ ] Duplicate events are safe.
- [ ] Every state transition is logged and metered.

---

## 4.10 Final Hardening And Failure Tests

### 4.10.1 Failure Scenarios

- [ ] Kafka down during order creation.
- [ ] Kafka down during catalog book update.
- [ ] Inventory service down while orders are submitted.
- [ ] Search Elasticsearch down while book event consumed.
- [ ] Duplicate `order.submitted` event.
- [ ] Duplicate `stock.reserved` event.
- [ ] Catalog service slow/down behind edge route.
- [ ] Redis down during catalog cache lookup.

### 4.10.2 Expected Behavior Matrix

| Failure | Expected Behavior |
|---|---|
| Kafka down during order create | Order saved, outbox pending, no event loss |
| Kafka down during book update | Book saved, outbox pending, search catches up later |
| Duplicate order event | Inventory no-op after first processing |
| Elasticsearch down | Search consumer records failure/retry, API returns 503 for search |
| Catalog slow | Timeout/circuit breaker avoids hanging callers |
| Redis down | Catalog falls back to DB if configured, logs warning |

### 4.10.3 Documentation

- [ ] Add `docs/production-patterns.md` summarizing implemented patterns.
- [ ] Add `docs/event-contracts.md` for event payloads.
- [ ] Add `docs/runbooks/outbox.md` for pending/failed outbox events.
- [ ] Add `docs/runbooks/saga.md` for stuck orders.
- [ ] Add `docs/runbooks/observability.md` for dashboards and traces.

---

## Sprint Plan With Concrete Deliverables

## Sprint 1 — API Foundation

### Goal

Make API errors and validation production-ready for `catalog-service` and `order-service` first.

### Tasks

- [ ] `catalog-service`: add ProblemDetail handler.
- [ ] `catalog-service`: add validation improvements.
- [ ] `catalog-service`: add controller tests for error contract.
- [ ] `order-service`: add ProblemDetail handler.
- [ ] `order-service`: add validation improvements.
- [ ] `order-service`: add controller tests for error contract.
- [ ] Create error catalog document.

### Verify

```bash
cd catalog-service && ./gradlew spotlessApply spotlessCheck test
cd order-service && ./gradlew spotlessApply spotlessCheck test
```

### Done When

- [ ] Both services return consistent `ProblemDetail`.
- [ ] Invalid requests return field-level `400`.
- [ ] Domain conflicts return `409` or `422`.

## Sprint 2 — Finish API Foundation For Remaining Services

### Tasks

- [ ] `inventory-service`: add ProblemDetail handler.
- [ ] `inventory-service`: add validation improvements.
- [ ] `inventory-service`: add tests.
- [ ] `search-service`: add ProblemDetail handler.
- [ ] `search-service`: add query param validation.
- [ ] `search-service`: add tests.
- [ ] `edge-service`: ensure auth/security failures still return expected status and do not leak details.

### Verify

```bash
cd inventory-service && ./gradlew test
cd search-service && ./gradlew test
cd edge-service && ./gradlew test
```

## Sprint 3 — Logging And Observability

### Tasks

- [ ] Add JSON logging profile to all services.
- [ ] Add MDC/correlation for MVC services.
- [ ] Add Reactor context/MDC bridge for WebFlux services.
- [ ] Add Prometheus registry.
- [ ] Expose `/actuator/prometheus`.
- [ ] Add OpenTelemetry tracing config.
- [ ] Add local Prometheus + Grafana + Tempo/Jaeger.
- [ ] Create first dashboard.

### Verify

```bash
curl http://localhost:9001/actuator/prometheus
curl http://localhost:9002/actuator/prometheus
curl http://localhost:9004/actuator/prometheus
curl http://localhost:9005/actuator/prometheus
```

## Sprint 4 — Order Outbox ✅

> **Status: COMPLETE.** Outbox implemented with Debezium CDC. See `docs/tasks/saga-outbox-plan.md`.

### Tasks (done)

- [x] `order-service`: create outbox migration (`V6__create_outbox_event.sql`)
- [x] `order-service`: add outbox model/repository (`JooqOutboxRepository`, `OutboxRecord`)
- [x] `order-service`: update submit order transaction (save + outbox in same R2DBC tx)
- [x] `order-service`: Debezium connector publishes to Kafka (no poller needed)
- [x] `order-service`: add outbox trace_id (W3C traceparent)
- [x] `order-service`: add Kafka-down recovery test (`OutboxAppendIT`)

### Verify

```bash
cd order-service && ./gradlew spotlessApply spotlessCheck test
```

## Sprint 5 — Catalog Outbox And Search Idempotency ✅

> **Status: COMPLETE.** See `docs/tasks/saga-outbox-plan.md`.

### Tasks (done)

- [x] `catalog-service`: create outbox migration (`V6__create_outbox_event.sql`)
- [x] `catalog-service`: write book mutation events to outbox (`OutboxBookEventPublisher`)
- [x] `catalog-service`: Debezium connector publishes (no poller needed)
- [x] `search-service`: idempotent by ISBN (upsert/delete, no processed_event guard needed)
- [x] `search-service`: duplicate event tests (`KafkaBookEventPublisherTest`)

### Verify

```bash
cd catalog-service && ./gradlew spotlessApply spotlessCheck test
cd search-service && ./gradlew test
```

## Sprint 6 — Inventory Idempotency ✅

> **Status: COMPLETE.** See `docs/tasks/saga-outbox-plan.md` §2.7.

### Tasks (done)

- [x] `inventory-service`: idempotency via `reservationPort.findByOrderId` short-circuit + Redis claim
- [x] `inventory-service`: process event and stock reservation in one transaction
- [x] `inventory-service`: skip duplicates safely
- [x] `inventory-service`: duplicate/concurrency tests (`IdempotentConsumerIT`)
- [ ] `dispatcher-service`: add dispatch idempotency (follow-up)

### Verify

```bash
cd inventory-service && ./gradlew test
cd dispatcher-service && ./gradlew test
```

## Sprint 7 — Resilience And Cache

### Tasks

- [ ] `edge-service`: audit route circuit breaker/timeouts.
- [ ] `order-service`: add Resilience4j around safe dependencies.
- [ ] `search-service`: add timeout/circuit breaker around Elasticsearch.
- [ ] `catalog-service`: add Redis read-through cache for ISBN lookup.
- [ ] `catalog-service`: add cache eviction on mutations.
- [ ] Add metrics/tests for circuit breaker and cache.

### Verify

```bash
cd edge-service && ./gradlew test
cd order-service && ./gradlew spotlessApply spotlessCheck test
cd search-service && ./gradlew test
cd catalog-service && ./gradlew spotlessApply spotlessCheck test
```

## Sprint 8 — Saga Choreography ✅

> **Status: COMPLETE.** See `docs/tasks/saga-outbox-plan.md`. Stress test: 1000/1000 orders DISPATCHED.

### Tasks (done)

- [x] Define order state machine in domain
- [x] Define event contracts (order-created, inventory-events, order-accepted, order-dispatched)
- [x] Implement `order-created` -> inventory reservation
- [x] Implement `reserved` -> order accepted
- [x] Implement `rejected` -> order cancelled (compensation)
- [x] Implement `order-accepted` -> dispatcher
- [x] Implement `order-dispatched` -> order dispatched
- [ ] Add timeout handler (follow-up — see saga-outbox-plan.md §3.3 open follow-ups)
- [x] Add happy path and compensation tests

### Verify

```bash
cd order-service && ./gradlew spotlessApply spotlessCheck test
cd inventory-service && ./gradlew test
cd dispatcher-service && ./gradlew test
```

## Sprint 9 — Failure Rehearsal And Docs

### Tasks

- [ ] Run Kafka-down order test manually.
- [ ] Run Kafka-down catalog event test manually.
- [ ] Run Elasticsearch-down search test manually.
- [ ] Run duplicate event replay tests.
- [ ] Write runbooks for outbox, saga, observability.
- [ ] Update roadmap tracker after completion.

### Verify

```bash
make test
```

---

## Manual Verification Playbook

### Kafka Down During Order Submit

```bash
docker stop kafka
# submit order
# query order DB: order exists
# query outbox_events: status = PENDING
docker start kafka
# wait poll interval
# query outbox_events: status = SENT
```

### Duplicate Inventory Event

```bash
# publish same order.submitted event twice
# query inventory reservation table
# expected: one reservation for orderId
# query processed_events
# expected: one processed event record or duplicate safely skipped
```

### Search Index Recovery

```bash
docker stop elasticsearch
# update a book in catalog
# expected: catalog outbox pending or search processing failure recorded
docker start elasticsearch
# expected: search index catches up
```

### Circuit Breaker

```bash
# stop downstream dependency
# send repeated requests
curl http://localhost:9002/actuator/metrics/resilience4j.circuitbreaker.calls
# expected: failures recorded and circuit opens
```

### Observability

```bash
# place an order through edge-service
# open Grafana/Tempo or Jaeger
# search by traceId/orderId
# expected: one trace spans HTTP + Kafka consumer work
```

---

## Risk Register

| Risk | Mitigation |
|---|---|
| Retrying writes creates duplicate orders | Do not retry non-idempotent writes until idempotency key exists |
| Outbox poller double-publishes in multi-instance deployment | N/A — Debezium uses replication slot, single active instance |
| Reactive MDC loses trace fields | Use Reactor context propagation, verify with logs inside reactive chains |
| Cache returns stale book after update | Evict cache on mutation and use TTL |
| Saga gets stuck in `SUBMITTED` | Add timeout scanner and dashboard metric |
| Error contract differs per service | Use same error catalog and tests asserting shape |
| Observability stack too heavy locally | Start with Prometheus + one tracing backend, add dashboards incrementally |

## Backlog After Phase 4

- [x] Replace polling outbox with Debezium CDC if needed. — **Done: Debezium CDC is the implementation, not polling.**
- [ ] Add Pact/contract tests for events and HTTP APIs.
- [ ] Add Alertmanager rules for failed outbox and saga timeout.
- [ ] Add load tests for cache and circuit breaker behavior.
- [ ] Add OpenAPI docs with ProblemDetail schemas.
- [ ] Add SLOs: availability, latency, error rate, event processing delay.
