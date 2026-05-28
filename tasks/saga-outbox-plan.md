# Saga + Transactional Outbox via Debezium — Implementation Plan

## 0. Decision (TL;DR)

- **Pattern**: Transactional Outbox using Debezium PostgreSQL connector + Kafka Connect, with Debezium **Outbox Event Router** SMT.
- **Saga style**: keep current **choreography** (Order → Inventory → Order accept; Order → Dispatcher → Order dispatched). No orchestrator (no Temporal) for now.
- **API contract**: **Command Acceptance pattern** — `POST /orders` returns `202 { orderId, status: PENDING }` immediately (8–15 ms), client polls `GET /orders/{id}` or subscribes via SSE for final status. Synchronous response (`200 { order }`) is deprecated.
- **Rejected alternatives**:
  - **Temporal** — orchestration engine, requires new cluster + worker SDKs, rewrites use-cases. Overkill for the durability gap we actually have.
  - **`@Scheduled` polling publisher** — works but extra app code per service, higher latency, DB load, and we must hand-roll leader election + idempotency. Keep as fallback only if Kafka Connect is unavailable.
  - **Kafka transactions / `@Transactional` around `streamBridge.send`** — does not compose cleanly with R2DBC (`order-service`, `inventory-service`); still a dual-write, only narrows the window.
  - **Synchronous response with outbox** — still waits for inventory reserve, latency 25–60 ms. Not acceptable for target SLO.

## 1. Scope of bug fixed

Today every write-then-publish path is a dual write:

- `order-service` `SubmitOrderService` → save + `orderCreated-out-0`
- `order-service` `ProcessInventoryDecisionService` → save accept + `acceptOrder-out-0`
- `order-service` `CancelOrderService` (if/when used) → save + `orderCancelled-out-0`
- `inventory-service` `ReserveStockService` → save reservation + `inventoryDecision-out-0`
- `catalog-service` `KafkaBookEventPublisher` → book CRUD + `book-events`

After this plan, **none** of these will publish to Kafka from the app. Each writes one row into its local `outbox_event` table inside the same DB transaction; Debezium tails the WAL and publishes to Kafka.

## 2. Architecture

```
+------------------+        DB tx         +------------------+
|  use-case        |--------------------->|  aggregate table |
|  service (R2DBC/ |                      +------------------+
|  JDBC)           |--------------------->|  outbox_event    |
+------------------+                      +------------------+
                                                  |
                                            (Postgres WAL)
                                                  |
                                       +------------------------+
                                       | Debezium PG connector  |
                                       | + Outbox Router SMT    |
                                       +------------------------+
                                                  |
                                                  v
                                       Kafka topic per aggregate_type
                                       key = aggregate_id, value = payload
```

- Existing **consumers** (`reserveStock-in-0`, `releaseStock-in-0`, `handleInventoryDecision-in-0`, `dispatchOrder-in-0`, search indexer) keep their bindings — destination topic names stay identical, only the **producer** changes.
- Idempotency on the consumer side is enforced by an `processed_event` table keyed by `event_id` (already mostly present in `inventory-service` via `reservationPort.findByOrderId` — make it explicit and uniform).

## 3. Per-service deliverables

### 3.1 `order-service` (R2DBC + Flyway + jOOQ)

1. Flyway `V<next>__create_outbox_event.sql`:
   ```sql
   CREATE TABLE outbox_event (
     id            UUID PRIMARY KEY,
     aggregate_type TEXT NOT NULL,    -- "order"
     aggregate_id  TEXT NOT NULL,     -- order id as string
     type          TEXT NOT NULL,     -- "OrderCreated" | "OrderAccepted" | "OrderCancelled"
     payload       JSONB NOT NULL,
     trace_id      TEXT,
     created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   CREATE INDEX outbox_event_aggregate_idx ON outbox_event(aggregate_type, aggregate_id);
   ```
2. Run `./gradlew generateJooq` (depends on `flywayMigrate`). Commit `src/main/generated-jooq/`.
3. New port `application/port/out/OutboxPort` with `Mono<Void> append(OutboxEvent e)`.
4. New adapter `adapter/out/persistence/JooqOutboxAdapter` writing the row using the **same R2DBC connection** as the aggregate save (use `TransactionalOperator`).
5. **Command Acceptance API change**:
   - Modify `SubmitOrderService` to return `202 Accepted` with `{ orderId, status: "PENDING" }` immediately after persisting the order + outbox row. Do **not** wait for inventory decision.
   - Change response DTO from full `Order` to `OrderAcceptedResponse { orderId, status, createdAt }`.
   - Remove the existing synchronous wait logic (if any) that waited for inventory before responding.
