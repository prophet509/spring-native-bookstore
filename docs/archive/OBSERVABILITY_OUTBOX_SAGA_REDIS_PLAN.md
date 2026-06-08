# Observability, Outbox Saga, and Redis Write-Ahead Plan

> Scope: assess current progress, define the target architecture, and provide an implementation order that fits the existing Spring Native Bookstore codebase.

## 1. Current Progress

### 1.1 What is already in place

| Area | Status | Evidence |
|---|---|---|
| Prometheus endpoints | Partial implementation exists | `catalog-service`, `order-service`, `inventory-service` expose `/actuator/prometheus` in local app config |
| Metrics registry | Implemented in main services | `micrometer-registry-prometheus` present in `catalog-service`, `order-service`, `config-service`, `edge-service`, others |
| Trace correlation in logs | Partial | services already use `logging.pattern.level` with `%X{trace_id}` and `%X{span_id}` |
| Platform observability stack | Implemented locally | Docker/K8s manifests for `prometheus`, `grafana`, `tempo`, `loki`, `fluent-bit` exist under `polar-deployment/` |
| Redis platform dependency | Implemented | `polar-redis` exists in Docker and K8s manifests |
| Edge Redis usage | Implemented | `edge-service` already uses Redis for session and rate limiting |
| Order event flow | Implemented, but not durable | `SubmitOrderService` persists order then publishes event immediately |
| Inventory decision flow | Implemented, but not durable | `ReserveStockService` publishes decision directly via `StreamBridge` |
| Catalog book event flow | Implemented, but not durable | `KafkaBookEventPublisher` emits directly to sinks/Kafka-facing functions |
| Problem Details API errors | Started | `docs/api-error-catalog.md` exists; service handlers already exist in some services |

### 1.2 Main gaps

| Gap | Why it matters |
|---|---|
| Services still depend on Java agent packaging for tracing | creates drift with the newer Spring Boot 4 OTel starter approach already described in `docs/tasks/observability.md` |
| Logging is correlated but not yet structured JSON | harder to query in Loki/Grafana |
| No durable outbox in `order-service` or `catalog-service` | Kafka outage can still break correctness or create event loss windows |
| No durable outbound pattern in `inventory-service` | inventory decisions are still direct sends after state changes |
| Saga state is implicit in order status transitions | hard to observe, retry, timeout, or compensate reliably |
| Redis is present but not used for idempotency / coordinator state / wake-up signaling | leaves throughput and recovery benefits on the table |

### 1.3 Current architectural weak point

The current order path is:

1. `order-service` loads book data.
2. `order-service` saves the order.
3. `order-service` immediately publishes `order-created`.
4. `inventory-service` reserves stock and immediately publishes `inventory-events`.
5. `order-service` updates order and immediately publishes `order-accepted`.
6. `dispatcher-service` emits `order-dispatched`.

This works functionally, but durability is weak around event publication. A DB write can succeed while Kafka publication fails or is ambiguous.

## 2. Recommended Combined Design

### 2.1 Design decision

Use:

- PostgreSQL outbox as the system of record for business events
- Saga orchestration/choreography on top of durable events
- Redis as a write-ahead accelerator and coordination layer, not the primary source of truth

### 2.2 Why Redis should not replace the DB outbox

If the goal is correctness, Redis alone is the wrong anchor for the outbox because:

- the business write and Redis write are not in one local transaction
- Redis durability depends on AOF/fsync policy and still sits outside the order/catalog transaction
- recovery is simpler when the authoritative pending event lives beside the aggregate in PostgreSQL

So the safe pattern is:

- write business state + outbox row in one PostgreSQL transaction
- after commit, optionally push a lightweight wake-up record into Redis
- publisher workers can consume the Redis hint first, then fall back to scanning the DB outbox

That gives both safety and speed.

### 2.3 Target architecture

