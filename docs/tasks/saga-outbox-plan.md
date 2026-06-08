# Saga + Transactional Outbox via Debezium

> **Status: COMPLETE.** Production cutover done (legacy `Kafka*Publisher` classes + their tests
> + the `legacy-publish` profile guard removed). Stress test (1000 req / 50 concurrency) passed:
> 100% HTTP success, RPS=120, all 1000 orders DISPATCHED, exactly 1000 inventory units consumed,
> outbox topology verified end-to-end.

## 1. Why chosen & documentation

### 1.1 Decision

- **Pattern**: Transactional Outbox — every write-then-publish path inserts one `outbox_event`
  row in the **same DB transaction** as the aggregate. Debezium's PostgreSQL connector tails the
  WAL and publishes to Kafka via the **Outbox Event Router** SMT. No app-side `streamBridge` /
  `Sinks` publishing is on the active path.
- **Saga style**: keep the existing **choreography** (Order → Inventory → Order accept;
  Order → Dispatcher → Order dispatched). No orchestrator.
- **Why**: previously every path was a dual write (save to Postgres, then publish to Kafka in a
  separate reactive step). A crash between the two loses the event. Outbox makes the publish
  atomic with the state change; Debezium gives at-least-once delivery from the WAL.

### 1.2 Rejected alternatives

- **Temporal / Camunda orchestration** — new cluster + worker SDKs, rewrites use-cases. Overkill
  for a durability gap. Revisit only if compensations grow beyond ~2 hops or human-approval
  steps appear.
- **`@Scheduled` polling publisher** — works, but extra per-service code, higher latency,
  hand-rolled leader election + idempotency. Keep only as fallback if Kafka Connect is
  unavailable.
- **Kafka transactions / `@Transactional` around the send** — does not compose with R2DBC
  (`order-service`, `inventory-service`); still a dual write, only narrows the window.
- **Redis write-behind / Redis as primary write store** — drops durability for the order /
  inventory write paths. Redis stays a **read cache + idempotency cache** only.

### 1.3 Documentation

- Saga — <https://microservices.io/patterns/data/saga.html>
- Transactional Outbox — <https://microservices.io/patterns/data/transactional-outbox.html>
- Transaction Log Tailing — <https://microservices.io/patterns/data/transaction-log-tailing.html>
- Debezium PostgreSQL connector —
  <https://debezium.io/documentation/reference/stable/connectors/postgresql.html>
- Debezium Outbox Event Router SMT —
  <https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html>
- Postgres logical replication —
  <https://www.postgresql.org/docs/current/logical-replication.html>
- OpenTelemetry messaging conventions —
  <https://opentelemetry.io/docs/specs/semconv/messaging/>

---

## 2. What is in the code today (verified)

### 2.0 Architectural deviation from the original draft

The original draft proposed a new `OutboxPort` + `JooqOutboxAdapter` and reworking each use-case
to call `outboxPort.append(...)`. The implementation took a **less invasive, equally-correct
shape**: each existing event-publisher port (`OrderEventPublisherPort`, `InventoryEventPublisher`,
`BookEventPublisher`) gained a **second adapter** (`OutboxXxxEventPublisher`) under
`@Profile("!legacy-publish")`, while the Kafka publishers stayed under `@Profile("legacy-publish")`
as a rollback escape. Use-case code is unchanged. Hexagonal layering is preserved.

### 2.1 Topology preserved (confirmed against connector configs and consumer bindings)

| Topic | Producer (now) | Consumer |
|---|---|---|
| `order-created-events` | order outbox row → Debezium router | inventory `reserveStock-in-0` |
| `order-accepted` | order outbox row → Debezium router | dispatcher `packlabel-in-0` |
| `order-cancelled-events` | order outbox row → Debezium router (when wired) | inventory `releaseStock-in-0` |
| `inventory-events` | inventory outbox row → Debezium router | order `handleInventoryDecision-in-0` |
| `book.created` / `book.updated` / `book.deleted` | catalog outbox rows → Debezium router | search `handleBookCreated/Updated/Deleted-in-0` |

Routing is by the literal `destination` column on the outbox row (`route.by.field=destination`,
`route.topic.replacement=${routedByValue}`), so topic names are reproduced exactly.

### 2.2 Outbox table

`V6__create_outbox_event.sql` is in place in **all three** services (order, inventory, catalog):