6. Replace `KafkaOrderEventPublisher` calls in:
   - `SubmitOrderService.publishOrderCreatedIfPending` → `outboxPort.append(...)` inside `TransactionalOperator.execute(...)` that also wraps the aggregate save.
   - `ProcessInventoryDecisionService` accept branch → same.
   - cancel path → same.
7. Add **SSE endpoint** for order status streaming:
   - New inbound adapter `adapter/in/web/OrderStatusSseController` with `GET /orders/{id}/stream` returning `Flux<ServerSentEvent<OrderStatus>>`.
   - Subscribe to `inventory-events` (already consumed) and emit status updates (`PENDING` → `ACCEPTED`/`REJECTED` → `DISPATCHED`).
   - Client can poll `GET /orders/{id}` as fallback.
8. Delete `KafkaOrderEventPublisher` (or keep behind a profile `legacy-publish` for rollback during cutover).
9. Remove `acceptOrder-out-0`, `orderCreated-out-0`, `orderCancelled-out-0` bindings from `application.yml` once Debezium is live.
10. Add `OutboxAppendIT` (Testcontainers Postgres) asserting that a failed Kafka isn't in the picture and a single tx writes both rows atomically.
11. Add `CommandAcceptanceE2EIT` (Testcontainers: Postgres + Kafka + Debezium) asserting:
    - `POST /orders` returns 202 in <15 ms.
    - Order status transitions from PENDING → ACCEPTED/REJECTED within 1 s.
    - SSE stream emits status updates correctly.

### 3.2 `inventory-service` (R2DBC + Flyway + jOOQ)

1. Same Flyway migration shape (`outbox_event`).
2. `generateJooq`, commit generated sources.
3. `OutboxPort` + `JooqOutboxAdapter` mirroring 3.1.
4. `ReserveStockService`: replace `eventPublisher.publishInventoryDecision(...)` with `outboxPort.append(...)` *inside the same R2DBC transaction* that persists `Reservation` + inventory updates. Today these are two separate reactive flatMaps — wrap them in `TransactionalOperator`.
5. Delete `KafkaInventoryEventPublisher` (or guard behind `legacy-publish` profile).
6. Remove `inventoryDecision-out-0` binding once Debezium is live.

### 3.3 `catalog-service` (JDBC + Flyway)

1. Flyway migration for `outbox_event` (same shape).
2. `OutboxPort` + `JdbcTemplate`/Spring Data JDBC adapter; uses Spring `@Transactional` since this service is JDBC.
3. Wrap `BookService` mutating methods so the `book` row write and `outbox_event` insert share one tx; replace `KafkaBookEventPublisher` calls.
4. Remove the corresponding `*-out-0` bindings.

### 3.4 `search-service`

- **No producer changes.** It is a consumer of `book-events` (and order topics if applicable). Verify idempotency: maintain a `processed_event_id` set keyed off the event UUID we now stamp at the producer (`outbox_event.id`). Add a small `processed_event` Elasticsearch index or in-memory cache if duplicates would corrupt projections.

### 3.5 `dispatcher-service`

- Stateless functional consumer/producer. It does not write to a DB, so dual-write does not apply. **No changes** unless we want at-least-once semantics for `OrderDispatchedMessage`; if so, add a tiny Postgres + outbox just for dispatched events (defer; not part of MVP).

### 3.6 `edge-service`, `config-service`

- Out of scope. No DB writes that need to publish events.

## 4. Infra changes (`polar-deployment`)

1. **Postgres**: enable logical replication on all three DBs.
   - In `docker-compose.yml` add `command: ["postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=10", "-c", "max_wal_senders=10"]` to each polardb container.
   - K8s: add the same args to the Postgres StatefulSets in `polar-deployment/kubernetes/local/`.