```text
Client
  -> edge-service
  -> order-service
     -> PostgreSQL transaction:
        save order
        save outbox_event(order.submitted)
     -> after commit:
        push wake-up key/stream entry to Redis

Outbox publisher worker
  -> pops Redis hint or polls DB
  -> locks event
  -> publishes to Kafka
  -> marks outbox row SENT / FAILED / RETRY
  -> updates Redis dedupe/recent-event state

inventory-service
  -> consumes order event
  -> reserves / rejects stock
  -> writes local state
  -> writes its own outbox event
  -> publisher emits inventory decision

order-service
  -> consumes inventory decision
  -> transitions saga state
  -> writes outbox_event(order.accepted or order.cancelled)

dispatcher-service
  -> consumes order.accepted
  -> dispatches order
  -> emits order.dispatched
```

## 3. Observability Target

### 3.1 Minimum target

- All services export metrics, traces, and correlated logs
- One request trace should be visible across HTTP and Kafka boundaries
- Every business event log should contain:
  - `traceId`
  - `spanId`
  - `eventId`
  - `eventType`
  - `aggregateId`
  - `sagaId` when applicable
- Dashboards must show:
  - request latency/error rate
  - outbox pending and retry depth
  - saga success/rejection/timeout counts
  - consumer lag / publish failures

### 3.2 Recommended implementation choice

Follow the direction already described in `docs/tasks/observability.md`:

- replace runtime Java agent dependency with Spring Boot 4 OpenTelemetry starter
- keep Prometheus
- keep Tempo and Grafana
- move toward structured JSON logging
- keep platform-level collection centralized

### 3.3 Observability metrics to add

| Metric | Type | Service |
|---|---|---|
| `bookstore_outbox_pending` | gauge | order, catalog, inventory |
| `bookstore_outbox_publish_total` | counter | order, catalog, inventory |
| `bookstore_outbox_publish_failures_total` | counter | order, catalog, inventory |
| `bookstore_outbox_publish_latency` | timer | order, catalog, inventory |
| `bookstore_saga_started_total` | counter | order |
| `bookstore_saga_completed_total` | counter | order |
| `bookstore_saga_rejected_total` | counter | order |
| `bookstore_saga_timeout_total` | counter | order |
| `bookstore_idempotency_hits_total` | counter | inventory, order |
| `bookstore_redis_write_ahead_enqueue_total` | counter | order, catalog, inventory |
| `bookstore_redis_write_ahead_miss_total` | counter | publisher workers |

## 4. Saga Model For This Repo

### 4.1 Recommended first saga

Implement one clear saga first:

- `PlaceOrderSaga`

States:

1. `SUBMITTED`
2. `INVENTORY_PENDING`
3. `INVENTORY_RESERVED`
4. `ACCEPTED`
5. `REJECTED`
6. `DISPATCH_PENDING`
7. `DISPATCHED`
8. `TIMED_OUT`
9. `CANCELLED`

### 4.2 Current mapping

Current code already has parts of this flow, but state handling is distributed across:

- `order-service` `SubmitOrderService`
- `inventory-service` `ReserveStockService`
- `order-service` `ProcessInventoryDecisionService`
- `dispatcher-service` `DispatcherFunctions`

The missing part is explicit saga metadata and timeout/recovery behavior.

### 4.3 Saga data recommendation

Create a dedicated saga table in `order-service`, for example:

`order_sagas`

Suggested fields:

- `saga_id`
- `order_id`
- `state`
- `version`
- `started_at`
- `updated_at`
- `deadline_at`
- `last_event_id`
- `failure_reason`
- `trace_id`

This is optional if you want to keep saga state inside `orders`, but a separate table is cleaner for retries, observability, and operations.

## 5. Redis Write-Ahead Pattern

### 5.1 Recommended Redis role

Use Redis for three things:

1. Wake-up queue for new outbox work
2. Idempotency keys for consumers
3. Short-lived saga coordination cache

### 5.2 Pattern details

#### A. Wake-up queue

After the DB transaction commits and inserts an outbox row:

- push `eventId` into Redis Stream or List
- publisher workers read from Redis first
- if Redis entry is missing or lost, scheduled DB polling still finds `PENDING` rows

This is the safest combined model.

#### B. Idempotency keys

For each consumed event:

- write `idempotency:{consumer}:{eventId}` with TTL
- if key already exists, skip duplicate processing
- still keep DB-level guards for business correctness where required

#### C. Saga cache

Optionally store a compact view:

- `saga:{sagaId}` -> current state, deadline, last event

Use this only as a cache/operator aid. PostgreSQL remains authoritative.

### 5.3 Redis data structure choice

Recommended:

- Redis Stream for wake-up events if you want consumer groups and visibility
- simple `SET NX EX` for idempotency keys
- Hash or JSON-like hash fields for saga snapshots

If you want the smallest implementation first:

- use Redis List or Stream for wake-up
- use `SET NX EX` for idempotency
- skip saga cache until phase 2

### 5.4 Failure model

| Failure | Expected behavior |
|---|---|
| Kafka down | DB commit succeeds, outbox stays `PENDING`, Redis wake-up may repeat, publisher retries later |
| Redis down | DB commit still succeeds, scheduled DB polling still publishes later |
| Duplicate Kafka delivery | consumer idempotency key prevents duplicate business transition |
| Service restart | workers resume from DB `PENDING` records |
| Partial publish uncertainty | outbox row remains retryable until acknowledged by publish result handling |

## 6. Implementation Sequence

### Phase 0: Baseline audit

- confirm all current messaging bindings per service
- confirm current actuator exposure and dashboards
- confirm existing Flyway migration numbering
- confirm where `trace_id` / `span_id` are already present in logs

### Phase 1: Finish observability baseline first

Services:

- `config-service`
- `catalog-service`
- `order-service`
- `inventory-service`
- `dispatcher-service`
- `edge-service`
- `search-service`

Tasks:

- replace Java agent packaging approach with Spring Boot OTel starter
- standardize management/OTLP config through `config/`
- switch prod profile logging to structured JSON
- add log fields for `eventId`, `eventType`, `orderId`, `isbn`, `sagaId`
- verify one HTTP trace and one Kafka trace end-to-end

Definition of done:

- traces visible in Tempo
- metrics visible in Prometheus
- logs queryable in Grafana/Loki
- one order flow trace spans edge -> order -> inventory -> dispatcher

### Phase 2: Durable outbox in `order-service`

Reason:

This is the highest-value correctness fix because order creation is the start of the business flow.

Tasks:

- add `outbox_events` table via Flyway
- create outbox domain model/repository
- change `SubmitOrderService` so it saves:
  - order row
  - outbox row for `order.submitted`
  in one transaction
- remove direct publish from request path
- add outbox publisher worker
- add Redis wake-up push after commit
- add scheduled DB backstop poller

Suggested outbox fields:

- `event_id`
- `aggregate_type`
- `aggregate_id`
- `event_type`
- `payload`
- `headers`
- `status`
- `attempt_count`
- `next_attempt_at`
- `created_at`
- `published_at`
- `last_error`
- `trace_id`
- `saga_id`

Definition of done:

- order save does not depend on Kafka availability
- pending outbox rows are visible and retryable
- duplicate publisher execution is safe

### Phase 3: Durable outbound in `inventory-service`

Tasks:

- add outbox table/migration
- when inventory reserves or rejects stock:
  - persist inventory/reservation state
  - persist outbox event for `inventory.reserved` or `inventory.rejected`
- publisher emits from outbox instead of direct `StreamBridge` send
- add consumer idempotency keying for `order-created` inputs

Reason:

This makes the middle of the saga durable and debuggable.

### Phase 4: Durable outbound in `catalog-service`

Tasks:

- add outbox for `book.created`, `book.updated`, `book.deleted`
- replace direct sink emission with outbox rows
- add publisher worker and metrics

Reason:

This is not the first business-critical saga path, but it aligns the catalog/search side with the same reliability model.

### Phase 5: Explicit `PlaceOrderSaga`