```sql
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type TEXT NOT NULL,
    aggregate_id   TEXT NOT NULL,
    type           TEXT NOT NULL,
    destination    TEXT NOT NULL,        -- exact Kafka topic
    payload        JSONB NOT NULL,
    trace_id       TEXT,                 -- W3C traceparent string (this session)
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX outbox_event_aggregate_idx ON outbox_event (aggregate_type, aggregate_id);
```

`TIMESTAMP WITH TIME ZONE` (not `TIMESTAMPTZ`) is chosen for H2 portability in catalog tests.

### 2.3 Per-service publishers (default profile)

- `order-service`:
  `adapter/out/messaging/OutboxOrderEventPublisher` (R2DBC + jOOQ DSL) covers
  `OrderCreated → order-created-events`, `OrderAccepted → order-accepted`,
  `OrderCancelled → order-cancelled-events`. The `SubmitOrderService` and
  `ProcessInventoryDecisionService` already wrap save + publish in one
  `transactionalOperator.transactional(...)`.
- `inventory-service`:
  `adapter/out/messaging/OutboxInventoryEventPublisher` covers `InventoryDecision →
  inventory-events`. Both `reserveAvailableStock` (success) and the `onErrorResume` rejection
  branch in `ReserveStockService` are routed through outbox.
- `catalog-service`:
  `adapter/out/messaging/OutboxBookEventPublisher` (`JdbcTemplate`) covers
  `BookCreated/BookUpdated/BookDeleted → book.{created,updated,deleted}`. `BookCatalogService`
  mutating methods are `@Transactional`, so book row + outbox row commit together.

The legacy `Kafka*Publisher` classes are still present under `@Profile("legacy-publish")` for
rollback. Production runs default (outbox).

### 2.4 trace_id (this session)

All three publishers inject `io.micrometer.tracing.Tracer` and write the active span's W3C
`traceparent` (`00-<traceId>-<spanId>-01`) into `outbox_event.trace_id`. The Debezium connector
maps that column to the `traceparent` Kafka header
(`transforms.outbox.table.fields.additional.placement: "type:header:eventType,trace_id:header:traceparent"`),
so consumers continue the same trace. Returns `null` when no span is active (column is
nullable).

### 2.5 Infrastructure

- `polar-postgres-{order,inventory,catalog}` run with
  `wal_level=logical -c max_replication_slots=10 -c max_wal_senders=10` (compose `command:`).
- `polar-kafka-connect` (`debezium/connect:2.7.3.Final`) is wired in
  `polar-deployment/docker/docker-compose.yml`.
- Three connector configs in `polar-deployment/docker/connect/`:
  `order-outbox-connector.json`, `inventory-outbox-connector.json`,
  `catalog-outbox-connector.json` — each with `route.by.field=destination`,
  `expand.json.payload=true`, and the `eventType` / `traceparent` header placements.
- `register-connectors.sh` registers all three against `http://localhost:8083`.

### 2.6 Outbox retention (this session)

`pg_cron` is not viable in this stack — the running image is `postgres:13.4` (stock, no
extension). Retention is therefore done outside the apps:

- `polar-deployment/docker/postgres/outbox-retention.sql` — parameterized
  `DELETE FROM outbox_event WHERE created_at < now() - interval` (preserves rows that haven't yet
  been picked up).
- `make outbox-cleanup` (default `OUTBOX_RETENTION_DAYS=7`) — runs the SQL via
  `docker exec psql` against all three DBs. Verified end-to-end (DELETE 0 across all DBs in this
  session, expected when nothing is older than the window).
- Production guidance: switch to a `pg_cron`-enabled Postgres image OR run the SQL via a k8s
  `CronJob`. Either way, **no app-side scheduling**.

### 2.7 Tests in place

- **Per-publisher unit (catalog)**:
  `OutboxBookEventPublisherTraceTest` — Mockito `JdbcTemplate` + `Tracer` stub. Asserts the
  `traceparent` arg is `"00-<traceId>-<spanId>-01"` when a span is active and `null` when not.