2. **Kafka Connect** with Debezium image:
   - Compose service `kafka-connect` using `debezium/connect:2.7` (or latest matching Kafka version), depends on Kafka.
   - Mount/POST connector configs:
     - `order-outbox-connector.json`
     - `inventory-outbox-connector.json`
     - `catalog-outbox-connector.json`
   - Each connector:
     ```json
     {
       "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
       "database.hostname": "polar-postgres-order",
       "database.port": "5432",
       "database.user": "user", "database.password": "password",
       "database.dbname": "polardb_order",
       "topic.prefix": "order",
       "plugin.name": "pgoutput",
       "slot.name": "order_outbox_slot",
       "publication.autocreate.mode": "filtered",
       "table.include.list": "public.outbox_event",
       "tombstones.on.delete": "false",
       "transforms": "outbox",
       "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
       "transforms.outbox.route.by.field": "aggregate_type",
       "transforms.outbox.route.topic.replacement": "${routedByValue}-events",
       "transforms.outbox.table.field.event.id": "id",
       "transforms.outbox.table.field.event.key": "aggregate_id",
       "transforms.outbox.table.field.event.type": "type",
       "transforms.outbox.table.field.event.payload": "payload",
       "transforms.outbox.table.fields.additional.placement": "type:header:eventType,trace_id:header:traceparent"
     }
     ```
   - Topic mapping after SMT (verify equals current names; rename `aggregate_type` values to match):
     - `order-created-events`, `order-accepted` (or `order-accepted-events`), `order-cancelled-events`
     - `inventory-events`
     - `book-events`
3. **Topic creation**: Kafka auto-create stays on for dev; for prod add explicit topic provisioning step.
4. **Outbox cleanup job**: Debezium does not delete rows. Add either:
   - `pg_cron` daily `DELETE FROM outbox_event WHERE created_at < now() - interval '7 days'`, OR
   - service-side `@Scheduled` (catalog-service) / reactive scheduler running same cleanup.
   Pick **pg_cron** to keep it out of the apps.

## 5. Cross-cutting concerns

- **Event schema**: introduce a `bookstore-events` shared module *(optional)* OR keep per-service DTOs and rely on JSON. Recommend per-service for now to avoid a monorepo dependency you don't have.
- **Tracing**: stamp `traceparent` into `outbox_event.trace_id` at write time (read from `Span.current()`); the SMT places it as Kafka header so consumers continue the trace.
- **Idempotency**: every consumer must dedupe by `outbox_event.id` (kafka header `id` from Debezium SMT). Add a `processed_event(event_id PK, processed_at)` table per consuming write-service.
- **Ordering**: Kafka partitions by `aggregate_id`. Acceptable for current saga (orderId-keyed).

## 6. Rollout sequence

1. **Phase 1 — Infra prep**
   - Patch Postgres compose/k8s for logical wal.
   - Add Kafka Connect with no connectors yet, verify `/connectors` endpoint.
2. **Phase 2 — order-service outbox + Command Acceptance**
   - Add migration, port, adapter, switch `SubmitOrderService` to outbox.
   - **API contract change**: modify `POST /orders` to return `202 { orderId, status: PENDING }`. Add SSE endpoint `GET /orders/{id}/stream`.
   - Deploy connector for `polardb_order` with `table.include.list=public.outbox_event`.
   - Run E2E: submit order → assert 202 response in <15 ms → assert event lands on `order-created-events` → inventory consumer reacts → SSE stream emits status updates.
   - Keep `KafkaOrderEventPublisher` behind `legacy-publish` profile, **off**.
   - **Client migration**: update any direct clients (frontend, tests) to use new API contract. Document deprecation of synchronous response.
3. **Phase 3 — order-service remaining events** (`accept`, `cancel`).
4. **Phase 4 — inventory-service**.
5. **Phase 5 — catalog-service**.
6. **Phase 6 — cleanup**: delete legacy publishers + `*-out-0` bindings; remove `legacy-publish` profile; remove synchronous response fallback code.
7. **Phase 7 — search-service consumer dedupe** + chaos test (kill Kafka mid-tx, kill app between commit and Debezium pickup, kill Connect — all events must eventually arrive exactly-effectively-once).

## 7. Tests to add (non-negotiable)

- `OutboxAppendIT` per service (Testcontainers Postgres): one tx writes both rows; rollback ⇒ neither row.
- `OutboxToKafkaE2EIT` (Testcontainers: Postgres + Kafka + Debezium Connect image): assert event arrives on the expected topic with expected key/headers.
- `IdempotentConsumerIT` for inventory + search: redelivering the same `event_id` is a no-op.
- Saga happy path + reject path integration test in `order-service` against Testcontainers Kafka, with the publisher path going through outbox + an embedded Debezium-equivalent (or a manual poller in tests since spinning Connect in tests is heavy — acceptable shortcut for unit-level coverage; the full E2E is the dedicated `OutboxToKafkaE2EIT`).