Tasks:

- add saga table or extend order state model with explicit saga metadata
- create saga transition service in `order-service`
- emit events based on state transitions, not ad hoc direct calls
- add timeout scanner for stuck sagas
- on timeout:
  - mark saga timed out
  - emit cancellation/compensation event when appropriate

Definition of done:

- each saga has a visible state
- retries do not corrupt state
- operators can query stuck sagas

### Phase 6: Redis enhancement layer

Tasks:

- add Redis dependency to services that need write-ahead coordination
- implement wake-up enqueue after outbox insert commit
- implement `SET NX EX` idempotency keys on consumers
- optionally add distributed publisher lock using Redis only if DB locking is insufficient
- optionally add saga cache views for fast operator lookups

Important:

Do not require Redis for correctness. Redis should improve latency and coordination, not be the only place pending work exists.

## 7. Concrete File Plan

### 7.1 Observability

- `docs/tasks/observability.md`
- `config/*-service.yml`
- `config/*-service-prod.yml`
- `*/build.gradle`
- `polar-deployment/docker/docker-compose.yml`
- `polar-deployment/docker/platform/*`
- optional `docs/observability.md`

### 7.2 Order outbox + saga

- `order-service/src/main/resources/db/migration/V<next>__create_outbox_events.sql`
- `order-service/src/main/resources/db/migration/V<next>__create_order_sagas.sql`
- `order-service/src/main/java/.../application/service/SubmitOrderService.java`
- `order-service/src/main/java/.../application/service/ProcessInventoryDecisionService.java`
- new `order-service/src/main/java/.../outbox/*`
- new `order-service/src/main/java/.../saga/*`
- new `order-service/src/test/java/...`

### 7.3 Inventory outbox + idempotency

- `inventory-service/src/main/resources/db/migration/V<next>__create_outbox_events.sql`
- `inventory-service/src/main/java/.../application/service/ReserveStockService.java`
- `inventory-service/src/main/java/.../adapter/in/messaging/OrderEventConsumer.java`
- new `inventory-service/src/main/java/.../outbox/*`
- new `inventory-service/src/main/java/.../idempotency/*`

### 7.4 Catalog outbox

- `catalog-service/src/main/resources/db/migration/V<next>__create_outbox_events.sql`
- `catalog-service/src/main/java/.../adapter/out/messaging/KafkaBookEventPublisher.java`
- new `catalog-service/src/main/java/.../outbox/*`

## 8. Testing Plan

### 8.1 Observability checks

- `curl http://localhost:9001/actuator/prometheus`
- `curl http://localhost:9002/actuator/prometheus`
- verify one trace in Tempo after placing an order
- verify logs contain `traceId`/`spanId` and business identifiers

### 8.2 Outbox checks

- stop Kafka
- create order
- verify order persists
- verify outbox row is `PENDING`
- restart Kafka
- verify publisher sends pending row
- verify row becomes `SENT`

### 8.3 Idempotency checks

- replay the same `order-created` message twice
- ensure inventory is reserved once
- replay same `inventory decision` twice
- ensure order state changes once

### 8.4 Saga checks

- happy path: submitted -> reserved -> accepted -> dispatched
- reject path: submitted -> rejected
- timeout path: submitted -> timed out -> cancelled/compensated

## 9. Recommended Delivery Order

If you want the lowest-risk execution order, do this:

1. Complete observability baseline.
2. Add durable outbox to `order-service`.
3. Add idempotency + durable outbox to `inventory-service`.
4. Make saga state explicit in `order-service`.
5. Add Redis wake-up queue and dedupe keys.
6. Add catalog outbox.
7. Add dashboards/runbooks.

## 10. Short Recommendation

Use PostgreSQL outbox for correctness, saga state for workflow control, and Redis as a non-authoritative write-ahead/coordination layer.

That combination fits this repo better than a Redis-only write-ahead design because it preserves transactional integrity while still giving you fast wake-up, dedupe, and recovery support.