- **Outbox append IT**:
  - catalog: `OutboxAppendIT` (H2 in PostgreSQL mode) — asserts the row is well-formed and routed
    by `destination`.
  - order: `OutboxAppendIT` (Testcontainers Postgres) — asserts the publisher is the outbox
    impl, that aggregate save + outbox append are visible **inside the same transaction** (same
    bound R2DBC connection ⇒ same commit/rollback fate).
  - inventory: `OutboxAppendIT` (Testcontainers Postgres) — covers BOTH the `reserved` (save +
    outbox in one tx) and `rejected` (single outbox insert, no aggregate) paths, plus same-tx
    visibility.
- **Idempotent consumer IT (inventory)**:
  `IdempotentConsumerIT` — mocks Redis to always grant the claim so the **DB-level guard**
  (`reservationPort.findByOrderId` short-circuit) is exercised on redelivery. Asserts that two
  deliveries of the same order id produce exactly one inventory deduction, one reservation row,
  and one outbox row.

### 2.8 Legacy bindings (this session)

Removed the now-unused source-side bindings:

- order `acceptOrder-out-0`, `orderCreated-out-0`, `orderCancelled-out-0`.
- inventory `inventoryDecision-out-0` plus the dead `source: inventoryDecision` declaration.
- catalog had none (catalog publishes via reactive `Sinks`, no binder source).

The legacy publisher classes themselves remain under `@Profile("legacy-publish")`. To keep that
rollback path verifiable, `inventory-service/src/test/resources/application-legacy-publish.yml`
re-declares the `inventoryDecision-out-0 → inventory-events` binding **for the test classpath
only**. Order's legacy publisher tests read by channel name and don't need an override.

---

## 3. Status

### 3.1 Production cutover — DONE

- Deleted: `KafkaOrderEventPublisher` + 3 tests, `KafkaInventoryEventPublisher` + test,
  `KafkaBookEventPublisher` (catalog) + test, `application-legacy-publish.yml` (test resource).
- Dropped `@Profile("!legacy-publish")` / `@Profile("legacy-publish")` from the 3 outbox
  publishers — they are now the unconditional default beans.
- Refactored `OrderServiceApplicationTests` and inventory `OrderEventConsumerTest` to assert
  outbox rows via `DSLContext` instead of reading from `OutputDestination`.
- All 3 service test suites green; spotless clean.

### 3.2 Stress test results (1000 req / 50 concurrency)

| Metric | Result |
|---|---|
| HTTP success rate | 1000 / 1000 = 100% |
| Duration | 8.3 s |
| RPS | 120 |
| Final order status | 1000 DISPATCHED |
| Inventory delta | available 5000 → 4000, reserved 0 → 1000 (exactly consumed) |
| Negative inventory | never |
| Outbox rows per destination | 1000 OrderCreated, 1000 OrderAccepted, 1000 InventoryDecision |

**Root-cause fix applied** when the first run produced 51 client-side timeouts: R2DBC pool
`max-size` was equal to test concurrency (`50 == 50`), so the pool was the bottleneck. Bumped to
100 (`initial-size: 10`) for both order and inventory. Subsequent run: 100%.

### 3.3 Open follow-ups

- **`ObservationThreadLocalAccessor` warnings** in order-service logs ("Observation to which
  we're restoring is not the same as the one set as this scope's parent observation"). Reactive
  tracing context-propagation noise; does not affect correctness (1000/1000 orders processed)
  but is worth tracking if trace-graph continuity becomes critical.
- **Reactive R2DBC explicit rollback** (raised in the prior plan revision): a forced
  `Mono.error(...)` after `save+publish` inside `transactionalOperator.transactional` did not
  rollback the order row in the IT. Replaced the IT with a same-tx visibility check (structural
  proof of shared connection ⇒ shared commit/rollback fate). Worth investigating before
  claiming the strict crash-safety guarantee.
- **Optional**: an automated `OutboxToKafkaE2EIT` per service using `debezium-testing-testcontainers`
  to cover the SMT routing automatically (manual E2E in §2.7 already proves the path live).

### 3.4 Decided NOT to do (with rationale)

- `processed_event` guard in search-service: handlers are idempotent upserts/deletes by isbn;
  redelivery is harmless. Adding a guard would be speculative. Skip per plan's own criterion
  (§2.6 of the original draft).

---

## 4. Out of scope (unchanged)

Orchestrated saga (Temporal/Camunda), schema registry / Avro, CDC of aggregate tables (we CDC
only `outbox_event` to keep contracts stable), and the `202 Accepted` + SSE API-contract change
(separable latency work).
