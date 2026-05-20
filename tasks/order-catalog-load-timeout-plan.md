# Order Catalog Load Timeout Plan

> Muc tieu: lam ro va sua bottleneck `POST /orders` khi load test 1000 request. Hien tuong hien tai: 1000 request voi concurrency 50 chi co 943 HTTP 200, 57 request tra `curl` code `000`, inventory reserve dung toi da 100 nhung order submission van bi timeout mot phan.

> Pham vi: uu tien `order-service`, `catalog-service`, va `scripts/load-test-orders.sh`. Khong doi business rule inventory: voi stock 100 thi toi da chi reserve 100.

## Current Findings

- [x] `order-service` goi sync sang `catalog-service` cho moi `POST /orders` qua `CatalogWebClientAdapter.loadBook()`.
- [x] `CatalogWebClientAdapter` hien dang map moi WebClient error thanh `BookNotFoundException`, nen timeout/connection error bi che thanh loi book-not-found.
- [x] `order-service` WebClient co `responseTimeout=5s`, read/write timeout `10s`.
- [x] `catalog-service` local Tomcat max threads la `50`, Hikari pool max la `5`.
- [x] Load test sach `1000/50` sau restart: `943 HTTP 200`, `57 HTTP 000`.
- [x] Inventory sau load test dung: `available=0`, `reserved=100`, `reservation rows=100`, Kafka inventory lag `0`.
- [x] `HTTP 000` la client khong nhan response, khong phai server HTTP status.

## Working Hypothesis

`POST /orders` dang bi phu thuoc vao synchronous catalog lookup. Khi concurrency cao, catalog lookup/connection pool/order DB/Kafka publish tao latency lon hon `curl --max-time 10` cho mot so request. Vi error handling catalog dang che mat cause, log hien tai khong chi ro timeout o catalog hay backlog o order-service.

## Success Criteria

- [ ] Load test `./scripts/load-test-orders.sh 1000 50` co `HTTP 000 = 0` hoac giam ve threshold chap nhan duoc co giai thich ro.
- [ ] `order-service` log phan biet duoc `404 book-not-found`, `catalog timeout`, `catalog 5xx`, va connection error.
- [ ] `order-service` tra status phu hop khi catalog unavailable: `503 downstream-unavailable`, khong tra nham `404 book-not-found`.
- [ ] Metrics/log co du thong tin de biet bottleneck nam o catalog, order DB, Kafka, hay client timeout.
- [ ] Inventory van khong am voi 1000 request cung ISBN.
- [ ] Sau test sach: reserved toi da bang initial stock, order rejected cho phan vuot stock, Kafka lag ve 0.

---

## Phase 1: Improve Diagnosis Before Tuning

### 1.1 Fix Catalog Error Mapping In `order-service`

**Files expected:**

- `order-service/src/main/java/com/locpham/bookstore/orderservice/adapter/out/catalog/CatalogWebClientAdapter.java`
- `order-service/src/main/java/com/locpham/bookstore/orderservice/domain/exception/*`
- `order-service/src/main/java/com/locpham/bookstore/orderservice/adapter/in/web/GlobalExceptionHandler.java` if present
- relevant tests under `order-service/src/test/java`

Tasks:

- [ ] Handle `404` from catalog as `BookNotFoundException` only.
- [ ] Handle `5xx`, timeout, connection refused as downstream/catalog unavailable exception.
- [ ] Add logging with fields: `isbn`, `catalogServiceUrl`, `errorClass`, `message`, elapsed time.
- [ ] Do not log JWT or request Authorization headers.
- [ ] Add/adjust tests for 404 vs timeout/5xx behavior.

Expected behavior:

| Catalog outcome | Order API behavior |
|---|---|
| 200 book found | create `PENDING` order |
| 404 book missing | 404 `book-not-found` |
| timeout/connect error | 503 `catalog-unavailable` or `downstream-unavailable` |
| 5xx | 503 `catalog-unavailable` or `downstream-unavailable` |

Verify:

```bash
cd order-service && ./gradlew test --tests '*Catalog*'
cd order-service && ./gradlew test
```

### 1.2 Add Request Timing Around Submit Flow

Tasks:

- [ ] Add concise INFO/DEBUG logs around submit order stages: load catalog, save order, publish order-created event.
- [ ] Include `orderId` after save, `isbn`, and elapsed millis.
- [ ] Keep logs sampled or DEBUG if too noisy.

Why:

- Need to know if `HTTP 000` happens before catalog returns, while saving order, or while publishing Kafka event.

### 1.3 Improve Load Test Output

**File:** `scripts/load-test-orders.sh`

Tasks:

- [ ] Keep result file path when failures occur instead of always deleting it.
- [ ] Print count of `000` separately as client/network timeout.
- [ ] Print min/max/avg request duration if possible using curl `time_total`.
- [ ] Add optional env vars: `ORDER_TIMEOUT_SECONDS`, `CONNECT_TIMEOUT_SECONDS`.
- [ ] Exit non-zero when `HTTP 000 > 0`, unless `ALLOW_HTTP_000=true`.
- [ ] After run, query or print recommended DB checks.

Verify:

```bash
bash -n scripts/load-test-orders.sh
./scripts/load-test-orders.sh 10 2
```

---

## Phase 2: Remove Avoidable Catalog Bottleneck

### 2.1 Cache Catalog Book Snapshot In `order-service`

Goal: avoid calling `catalog-service` 1000 times for the same ISBN during a burst.

Options:

| Option | Pros | Cons |
|---|---|---|
| Caffeine local cache | simple, fast, no infra | per-instance cache, needs TTL |
| Redis cache | shared across instances | extra dependency/config |
| rely on catalog cache only | central place | order still pays network hop |

Recommendation for this repo stage: start with small Caffeine local cache in `order-service`, TTL 1-5 minutes, max size bounded.

Tasks:

- [ ] Add cache dependency/config if not already available.
- [ ] Cache successful `BookSnapshot` by ISBN.
- [ ] Do not cache failures initially, or cache 404 briefly with very small TTL only if needed.
- [ ] Add tests proving repeated same ISBN calls catalog once within TTL.
- [ ] Document staleness tradeoff: order uses snapshot price/title at submission time.

Verify:

```bash
cd order-service && ./gradlew test
./scripts/load-test-orders.sh 1000 50
```

Expected improvement:

- Catalog receives far fewer calls for repeated ISBN.
- `HTTP 000` should drop significantly if catalog was bottleneck.

### 2.2 Tune Catalog Local Capacity

Tasks:

- [ ] Raise catalog Hikari pool from `5` to a measured value, e.g. `10` or `20`, if DB can handle it.
- [ ] Consider raising Tomcat `threads.max` if thread starvation is observed.
- [ ] Keep changes in config (`catalog-service/src/main/resources/application.yml` or `config/catalog-service.yml`) instead of hardcoding.
- [ ] Add comments only if values are non-obvious.

Verify:

```bash
cd catalog-service && ./gradlew test
curl -s http://localhost:9001/actuator/metrics/hikaricp.connections.active
```

### 2.3 Tune `order-service` Pools And Timeouts

Tasks:

- [ ] Review R2DBC pool `max-size: 10` versus load concurrency `50`.
- [ ] Increase pool only after confirming DB is bottleneck.
- [ ] Make WebClient timeout properties configurable from YAML.
- [ ] Keep timeout explicit; do not just set huge timeouts to hide slowness.

Candidate config:

```yaml
polar:
  catalog-service-url: http://localhost:9001
  catalog-client:
    connect-timeout: 2s
    response-timeout: 3s
```

---

## Phase 3: Resilience Behavior

### 3.1 Add Circuit Breaker / Time Limiter For Catalog Calls

Goal: catalog slowdown should not hang all order requests.

Tasks:

- [ ] Add Resilience4j or Spring Cloud Circuit Breaker if already planned in production roadmap.
- [ ] Configure circuit breaker for catalog lookup.
- [ ] Configure time limiter shorter than external client timeout.
- [ ] Return `503 downstream-unavailable` when circuit open or timeout.
- [ ] Expose circuit breaker metrics through actuator.

Verify:

```bash
cd order-service && ./gradlew test
curl -s http://localhost:9002/actuator/metrics | grep resilience || true
```

### 3.2 Decide Fallback Policy

Important decision:

- [ ] If catalog is unavailable, should order be rejected immediately?
- [ ] Or should order be accepted with stale cached snapshot?

Recommended initial behavior:

- Use fresh/cache snapshot when available.
- If no cached snapshot and catalog unavailable, return `503`.
- Do not create order with unknown book details.

---

## Phase 4: State Consistency Checks

### 4.1 Make Load Test Validate Whole Flow

Tasks:

- [ ] Validate `reserved_quantity == min(initial_stock, successful_order_count)` only after Kafka lag reaches 0.
- [ ] Validate `available_quantity >= 0`.
- [ ] Validate reservation rows match inventory reserved quantity.
- [ ] Validate no stale `PENDING` after a wait window, or explicitly report pending count.
- [ ] Print order status counts after run.

### 4.2 Investigate Remaining `PENDING` Orders

Current post-test example:

```text
DISPATCHED  100
PENDING     399
REJECTED    444
```

Tasks:

- [ ] Check why some orders remain `PENDING` after inventory consumer lag is 0.
- [ ] Confirm whether order-service `handleInventoryDecision` consumed all `inventory-events`.
- [ ] Check order-service Kafka consumer lag for `inventory-events`.
- [ ] Add retry/error handling/logging for inventory decision consumer.

Verify:

```bash
docker exec polar-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-service
```

---

## Phase 5: Final Verification Matrix

Run from clean DB state:

```bash
docker exec polar-postgres-order psql -U user -d polardb_order -c "truncate table orders restart identity cascade;"
docker exec polar-postgres-inventory psql -U user -d polardb_inventory -c "truncate table reservation cascade; update inventory set available_quantity = 100, reserved_quantity = 0, version = 0 where isbn = '9781617296956';"
```

Scenarios:

| Scenario | Command | Expected |
|---|---|---|
| Smoke | `./scripts/load-test-orders.sh 10 2` | 10 2xx, reserved increases by 10 |
| Capacity exact | `./scripts/load-test-orders.sh 100 20` | reserved 100, available 0, no negative |
| Oversubscribe | `./scripts/load-test-orders.sh 1000 50` | no negative, 100 reserved, excess rejected |
| Catalog down | stop catalog then POST order | 503 downstream unavailable, no order persisted |
| Catalog slow | inject delay | circuit opens/timeouts visible |

Required checks after each run:

```bash
docker exec polar-postgres-order psql -U user -d polardb_order -c "select status, count(*) from orders group by status order by status;"
docker exec polar-postgres-inventory psql -U user -d polardb_inventory -c "select isbn, available_quantity, reserved_quantity, version from inventory where isbn='9781617296956'; select status, count(*), coalesce(sum(quantity),0) from reservation where isbn='9781617296956' group by status order by status;"
docker exec polar-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group inventory-service
docker exec polar-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-service
```

## Open Questions

- [ ] Co can order-service cache book snapshot khong, hay bat buoc always read latest catalog?
- [ ] Khi catalog unavailable, API nen fail `503` hay accept order voi cached snapshot?
- [ ] Load test muc tieu la stress local dev hay SLO gan production?
- [ ] `PENDING` order sau inventory decision bao lau thi xem la loi?

## Recommended Implementation Order

1. Fix catalog error mapping and logging in `order-service`.
2. Improve load test observability and fail conditions.
3. Run clean `1000/50` again to confirm exact bottleneck.
4. Add Caffeine cache for catalog snapshots if catalog lookup is confirmed hot path.
5. Tune pools/timeouts based on metrics.
6. Add circuit breaker/time limiter.
7. Investigate and fix stale `PENDING` orders.