## 8. Out of scope (explicit)

- Switching to **orchestrated** saga (Temporal / Camunda). Re-evaluate only if compensations grow beyond 2 hops or business adds human-approval steps.
- Schema registry / Avro. Stay JSON; revisit if event count grows.
- CDC of aggregate tables themselves (we deliberately CDC only `outbox_event` to keep contracts stable).

## 9. Risk register

| Risk | Mitigation |
|------|------------|
| Replication slot leak if Connect is down | Monitor `pg_replication_slots`; alert on `active=false` for >5 min |
| Outbox table growth | pg_cron cleanup (§4) + alert on row count |
| Topic name drift from current bindings | Phase 2 explicitly verifies before deleting legacy publishers |
| Debezium upgrade pinning | Pin image tag, bump in a dedicated PR |
| Native image impact | None — outbox is plain JDBC/R2DBC; Debezium runs out-of-process |

## 10. Latency target — 15–30 ms with Redis (honest analysis)

> You asked: *"use Redis to write-ahead then sync after, to make the request 15–30 ms."*
> Short answer: **don't write-behind a cache for saga events**. There is a safe way to hit the target. Read this section before designing anything.

### 10.1 Where the time actually goes today

Measure first. A typical `POST /orders` request in this stack:

| Step | Typical cost |
|------|--------------|
| TLS + auth (Keycloak JWT verify, cached JWK) | 1–3 ms |
| Edge gateway routing | 1–2 ms |
| `catalogBookPort.loadBook` HTTP to catalog | **5–25 ms** ← biggest variable |
| R2DBC insert order | 2–6 ms |
| `streamBridge.send` (today) | 1–4 ms |
| Serialization + response | 1–2 ms |

Going to outbox **does not slow you down** — it removes the Kafka send and replaces it with one extra `INSERT outbox_event` in the same tx (sub-ms). Net change ≈ 0. The real wins come from cutting the catalog round-trip and the synchronous publish.

### 10.2 Four options to hit 15–30 ms

#### Option A — Command Acceptance pattern (RECOMMENDED)

Respond `202 Accepted` with an `orderId` *as soon as* the command is durably accepted, do the heavy work async.

- Client `POST /orders` →
  1. Validate JWT (cached) — 1 ms
  2. Lookup book in **Redis cache** (warmed from `book-events`) — <1 ms; fallback to catalog HTTP only on miss
  3. Single Postgres tx: `INSERT order` (status=`ACCEPTED_PENDING`) + `INSERT outbox_event` — 3–6 ms
  4. Return `202 { orderId, status: "PENDING" }` — total **8–15 ms**
- Debezium pushes `OrderCreated` to Kafka. Inventory reserves async. Client polls `GET /orders/{id}` or subscribes via SSE/WebSocket for `ACCEPTED`/`REJECTED`.

**This keeps full durability** (Postgres is still the source of truth), uses Redis only as a **read** cache (safe), and the API contract becomes async — which is the *right* shape for a saga anyway.

#### Option B — Redis as the primary write store (DANGEROUS unless you know the trade-off)

Use Redis Streams (`XADD`) as the outbox. A worker drains the stream → Postgres + Kafka.

- Pros: write path is ~0.3–1 ms. Easy 15 ms p99.
- Cons:
  - Default AOF `everysec` ⇒ up to 1 s of orders **lost** on a node crash.
  - With `appendfsync=always` you're back to ~1–3 ms per write *and* still less durable than Postgres replication.
  - Recovery complexity: replay stream into Postgres on restart, dedupe, handle partial sync.
  - You lose Debezium's exactly-once-from-WAL guarantee.
- Verdict: **only acceptable for non-financial, replayable events** (e.g. analytics, search index updates). **Not for orders or inventory.**

#### Option C — Write-behind cache (NOT RECOMMENDED for this domain)

`POST /orders` writes only to Redis, returns 200, async worker persists to Postgres.

- Same downsides as B *plus* you've now lied to the client about success before it was durably stored. A node crash = silently dropped paid orders. Don't.

#### Option D — Pure Postgres optimization (do this regardless)

Even with Option A, tune the DB path so it's never the bottleneck:

- R2DBC pool: keep `initial-size=2 max-size=10` for dev; in prod size to `cores × 2`.
- `synchronous_commit = local` on Postgres for the order DB — saves 1–3 ms per tx, still durable on the primary.
- Prepared statement cache enabled (R2DBC default OK; verify).
- Avoid N+1 — `SubmitOrderService` already does one save; keep it that way.
- Use `RETURNING id` so you don't do a follow-up SELECT (R2DBC + jOOQ does this).
- Co-locate Postgres + service in the same AZ; <1 ms RTT.

### 10.3 Recommendation

**Option A + Option D is the default.** Keep the outbox plan in §1–§9 unchanged. Add Redis purely as:

1. **Read cache** for `book` lookups in `order-service` (TTL 5 min, invalidated by `book-events` consumer). Cuts the catalog round-trip from 5–25 ms to <1 ms. This alone gets you to ~15 ms.
2. **Idempotency cache** for `processed_event` (key = `evt:{event_id}`, TTL 24 h) on consumer side. Avoids a Postgres roundtrip per consumed event.
3. **Optional Redis Streams** for `dispatcher-service` only (it's already non-durable, stateless) — fine because dispatched events are replayable.

What Redis is **not** doing: accepting writes that haven't yet hit Postgres for the order/inventory/catalog write paths.

**API contract change is mandatory**: `POST /orders` returns `202 Accepted` with `{ orderId, status: PENDING }`. Client polls `GET /orders/{id}` or subscribes SSE. Synchronous response is deprecated.

### 10.4 Concrete additions (delta on top of §3)

- `order-service`:
  - Add `RedisBookCacheAdapter implements CatalogBookPort` decorating `HttpCatalogBookPort`. Use Lettuce reactive (`spring-boot-starter-data-redis-reactive`).
  - Subscribe to `book-events` (already published once §3.3 lands) and evict/update cache.
  - Add `Idempotency-Key` header support: store `idem:{key} -> orderId` in Redis 24 h, return same `orderId` on retry.
- `inventory-service`:
  - Add Redis-backed `processed_event` dedupe in front of the existing Postgres reservation lookup.
- `search-service`:
  - Same Redis dedupe for consumer.
- Compose: Redis already runs at `:6379` (used by edge-service rate limiting). Add a separate logical DB index per service or a key prefix.
- SLOs to add to observability dashboards: `http_server_requests_seconds{uri="/orders",quantile="0.99"}` target 30 ms, alert at 50 ms.

### 10.5 Anti-goals

- Do **not** put unconfirmed orders into Redis and ack the client.
- Do **not** use `KEYS *` or unbounded `SCAN` in hot path.
- Do **not** rely on Redis for anything that must survive a region-wide outage; treat it as ephemeral.

## 11. Learning resources

Curated, in suggested reading order. Stuff actually relevant to this codebase, not generic blog spam.

### 11.1 Patterns — read these first

- **microservices.io – Saga**: <https://microservices.io/patterns/data/saga.html>
- **microservices.io – Transactional Outbox**: <https://microservices.io/patterns/data/transactional-outbox.html>
- **microservices.io – Transaction Log Tailing** (= what Debezium does): <https://microservices.io/patterns/data/transaction-log-tailing.html>
- **microservices.io – CQRS** (background, since Option A pushes you toward async reads): <https://microservices.io/patterns/data/cqrs.html>
- Chris Richardson, *Microservices Patterns* (Manning), Ch. 3 (interprocess comm), Ch. 4 (sagas), Ch. 6 (event sourcing & outbox). The single best book for this design.

### 11.2 Debezium / CDC

- **Debezium docs – PostgreSQL connector**: <https://debezium.io/documentation/reference/stable/connectors/postgresql.html>
- **Debezium docs – Outbox Event Router SMT** (we use this): <https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html>
- Debezium blog: *Reliable Microservices Data Exchange With the Outbox Pattern* (Gunnar Morling): <https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/>
- *Distributed transactions: don't, just don't* — Pat Helland, classic, explains why we use outbox at all: <https://www.cidrdb.org/cidr2007/papers/cidr07p15.pdf>

### 11.3 Choreography vs Orchestration (why we picked choreography here)

- Yves Reynhout, *Saga choreography vs orchestration*: <https://blog.bernd-ruecker.com/saga-how-to-implement-complex-business-transactions-without-two-phase-commit-e00aa41a1b1b>
- Bernd Rücker (Camunda), *3 common pitfalls in microservice integration* (good critique of pure choreography — read once you understand the basics so you know when to switch): <https://blog.bernd-ruecker.com/3-common-pitfalls-in-microservice-integration-and-how-to-avoid-them-3f27a442cd07>
- **Temporal docs – When to use Temporal**: <https://docs.temporal.io/temporal> — read so you know when to revisit the orchestration option.

### 11.4 Spring Cloud Stream + Kafka (current binder)

- **Spring Cloud Stream reference**: <https://docs.spring.io/spring-cloud-stream/reference/>
- **Spring Kafka reference** (transactional producer, consumer rebalancing): <https://docs.spring.io/spring-kafka/reference/>
- *Confluent – Idempotent producers and exactly-once semantics*: <https://docs.confluent.io/kafka/design/delivery-semantics.html>

### 11.5 R2DBC + Postgres tuning (relevant to Option D)

- **R2DBC Pool** docs: <https://github.com/r2dbc/r2dbc-pool>
- **Postgres `synchronous_commit`** docs: <https://www.postgresql.org/docs/current/wal-async-commit.html>
- **Postgres logical replication** (what Debezium needs): <https://www.postgresql.org/docs/current/logical-replication.html>

### 11.6 Redis used correctly

- **Redis – Persistence**: <https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/> — read this so you understand why §10.2 Option B is risky.
- **Redis Streams intro**: <https://redis.io/docs/latest/develop/data-types/streams/>
- **Spring Data Redis Reactive**: <https://docs.spring.io/spring-data/redis/reference/redis/reactive.html>

### 11.7 Idempotency & exactly-once delivery

- *Stripe engineering – Idempotency keys*: <https://stripe.com/blog/idempotency>
- *Confluent – Exactly-once semantics are possible: here's how*: <https://www.confluent.io/blog/exactly-once-semantics-are-possible-heres-how-apache-kafka-does-it/>

### 11.8 Observability of async flows

- **OpenTelemetry – Messaging semantic conventions** (so trace context survives the outbox hop): <https://opentelemetry.io/docs/specs/semconv/messaging/>
- *Grafana – Tracing async systems*: <https://grafana.com/blog/2023/02/09/distributed-tracing-with-grafana-tempo-and-opentelemetry/>

### 11.9 Hands-on (after reading)

1. Spin up Debezium + Postgres + Kafka with the official Debezium tutorial: <https://debezium.io/documentation/reference/stable/tutorial.html>
2. Re-do the tutorial with the Outbox Event Router instead of plain table CDC.
3. Then implement Phase 2 of §6 against `order-service`.

## 12. Cache design — see separate document

**Cache strategy has been moved to its own document: `cache-ap-strategy.md`** (in this same `tasks/` folder).

That document prioritises **Availability + Partition Tolerance (AP)** per CAP theorem — appropriate for a 1M-user system where degraded-but-up beats correct-but-down.

Key principle: **Cache is AP, saga is CP.** This plan (`saga-outbox-plan.md`) handles the CP write path (outbox + Debezium). The cache document handles the AP read path (Caffeine + Redis Cluster with stale-while-error fallbacks).

Read order:

1. This document (`saga-outbox-plan.md`) end-to-end first — understand the saga + outbox.
2. Then `cache-ap-strategy.md` for read-path optimisation under partition.

The two are independent: you can ship outbox without cache changes, or cache changes without outbox. They compose to give the latency targets in §10.7 / §12.7 of the cache doc.

<details>
<summary>Historical context (original inline §12 content)</summary>

The earlier version of §12 inlined cache design here. It mixed CP and AP guidance which created confusion. Specifically:

- Original suggested Caffeine + single-node Redis — single-node Redis is a single point of failure, **violates AP**.
- Original used short TTL as primary invalidation — under partition this turns into "no cache", **degrades A**.
- Original idempotency check assumed Redis always available — **CP assumption**.

`cache-ap-strategy.md` fixes all three: Redis Cluster, event-driven invalidation with stale-if-error as backstop, and graceful idempotency degradation.

</details>

## 13. Done definition

- All five write paths listed in §1 commit aggregate + outbox in one tx, no `streamBridge.send` left in production code.
- E2E test `OutboxToKafkaE2EIT` green for order, inventory, catalog.
- Killing the app between DB commit and process exit still results in the event being delivered after restart.
- `legacy-publish` profile removed.
