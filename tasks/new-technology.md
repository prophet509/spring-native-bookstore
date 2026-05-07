# 🔬 Technology Deep-Dive Plan v2 — spring-native-bookstore

> **Mục tiêu:** Senior Engineer không chỉ biết implement pattern — mà hiểu **tại sao**, **khi nào**, và **trade-off** của mỗi quyết định.
> Format: **[Service]** → **[Task]** → **[Verify]** → **[Senior Insight]**
> Cập nhật `⬜ → 🔄 → ✅` khi tiến hành.

---

## 📦 MODULE 1 — Foundation Professionalism
> **Thời gian:** 1–2 tuần
> **Senior differentiator:** Code production-ready ngay từ task đầu tiên.

---

### 1.1 Global Exception Handling (RFC 7807)

**Service:** `catalog-service`, `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Senior không chỉ catch exception — mà thiết kế **error contract** cho toàn bộ API ecosystem. Client team phải biết chính xác cách đọc lỗi mà không cần hỏi backend.

**Tasks:**
- [ ] Tạo `GlobalExceptionHandler` với `@RestControllerAdvice` trong mỗi service
- [ ] Định nghĩa `ProblemDetail` chuẩn RFC 7807 (Spring Boot 3+ hỗ trợ native):
  ```json
  {
    "type": "https://bookstore.api/errors/book-not-found",
    "title": "Book Not Found",
    "status": 404,
    "detail": "Book with ISBN 978-3-16-148410-0 does not exist",
    "instance": "/books/978-3-16-148410-0",
    "timestamp": "2026-04-29T05:00:00Z",
    "traceId": "abc123"
  }
  ```
- [ ] Map domain exceptions:
  - `BookNotFoundException` → 404
  - `InsufficientStockException` → 422
  - `OrderAlreadyProcessedException` → 409
  - `ConstraintViolationException` → 400
- [ ] Reactive (WebFlux) riêng: `@ControllerAdvice` + `WebExceptionHandler` trong `order-service`
- [ ] **Không bao giờ** lộ stack trace trên production profile
- [ ] **Senior Insight:** Viết **error catalog document** — bảng tra cứu tất cả error codes cho client team. Senior nghĩ cho người dùng API, không chỉ code cho server chạy.

**Verify:**
```bash
curl -v http://localhost:9001/books/isbn-fake
# Kỳ vọng: 404 với ProblemDetail body, không phải Whitelabel Error Page
```

---

### 1.2 Validation — Multi-Layer Strategy

**Service:** `catalog-service`, `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Junior validate ở controller. Senior phân biệt **3 tầng validation** — mỗi tầng có mục đích khác nhau, fail khác nhau, handle khác nhau.

**Tasks:**
- [ ] **Layer 1 — Bean Validation (Jakarta):** Annotate request DTOs:
  - `@NotBlank`, `@NotNull`, `@Positive`, `@Size(max = 255)`
  - `@ISBN` cho ISBN fields
  - Enable `@Valid` / `@Validated` trong controllers
- [ ] **Layer 2 — Custom Cross-Field Validation:** Tạo `@ValidIsbn` custom validator (ISBN-13 checksum logic tự viết, no library)
- [ ] **Layer 3 — Business Rule Validation:** Trong domain service, không phải controller:
  - `InsufficientStockException` → 422 (business rule, không phải format error)
  - `OrderAlreadyProcessedException` → 409 (state conflict)
- [ ] Phân biệt rõ: **Constraint Violation** (400) vs **Business Rule Violation** (422/409)
- [ ] Test `GlobalExceptionHandler` cho mọi validation scenario

**Senior Insight:**
```
Layer 1 (400 Bad Request)     → "Format request sai"  → Controller/DTO
Layer 2 (422 Unprocessable)   → "Semantic data sai"    → Custom validator
Layer 3 (422/409)             → "Business rule vi phạm" → Domain service

Junior often conflates Layer 1 and Layer 3 → inconsistent error responses.
```

**Verify:**
```bash
curl -X POST http://localhost:9001/books -d '{"title":""}' -H "Content-Type: application/json"
# Kỳ vọng: 400 với field-level error details
```

---

### 1.3 Structured Logging & Correlation

**Service:** Tất cả services

**Tại sao Senior cần biết:**
Incident lúc 3am — log có thể search trên ELK/CloudWatch là khác biệt giữa fix trong 15 phút và fix cả đêm. Senior thiết kế **log strategy** trước khi code business logic.

**Tasks:**
- [ ] Cấu hình Logback với `logstash-logback-encoder` cho JSON output:
  ```json
  {
    "timestamp": "2026-04-29T05:00:00.000Z",
    "level": "INFO",
    "service": "order-service",
    "traceId": "abc123",
    "spanId": "def456",
    "userId": "user-789",
    "orderId": "order-001",
    "message": "Order submitted successfully",
    "durationMs": 45
  }
  ```
- [ ] MDC Filter tự động inject `traceId`, `spanId`, `userId` vào mọi log line
- [ ] Log profile: `local` = human-readable console, `prod` = JSON
- [ ] **Data masking:** Regex filter log NEVER expose password, JWT token, credit card
- [ ] Business milestone logs: `order.placed`, `stock.reserved`, `order.dispatched`
- [ ] **Senior Insight:** Thêm `log correlation test` — verify traceId xuyên suốt HTTP → Kafka → consumer

**Verify:**
```bash
cd order-service && ./gradlew bootRun --args='--spring.profiles.active=prod'
# Mỗi dòng log = valid JSON object
```

---

### 1.4 API Versioning & Documentation

**Service:** `catalog-service`, `order-service`

**Tại sao Senior cần biết:**
API là **contract** với client. Thay đổi API mà không versioning = break production. Senior plan cho backward compatibility từ ngày đầu.

**Tasks:**
- [ ] **Versioning strategy** — chọn URI versioning cho REST simplicity:
  - `GET /api/v1/books` vs `GET /api/v2/books`
  - Configure via Spring `WebMvcConfigurer` / `WebFluxConfigurer`
- [ ] **OpenAPI Documentation** với Springdoc OpenAPI:
  ```yaml
  # build.gradle
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'
  ```
- [ ] Annotate controllers với `@Operation`, `@ApiResponses`, `@Schema`:
  ```java
  @Operation(summary = "Get book by ISBN", description = "Returns a single book")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Book found"),
      @ApiResponse(responseCode = "404", description = "Book not found",
          content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  ```
- [ ] Generate OpenAPI spec JSON/YAML tại build time
- [ ] Verify Swagger UI tại `/swagger-ui.html`
- [ ] **Senior Insight:** Khi thêm field mới vào response → v1 vẫn hoạt động, v2 có field mới. **Không bao giờ xóa field** trong cùng version.

**Verify:**
```bash
curl http://localhost:9001/v3/api-docs
# Kỳ vọng: valid OpenAPI 3.0 spec JSON
```

---

## 🗄️ MODULE 2 — Database Mastery
> **Thời gian:** 2 tuần
> **Senior differentiator:** Biết DB là bottleneck #1 của hầu hết hệ thống. Tối ưu DB trước khi nghĩ đến cache.

---

### 2.1 Connection Pool Tuning (HikariCP)

**Service:** `catalog-service`, `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Connection pool sai config = deadlock dưới load, hoặc waste resources. Junior dùng default, Senior tune theo deployment environment.

**Tasks:**
- [ ] Hiểu và tune HikariCP config cho mỗi service:
  ```yaml
  spring:
    datasource:
      hikari:
        maximum-pool-size: 20        # CPU cores * 2 + effective spindle count
        minimum-idle: 5
        idle-timeout: 300000         # 5 minutes
        connection-timeout: 2000      # fail fast if no connection
        max-lifetime: 1800000        # 30 minutes (must < DB wait_timeout)
        leak-detection-threshold: 60000 # alert if connection held > 60s
  ```
- [ ] **Formula:** `pool_size = Tn × (Cm - 1) + 1` donde Tn = concurrent threads, Cm = concurrent connections per thread
- [ ] Monitor pool metrics qua HikariCP Micrometer integration:
  - `hikaricp.connections.active`
  - `hikaricp.connections.idle`
  - `hikaricp.connections.pending`
  - `hikaricp.connections.timeout.total`
- [ ] **Senior Insight:** R2DBC (order-service) dùng connection pool khác — `r2dbc-pool`. Config khác HikariCP. Hiểu sự khác biệt reactive vs blocking pool.

**Verify:**
```bash
curl http://localhost:9001/actuator/metrics/hikaricp.connections.active
# Monitor pool usage under load
```

---

### 2.2 Indexing Strategy & Query Optimization

**Service:** `catalog-service`, `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Query chậm不是因为 data nhiều — mà vì thiếu index hoặc sai index strategy. Senior đọc `EXPLAIN ANALYZE` như đọc sách.

**Tasks:**
- [ ] **EXPLAIN ANALYZE** mọi query quan trọng — đọc execution plan:
  ```sql
  EXPLAIN ANALYZE SELECT * FROM book WHERE isbn = '978-3-16-148410-0';
  ```
- [ ] Tạo indexes qua Flyway migrations:
  ```sql
  -- Catalog: ISBN lookup (most frequent query)
  CREATE UNIQUE INDEX idx_book_isbn ON book (isbn);

  -- Order: status filtering + pagination
  CREATE INDEX idx_order_status_created ON orders (status, created_at DESC);

  -- Inventory: stock reservation lookup
  CREATE INDEX idx_stock_reservation_order ON stock_reservation (order_id)
    WHERE status = 'PENDING';
  ```
- [ ] **Composite index ordering:** Most selective column first. Hiểu **index skip scan** limitation.
- [ ] **N+1 Problem detection:** Bật Hibernate/Spring Data JDBC SQL logging:
  ```yaml
  logging:
    level:
      org.springframework.jdbc.core: DEBUG
      org.springframework.r2dbc: DEBUG
  ```
- [ ] Fix N+1: dùng JOIN FETCH hoặc batch fetching
- [ ] **Partial Index** cho queries có WHERE clause phổ biến (như active orders)
- [ ] **Senior Insight:** Index trade-off — write performance giảm khi index tăng. Không index mọi thứ. Index = cân bằng giữa read và write.

**Verify:**
```sql
-- Before index
EXPLAIN ANALYZE SELECT * FROM orders WHERE status = 'PENDING' ORDER BY created_at DESC;
-- Note: Seq Scan → slow

-- After index
EXPLAIN ANALYZE SELECT * FROM orders WHERE status = 'PENDING' ORDER BY created_at DESC;
-- Note: Index Scan → fast
```

---

### 2.3 Zero-Downtime Database Migrations

**Service:** Tất cả services dùng Flyway

**Tại sao Senior cần biết:**
Migration chạy trên production lúc deploy — nếu sai, DB locked, service down. Senior thiết kế migration strategy **expand-contract** để deploy không downtime.

**Tasks:**
- [ ] Hiểu **Expand-Contract pattern** cho schema changes:
  ```
  Phase 1 (Expand): Add nullable column → deploy → safe, không break existing code
  Phase 2 (Migrate): Backfill data → deploy mới
  Phase 3 (Contract): Remove old column → deploy
  ```
- [ ] **Rules cho zero-downtime migrations:**
  - NEVER rename column directly → add new, migrate data, drop old
  - NEVER remove column trong 1 deploy → expand-contract qua 2 deploys
  - NEVER add NOT NULL column without default → add nullable → backfill → set NOT NULL
- [ ] Viết migration an toàn:
  ```sql
  -- SAFE: Add nullable column
  ALTER TABLE book ADD COLUMN description TEXT;

  -- UNSAFE (blocks reads on large tables):
  ALTER TABLE book ADD COLUMN description TEXT NOT NULL DEFAULT '';
  -- Better: Add nullable → backfill → ALTER SET NOT NULL
  ```
- [ ] Configure Flyway optimistic locking cho multi-instance deployment:
  ```yaml
  spring:
    flyway:
      fail-on-missing-locations: true
      validate-on-migrate: true
  ```
- [ ] **Senior Insight:** Vitale Ch.15 giải thích vì sao Flyway migration phải **backwards compatible** — rolling deploy means old và new code chạy song song.

**Verify:**
```bash
# Verify migration runs cleanly
cd catalog-service && ./gradlew flywayInfo
# Verify rollback strategy exists (document, not auto-rollback)
```

---

### 2.4 Database Connection Failover & Resilience

**Service:** `catalog-service`, `order-service`

**Tại sao Senior cần biết:**
DB fail → toàn bộ service fail. Senior biết configure connection resilience để survive DB restart.

**Tasks:**
- [ ] Configure HikariCP connection validation:
  ```yaml
  spring:
    datasource:
      hikari:
        validation-timeout: 1000
        connection-test-query: SELECT 1
  ```
- [ ] Configure R2DBC failover cho `order-service`:
  ```yaml
  spring:
    r2dbc:
      properties:
        preparedStatementCacheQueries: 256
        validateConnectionString: false
  ```
- [ ] Test: Stop PostgreSQL → verify service returns 503 (not 500) → restart PostgreSQL → verify auto-recovery
- [ ] **Senior Insight:** Production DB có failover (RDS Multi-AZ) — connection string phải support multiple hosts.

---

## 📡 MODULE 3 — Kafka Deep Dive
> **Thời gian:** 2–3 tuần
> **Senior differentiator:** Không chỉ "publish/consume" — hiểu Kafka internals để debug production issues.

---

### 3.1 Kafka Internals — Partitions, Consumer Groups, Offset Management

**Service:** `order-service`, `inventory-service`, `dispatcher-service`

**Tại sao Senior cần biết:**
"Kafka consume bị duplicate" hoặc "message bị delay" — Senior biết nguyên nhân là partition strategy, consumer group config, hay offset commit.

**Tasks:**
- [ ] **Partition Strategy:** Hiểu và cấu hình:
  ```yaml
  # Producer: quyết định message đi partition nào
  spring.kafka.producer.properties.partitioner.class: org.apache.kafka.clients.producer.RoundRobinPartitioner
  # Hoặc custom partitioner by orderId để đảm bảo ordering
  ```
- [ ] **Consumer Group:** Hiểu rebalancing:
  ```
  Scenario: 3 inventory-service instances → 3 partitions
  - Instance 1 consumes partition 0
  - Instance 2 consumes partition 1
  - Instance 3 consumes partition 2
  - If instance 2 dies → rebalance → instance 1 or 3 takes partition 1
  ```
- [ ] **Offset Management:**
  ```yaml
  spring:
    kafka:
      consumer:
        enable-auto-commit: false  # Manual commit = exactly-once
        auto-offset-reset: earliest # Khi consumer mới join group
  ```
- [ ] Implement **manual acknowledgment** trong Spring Cloud Stream:
  ```java
  @Bean
  public Consumer<Message<OrderPlacedEvent>> handleOrderPlaced() {
      return message -> {
          try {
              processOrder(message.getPayload());
              message.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class).acknowledge();
          } catch (Exception e) {
              // Don't ack → redelivery
          }
      };
  }
  ```
- [ ] **Senior Insight:** `auto-offset-reset: earliest` vs `latest` — chọn sai = mất message hoặc xử lý duplicate. Production nên dùng `earliest` cho new consumer groups.

**Verify:**
```bash
# Check consumer group lag
docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group inventory-service
# Kỳ vọng: LAG = 0 khi hệ thống healthy
```

---

### 3.2 Dead Letter Queue (DLQ)

**Service:** `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Message poison (bad data, deserialization error) → consumer crash loop → toàn bộ partition bị block. DLQ là safety net.

**Tasks:**
- [ ] Configure Spring Cloud Stream DLQ:
  ```yaml
  spring:
    cloud:
      stream:
        kafka:
          bindings:
            handleOrderPlaced-in-0:
              consumer:
                enable-dlq: true
                dlq-name: order-service-dlq
                max-attempts: 3              # retry 3 lần trước khi gửi DLQ
                back-off-initial-interval: 1000
                back-off-multiplier: 2.0
                back-off-max-interval: 10000
  ```
- [ ] Tạo **DLQ Consumer** để process failed messages:
  ```java
  @Bean
  public Consumer<Message<byte[]>> handleDlq() {
      return message -> {
          log.error("DLQ message received: headers={}, payload={}",
              message.getHeaders(), new String(message.getPayload()));
          // Alert via Slack/email
          // Store in dead_letter_events table for manual review
      };
  }
  ```
- [ ] **DLQ Dashboard:** Expose metric `bookstore.dlq.messages.total` cho monitoring
- [ ] **Replay mechanism:** Tool/script để replay DLQ messages sau khi fix bug
- [ ] **Senior Insight:** Retry không phải luôn đúng — **idempotent consumer** + **DLQ** > infinite retry. Hiểu khi nào retry vs khi nàoDLQ.

**Verify:**
```bash
# Send bad message to trigger DLQ
curl -X POST http://localhost:9002/orders -d '{"bad":"data"}'
# Check DLQ topic
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic order-service-dlq --from-beginning
```

---

### 3.3 Exactly-Once Semantics & Idempotent Consumer

**Service:** `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
"At-least-once delivery" = có thể nhận message trùng. Senior thiết kế consumer **idempotent by default**.

**Tasks:**
- [ ] **Idempotent Consumer Pattern:** Dùng `orderId` làm idempotency key:
  ```java
  public void handleOrderPlaced(OrderPlacedEvent event) {
      if (processedEventRepository.existsByEventId(event.eventId())) {
          log.info("Duplicate event detected: {}", event.eventId());
          return; // Already processed, skip
      }
      // Process event
      processOrder(event);
      processedEventRepository.save(new ProcessedEvent(event.eventId()));
  }
  ```
- [ ] Tạo Flyway migration cho idempotency table:
  ```sql
  CREATE TABLE processed_events (
      event_id VARCHAR(100) PRIMARY KEY,
      processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
  -- Auto-cleanup: delete events older than 7 days
  CREATE INDEX idx_processed_events_created ON processed_events (processed_at);
  ```
- [ ] **Kafka Transactions (optional):** Configure exactly-once cho producer:
  ```yaml
  spring:
    kafka:
      producer:
        transaction-id-prefix: order-service-tx-
        properties:
          transactional.id: order-service-tx-1
  ```
- [ ] **Senior Insight:** Idempotent consumer + at-least-once delivery = effectively exactly-once. Đơn giản và reliable hơn Kafka transactions trong hầu hết cases.

**Verify:**
```bash
# Publish same event twice
# Verify: processed only once, second one logged as duplicate
```

---

### 3.4 Message Ordering & Priority

**Service:** `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Order cancel đến TRƯỚC order place (do network retry) → stock released cho order chưa tồn tại → data corruption. Senior hiểu ordering guarantee của Kafka.

**Tasks:**
- [ ] Hiểu Kafka ordering guarantee:
  ```
  Guarantee: Messages trong CÙNG 1 PARTITION được process theo order.
  No guarantee: Messages KHÁC partition.
  
  Solution: Partition by orderId → tất cả events của 1 order → cùng partition → đúng order.
  ```
- [ ] Implement custom partitioner:
  ```java
  public class OrderIdPartitioner implements Partitioner {
      @Override
      public int partition(String topic, Object key, byte[] keyBytes,
                          Object value, byte[] valueBytes, Cluster cluster) {
          int numPartitions = cluster.partitionCountForTopic(topic);
          return Math.abs(key.hashCode()) % numPartitions;
      }
  }
  ```
- [ ] **Out-of-order handling:** Implement version/timestamp check trong consumer:
  ```java
  if (existingOrder.getVersion() >= event.getVersion()) {
      log.warn("Stale event received, ignoring: {}", event);
      return;
  }
  ```
- [ ] **Senior Insight:** Event ordering là hard problem. Nhất là khi có retry, DLQ, multiple consumers. Document ordering contract rõ ràng.

---

## 🏗️ MODULE 4 — Distributed Systems Patterns
> **Thời gian:** 3–4 tuần
> **Senior differentiator:** Implement patterns đúng context, không phải copy-paste tutorial.

---

### 4.1 Outbox Pattern

**Service:** `order-service`

**Tại sao Senior cần biết:**
Save DB thành công + publish Kafka fail = data inconsistency. Outbox giải quyết bằng cách ghi event vào cùng transaction với business data.

**Tasks:**
- [ ] Tạo Flyway migration:
  ```sql
  CREATE TABLE outbox_events (
      id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      aggregate_type VARCHAR(50) NOT NULL,
      aggregate_id   VARCHAR(100) NOT NULL,
      event_type     VARCHAR(100) NOT NULL,
      payload        JSONB NOT NULL,
      created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      processed_at   TIMESTAMPTZ,
      status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      retry_count    INTEGER NOT NULL DEFAULT 0
  );
  CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
  ```
- [ ] Trong cùng DB transaction: save Order + insert outbox_events
- [ ] Tạo `OutboxPoller` (Spring Scheduling): PENDING → publish Kafka → SENT
- [ ] Retry: FAILED sau 3 lần → DLQ + alert
- [ ] Test: Kill Kafka → đặt hàng → restart Kafka → event tự gửi lại
- [ ] **Senior Insight:** Outbox Poller có 2 variants:
  - **Polling-based** (đơn giản, đủ cho project này)
  - **CDC (Debezium)** (enterprise-grade, capture WAL stream) — **nên đọc hiểu** dù không implement

**Verify:**
```bash
docker stop kafka
# Place order → outbox status = PENDING
docker start kafka
# Event published → outbox status = SENT
```

---

### 4.2 Circuit Breaker + Retry + Bulkhead

**Service:** `order-service` (gọi `catalog-service`), `inventory-service`

**Tại sao Senior cần biết:**
Cascading failure — 1 service chậm kéo toàn bộ hệ thống chậm. Senior phòng thủ với **3 layers resilience**: Timeout → Retry → Circuit Breaker.

**Tasks:**
- [ ] Thêm `resilience4j-spring-boot3` dependency
- [ ] **Layer 1 — Timeout:** Request catalog không quá 2s:
  ```yaml
  resilience4j:
    timelimiter:
      instances:
        catalog-service:
          timeout-duration: 2s
  ```
- [ ] **Layer 2 — Retry** với exponential backoff:
  ```yaml
  resilience4j:
    retry:
      instances:
        catalog-service:
          max-attempts: 3
          wait-duration: 1s
          retry-exceptions:
            - java.io.IOException
            - java.util.concurrent.TimeoutException
          ignore-exceptions:
            - com.bookstore.exception.BookNotFoundException
  ```
- [ ] **Layer 3 — Circuit Breaker:**
  ```yaml
  resilience4j:
    circuitbreaker:
      instances:
        catalog-service:
          sliding-window-size: 10
          failure-rate-threshold: 50
          wait-duration-in-open-state: 30s
          permitted-calls-in-half-open-state: 3
  ```
- [ ] **Layer 4 — Bulkhead** (isolate resources):
  ```yaml
  resilience4j:
    bulkhead:
      instances:
        catalog-service:
          max-concurrent-calls: 10
          max-wait-duration: 1s
  ```
- [ ] Implement fallback method: cached data hoặc graceful degradation
- [ ] Expose qua `/actuator/circuitbreakers`, `/actuator/retries`
- [ ] **Senior Insight:** Thứ tự áp dụng: `Bulkhead → CircuitBreaker → Retry → Timeout`. Trong Resilience4j, decorator order matters.

**Verify:**
```bash
# Kill catalog-service → place 10 orders → check circuit state
curl http://localhost:9002/actuator/circuitbreakers
# Kỳ vọng: state = OPEN after threshold exceeded
```

---

### 4.3 Caching — Read-Through & Cache-Aside

**Service:** `catalog-service`, `order-service`

**Tại sao Senior cần biết:**
Cache không phải "thêm Redis là xong". Sai cache strategy = stale data, memory leak, cache stampede. Senior chọn đúng pattern cho đúng use case.

**Tasks:**
- [ ] **Read-Through Cache** trong `catalog-service`:
  ```java
  @Cacheable(value = "books", key = "#isbn", unless = "#result == null")
  public Book findByIsbn(String isbn) { ... }

  @CacheEvict(value = "books", key = "#book.isbn")
  public Book updateBook(Book book) { ... }
  ```
- [ ] Configure Redis TTL và serialization:
  ```yaml
  spring:
    data:
      redis:
        cache:
          time-to-live: 5m
          cache-null-values: false
          key-prefix: "bookstore:"
  ```
  - Serialization: Jackson JSON (KHÔNG dùng Java serialization — security risk)
- [ ] **Cache stampede prevention:** Implement cache lock hoặc probabilistic early expiration:
  ```java
  @Cacheable(value = "books", key = "#isbn",
      sync = true) // Spring 6+ built-in stampede prevention
  public Book findByIsbn(String isbn) { ... }
  ```
- [ ] **Cache-Aside** cho order history trong `order-service`:
  - Read: check Redis → miss → DB → save Redis
  - Write: invalidate cache ngay khi update order status
- [ ] Measure: cache hit < 5ms, cache miss < 50ms
- [ ] **Senior Insight:** 3 cache problems phải biết:
  1. **Cache Penetration** — query data không tồn tại → cache null (with short TTL)
  2. **Cache Breakdown** — hot key expired → stampede → `sync = true`
  3. **Cache Avalanche** — nhiều keys expire cùng lúc → randomize TTL

**Verify:**
```bash
time curl http://localhost:9001/books/isbn-123  # cache miss
time curl http://localhost:9001/books/isbn-123  # cache hit — faster
```

---

### 4.4 Saga Pattern — PlaceOrder Choreography

**Service:** `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Không có distributed transaction trong microservices. Saga = eventual consistency với compensation. Senior hiểu khi nào dùng **Choreography** vs **Orchestration**.

**Luồng Saga:**
```
PlaceOrder Saga (Choreography-based):
  Step 1: order-service     → Save Order (PENDING)      → publish OrderPlaced
  Step 2: inventory-service → Reserve Stock              → publish StockReserved | StockInsufficient
  Step 3: order-service     → [StockReserved]  → CONFIRMED
          order-service     → [StockInsufficient] → CANCELLED
  Step 4: inventory-service → [OrderCancelled] → Release Stock

Compensation:
  Nếu bất kỳ step fail → ngược lại từ step fail trở về trước
  StockReserved → OrderConfirmed → OK
  StockInsufficient → Order CANCELLED → Release Stock (if reserved)
```

**Tasks:**
- [ ] Map Saga flow trên giấy/diagram TRƯỚC khi code
- [ ] Implement compensation logic:
  - `order-service`: `StockReservationFailed` → Order = CANCELLED
  - `inventory-service`: `OrderCancelled` → release reserved stock
- [ ] **Idempotency** cho mỗi step — dùng eventId/orderId
- [ ] **Saga timeout:** Nếu 5 phút không nhận response → tự động cancel
  ```java
  @Scheduled(fixedRate = 60000)
  void checkStaleOrders() {
      List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(
          Status.PENDING, Instant.now().minus(5, ChronoUnit.MINUTES));
      stale.forEach(order -> cancelOrder(order, "Saga timeout"));
  }
  ```
- [ ] Test end-to-end: simulate `StockInsufficient` → verify Order = CANCELLED, stock unchanged
- [ ] **Senior Insight:** Choreography (event-based) vs Orchestration (central coordinator):
  - **Choreography:** Simple, loose coupling, hard to track. Good for 3-4 services.
  - **Orchestration:** Central saga orchestrator, easier tracking, more coupling. Good for complex sagas.
  -本项目 dùng **Choreography** — phù hợp vì chỉ có 2-3 services tham gia.

**Verify:**
- Place order với quantity > available stock
- Verify: Order status = CANCELLED, stock unchanged in DB

---

## ⚡ MODULE 5 — Reactive & Concurrency
> **Thời gian:** 2 tuần
> **Senior differentiator:** Hiểu KHI NÀO dùng reactive, khi KHÔNG. Biết backpressure, thread safety, virtual threads.

---

### 5.1 Reactive Deep Dive — Backpressure & Thread Model

**Service:** `order-service` (WebFlux)

**Tại sao Senior cần biết:**
Junior dùng reactive vì "cool". Senior dùng reactive vì có reason — high concurrency, non-blocking I/O — và hiểu backpressure để không OOM.

**Tasks:**
- [ ] Hiểu **Reactive Streams contract:** Publisher → Subscriber với **backpressure**
  ```
  Subscriber.request(10) → Publisher emits max 10 items
  Without backpressure: Publisher emits 1M items → Subscriber OOM
  ```
- [ ] Implement backpressure-aware consumer:
  ```java
  @Bean
  public Consumer<Flux<Message<OrderPlacedEvent>>> handleOrderPlaced() {
      return flux -> flux
          .flatMap(message -> processOrder(message.getPayload()), 4) // concurrency = 4
          .onErrorResume(e -> {
              log.error("Error processing order", e);
              return Mono.empty();
          });
  }
  ```
- [ ] Hiểu **Event Loop model** của Netty:
  - Default: 2 × CPU cores event loops
  - **KHÔNG BAO GIỜ** block event loop — no `Thread.sleep`, no blocking DB call
  - Detect blocking calls: `blockhound` dependency:
    ```java
    // Test configuration
    @Test
    void shouldNotBlockEventLoop() {
        BlockHound.install();
        // ... test reactive pipeline
    }
    ```
- [ ] **Thread safety:** Reactor objects (`Mono`, `Flux`) are immutable and thread-safe by design
- [ ] **Senior Insight:** Reactive ≠ faster cho single request. Reactive = handle **more concurrent connections** với **fewer threads**. Trade-off: harder debugging, harder stack traces.

**Verify:**
```bash
# Load test with 1000 concurrent connections
# Reactive (order-service) should handle without thread exhaustion
# Blocking (catalog-service MVC) would need thread pool tuning
```

---

### 5.2 Virtual Threads (Java 21) vs Reactive

**Service:** `catalog-service`, `order-service`

**Tại sao Senior cần biết:**
Java 21 virtual threads = simpler concurrency model. Senior biết khi nào virtual threads **thay thế** reactive, khi nào **complement**.

**Tasks:**
- [ ] Enable virtual threads trong `catalog-service` (Spring MVC):
  ```yaml
  spring:
    threads:
      virtual:
        enabled: true
  ```
  - Spring Boot 3.2+ tự động dùng virtual threads cho request handling
- [ ] **Compare:** Load test same endpoint:
  - Without virtual threads (platform threads + Tomcat pool)
  - With virtual threads
  - Reactive (WebFlux)
- [ ] Document findings:
  ```
  | Approach         | Threads needed | Memory per conn | Complexity | Use case |
  |------------------|---------------|-----------------|------------|----------|
  | Platform threads  | 200 (Tomcat)  | ~1MB each       | Low        | Traditional |
  | Virtual threads   | Millions      | ~1KB each       | Low        | I/O heavy |
  | Reactive (WebFlux)| Few (Netty)   | Minimal         | High       | Streaming, backpressure |
  ```
- [ ] **Senior Insight:** Virtual threads không thay thế reactive khi cần:
  - Streaming responses (Server-Sent Events)
  - Complex backpressure scenarios
  - Multi-source data composition (Flux.zip, merge)

**Verify:**
```bash
# Enable virtual threads → load test → compare throughput
ab -n 10000 -c 200 http://localhost:9001/books
```

---

## 🔐 MODULE 6 — Production Security
> **Thời gian:** 2–3 tuần
> **Senior differentiator:** Security không chỉ là login/logout. Senior hiểu OWASP Top 10, defense in depth, security headers.

---

### 6.1 Security Headers & OWASP Top 10

**Service:** Tất cả services

**Tại sao Senior cần biết:**
XSS, CSRF, clickjacking — tấn công phổ biến nhưng preventable. Senior không implement business logic trước khi secure HTTP layer.

**Tasks:**
- [ ] Implement security headers (Spring Security):
  ```java
  .headers(headers -> headers
      .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)  // X-Frame-Options
      .contentSecurityPolicy(csp -> csp.policyDirectives(
          "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"))
      .referrerPolicy(ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN))
      .permissionsPolicy(perm -> perm.policy("camera=(), microphone=(), geolocation=()"))
  )
  ```
- [ ] **OWASP Top 10 mapping** to this project:
  | OWASP Risk | Mitigation in this project |
  |------------|---------------------------|
  | A01: Broken Access Control | `@PreAuthorize` method security, role-based |
  | A02: Cryptographic Failures | TLS everywhere, no sensitive data in logs |
  | A03: Injection | Parameterized queries (Spring Data JDBC/R2DBC) |
  | A05: Security Misconfiguration | No stack traces in prod, no default passwords |
  | A07: Auth Failures | Keycloak OIDC, token expiration, rate limiting |
- [ ] **Input sanitization:** Prevent XSS in book title, author, etc.
- [ ] **SQL Injection prevention:** Verify all queries use parameterized bindings (Spring Data does this by default, but verify custom queries)

---

### 6.2 Downstream JWT Validation

**Service:** `catalog-service`, `order-service`

**Tại sao Senior cần biết:**
Edge-service validates token, nhưng downstream services **phải tự validate**. Trust boundary = mỗi service, không chỉ gateway.

**Tasks:**
- [ ] Add `spring-boot-starter-oauth2-resource-server` to downstream services
- [ ] Configure JWT validation:
  ```yaml
  spring:
    security:
      oauth2:
        resourceserver:
          jwt:
            issuer-uri: ${KEYCLOAK_URL}/realms/PolarBookshop
  ```
- [ ] Extract user context from JWT in downstream:
  ```java
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
      var converter = new JwtAuthenticationConverter();
      converter.setJwtGrantedAuthoritiesConverter(jwt -> {
          List<String> roles = jwt.getClaimAsStringList("roles");
          return roles.stream()
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
              .collect(Collectors.toList());
      });
      return converter;
  }
  ```
- [ ] **Method-level security:**
  ```java
  @PreAuthorize("hasRole('employee')")
  public Book createBook(BookRequest request) { ... }

  @PreAuthorize("hasAnyRole('customer', 'employee')")
  public Order placeOrder(OrderRequest request) { ... }
  ```
- [ ] **Senior Insight:** Defense in depth — nếu attacker bypass edge-service, downstream vẫn reject unauthenticated requests.

**Verify:**
```bash
# Call catalog-service directly without JWT
curl http://localhost:9001/books
# Kỳ vọng: 401 Unauthorized
```

---

### 6.3 Rate Limiting & Throttling

**Service:** `edge-service`

**Tại sao Senior cần biết:**
DDoS hoặc bot traffic → backend overwhelmed. Rate limiting tại gateway = first line of defense.

**Tasks:**
- [ ] Configure Redis-based rate limiting (already partially implemented):
  ```yaml
  # config/edge-service.yml
  spring:
    cloud:
      gateway:
        redis-rate-limiter:
          replenish-rate: 10      # 10 requests/second
          burst-capacity: 20      # allow burst of 20
          requested-tokens: 1
  ```
- [ ] Custom `KeyResolver` — rate limit per user, not per IP:
  ```java
  @Bean
  KeyResolver userKeyResolver() {
      return exchange -> ReactiveSecurityContextHolder.getContext()
          .map(ctx -> ctx.getAuthentication().getName())
          .defaultIfEmpty("anonymous");
  }
  ```
- [ ] **Sliding window vs Token bucket:** Hiểu cả 2 algorithms:
  - Token bucket (Redis) → burst-friendly, good for APIs
  - Sliding window → smoother rate, good for billing
- [ ] Test: 25 requests liên tiếp → verify request 21-25 receive 429
- [ ] **Senior Insight:** Rate limiting + Circuit Breaker = **different layers of protection**. Rate limit = prevent abuse. Circuit Breaker = prevent cascading failure.

---

## 📡 MODULE 7 — Observability & Production Readiness
> **Thời gian:** 2 tuần
> **Senior differentiator:** "If you can't measure it, you can't improve it." Senior thiết kế observability TRƯỚC khi cần debug.

---

### 7.1 Distributed Tracing (OpenTelemetry)

**Service:** Tất cả services

**Tasks:**
- [ ] Thêm `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin`
- [ ] Cấu hình trace propagation qua Kafka headers + HTTP headers
- [ ] Verify trace xuyên suốt:
  ```
  HTTP Request → edge-service → order-service → [Kafka] → inventory-service
       ↑______________________traceId xuyên suốt________________________↑
  ```
- [ ] Custom `Span` cho business operations:
  ```java
  Span span = tracer.nextSpan().name("reserve-stock").start();
  try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
      stockService.reserve(orderId, items);
  } finally {
      span.end();
  }
  ```
- [ ] Sampling: 100% dev, 10% production
- [ ] **Senior Insight:** Tracing overhead = ~1-2% latency. Trade-off: full visibility vs performance. **Head-based sampling** vs **tail-based sampling** (collect all, decide later).

**Verify:**
- Place order → open Zipkin → see single trace across all services

---

### 7.2 Custom Business Metrics

**Service:** `order-service`, `inventory-service`

**Tasks:**
- [ ] Implement 4 types of Micrometer metrics:
  ```java
  // Counter: orders placed
  Counter ordersPlaced = Counter.builder("bookstore.orders.placed")
      .tag("status", "success")
      .register(meterRegistry);

  // Gauge: current stock level
  Gauge.builder("bookstore.stock.available", stockService, StockService::getTotalAvailableStock)
      .register(meterRegistry);

  // Timer: reservation duration
  Timer reservationTimer = Timer.builder("bookstore.stock.reservation.duration")
      .register(meterRegistry);

  // Distribution Summary: order value
  DistributionSummary orderValue = DistributionSummary.builder("bookstore.orders.value")
      .register(meterRegistry);
  ```
- [ ] Configure Prometheus endpoint (`/actuator/prometheus`)
- [ ] Grafana dashboard: order rate, error rate, stock levels, p99 latency
- [ ] Alerts: error rate > 5% in 5 minutes → alert
- [ ] **Senior Insight:** 4 **golden signals** of observability:
  1. **Latency** — Timer
  2. **Traffic** — Counter
  3. **Errors** — Counter with error tag
  4. **Saturation** — Gauge (thread pool, connection pool)

**Verify:**
```bash
curl http://localhost:9002/actuator/prometheus | grep bookstore
# Kỳ vọng: all custom metrics with labels
```

---

### 7.3 Health Checks, Probes & Graceful Shutdown

**Service:** Tất cả services

**Tại sao Senior cần biết:**
K8s kill pod bất ngờ → nếu shutdown không graceful → mất in-flight requests. Senior configure lifecycle properly.

**Tasks:**
- [ ] Configure **Liveness, Readiness, Startup probes:**
  ```yaml
  # config/catalog-service.yml
  management:
    endpoint:
      health:
        probes:
          enabled: true
        show-details: always
    health:
      livenessstate:
        enabled: true
      readinessstate:
        enabled: true
  ```
- [ ] **Custom health indicators:**
  ```java
  @Component
  public class KafkaHealthIndicator implements HealthIndicator {
      @Override
      public Health health() {
          // Check Kafka connectivity
          if (kafkaConnected) {
              return Health.up().withDetail("topics", activeTopics).build();
          }
          return Health.down().withDetail("error", "Kafka not connected").build();
      }
  }
  ```
- [ ] **Graceful shutdown:**
  ```yaml
  server:
    shutdown: graceful
  spring:
    lifecycle:
      timeout-per-shutdown-phase: 30s
  ```
- [ ] K8s deployment config:
  ```yaml
  spec:
    containers:
      - name: catalog-service
        livenessProbe:
          httpGet: { path: /actuator/health/liveness, port: 9001 }
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet: { path: /actuator/health/readiness, port: 9001 }
          initialDelaySeconds: 10
          periodSeconds: 5
    terminationGracePeriodSeconds: 60
  ```
- [ ] **Senior Insight:** Readiness ≠ Liveness:
  - **Readiness fail** → pod removed from Service, but NOT restarted. "I'm alive but can't handle requests yet."
  - **Liveness fail** → pod IS restarted. "I'm stuck, kill me."
  - Mixing them up = pod restart loop.

**Verify:**
```bash
curl http://localhost:9001/actuator/health/readiness
# Kỳ vọng: {"status":"UP"}
# Simulate DB down → readiness = DOWN, pod removed from service
```

---

## 🔥 MODULE 8 — Performance Engineering
> **Thời gian:** 2 tuần
> **Senior differentiator:** Senior không đoán performance — đo và profile. Biết bottleneck ở đâu trước khi tối ưu.

---

### 8.1 JVM Tuning & GC Selection

**Service:** Tất cả services

**Tại sao Senior cần biết:**
OutOfMemoryError trong production = P1 incident. Senior chọn GC strategy và memory settings phù hợp workload.

**Tasks:**
- [ ] **GC Selection cho Java 21:**
  ```
  | GC              | Use case                | Latency     | Throughput |
  |-----------------|------------------------|-------------|------------|
  | G1GC (default)  | General purpose         | Good        | Good       |
  | ZGC             | Low-latency required    | Excellent   | Good       |
  | Serial GC       | Small heap, native img  | N/A         | N/A        |
  ```
- [ ] For native images (Module 9): Serial GC hoặc không GC (small heap)
- [ ] Configure JVM cho mỗi service:
  ```yaml
  # docker-compose.yml hoặc K8s
  JAVA_OPTS: >
    -XX:+UseZGC
    -Xms256m -Xmx512m
    -XX:MaxMetaspaceSize=128m
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/tmp/heapdump.hprof
  ```
- [ ] **Monitor GC** qua Micrometer:
  ```bash
  curl http://localhost:9001/actuator/metrics/jvm.gc.pause
  # P99 GC pause > 100ms → wrong GC or memory config
  ```
- [ ] **Senior Insight:** GC tuning chỉ cần khi:
  - Heap > 4GB
  - Latency requirements < 50ms P99
  - Object allocation rate very high
  Otherwise, default G1GC is fine.

---

### 8.2 Load Testing & Performance Baseline

**Service:** Tất cả services

**Tại sao Senior cần biết:**
"Performance problem" không đoán được. Load test = thiết lập **baseline** để detect regression.

**Tasks:**
- [ ] Chọn load testing tool: **k6** (JavaScript-based, easy):
  ```javascript
  // k6-scripts/catalog-load.js
  import http from 'k6/http';

  export let options = {
      stages: [
          { duration: '30s', target: 20 },   // ramp up to 20 users
          { duration: '1m', target: 20 },     // sustain 20 users
          { duration: '30s', target: 100 },   // ramp up to 100 users
          { duration: '1m', target: 100 },    // sustain 100 users
          { duration: '30s', target: 0 },     // ramp down
      ],
      thresholds: {
          http_req_duration: ['p(99)<500'],   // 99% requests < 500ms
          http_req_failed: ['rate<0.01'],     // error rate < 1%
      },
  };

  export default function () {
      http.get('http://localhost:9001/books');
  }
  ```
- [ ] Establish **performance baseline:**
  ```
  | Endpoint              | P50   | P95   | P99   | Max RPS |
  |-----------------------|-------|-------|-------|---------|
  | GET /books            | 5ms   | 15ms  | 50ms  | 500     |
  | GET /books/{isbn}     | 3ms   | 10ms  | 30ms  | 800     |
  | POST /orders          | 50ms  | 150ms | 400ms | 200     |
  | GET /orders (cached)  | 2ms   | 5ms   | 10ms  | 2000    |
  ```
- [ ] Identify bottleneck under load: DB? CPU? Memory? Network?
- [ ] **Senior Insight:** Load test trước và sau mỗi optimization. Không tối ưu không đo được. Document baseline trong repo.

**Verify:**
```bash
k6 run k6-scripts/catalog-load.js
# Kỳ vọng: pass all thresholds
```

---

### 8.3 GraalVM Native Image

**Service:** `catalog-service`, `order-service`, `config-service`

**Tại sao Senior cần biết:**
Project tên "spring-native-bookstore" — native image là core feature. Startup < 100ms, memory < 50MB, nhưng có **constraints** phải hiểu.

**Tasks:**
- [ ] Build native image cho mỗi service:
  ```bash
  cd catalog-service && ./gradlew bootBuildImage
  # Hoặc native compile:
  ./gradlew nativeCompile
  ```
- [ ] **AOT Processing — hiểu restriction:**
  ```
  Native Image Limitations:
  - No runtime bytecode generation (no CGLIB proxy at runtime)
  - No reflection unless registered
  - No dynamic classloading
  - All Spring beans must be known at build time
  
  Spring Boot 3+ AOT engine handles most of this automatically.
  But custom reflection/proxy must be registered via RuntimeHints.
  ```
- [ ] **RuntimeHints** cho custom reflection:
  ```java
  @Configuration
  public class NativeHints implements RuntimeHintsRegistrar {
      @Override
      public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
          hints.reflection()
              .registerType(MyCustomClass.class, MemberCategory.INVOKE_PUBLIC_METHODS);
      }
  }
  ```
- [ ] **Measure native vs JVM:**
  ```
  | Metric          | JVM      | Native Image |
  |-----------------|----------|-------------|
  | Startup time    | ~3s      | <100ms      |
  | Memory usage    | ~300MB   | ~50MB       |
  | Peak throughput | Higher   | Slightly lower |
  | Warm-up time    | Minutes  | None        |
  ```
- [ ] **Test native image:** Run tests against native binary:
  ```bash
  ./gradlew nativeTest
  ```
- [ ] **Senior Insight:** Native image trade-offs:
  - **Good for:** Cloud, K8s, serverless (fast scale-up, low memory)
  - **Bad for:** Long-running services cần peak throughput (JIT warm-up > AOT)
  - **Decision framework:** Need fast startup/low memory? → Native. Need max throughput? → JVM.

**Verify:**
```bash
cd catalog-service && ./gradlew bootBuildImage
docker run --rm -p 9001:9001 catalog-service:latest
# Kỳ vọng: startup < 100ms, memory < 100MB
```

---

## 🧩 MODULE 9 — Architecture Patterns
> **Thời gian:** 2–3 tuần
> **Senior differentiator:** Biết khi nào dùng pattern, khi nào KHÔNG. Overengineering = junior trait.

---

### 9.1 CQRS (Command Query Responsibility Segregation)

**Service:** `order-service`

**Tại sao Senior cần biết:**
Read model và write model có **different optimization goals**. CQRS tách riêng để tối ưu từng bên.

**Tasks:**
- [ ] Hiểu CQRS trong context của project:
  ```
  Current: Same model for reads and writes
  ├── OrderController.createOrder(OrderRequest) → writes to orders table
  └── OrderController.getOrders()               → reads from orders table

  CQRS: Separate models
  ├── Command side: createOrder, cancelOrder, acceptOrder
  │   └── Optimized for writes: normalized tables, strict validation
  └── Query side: getOrder, getOrders, getOrderHistory
      └── Optimized for reads: denormalized view, pagination, filtering
  ```
- [ ] Implement **query model** — denormalized view:
  ```sql
  CREATE TABLE order_summary_view (
      order_id VARCHAR(100) PRIMARY KEY,
      book_isbn VARCHAR(20),
      book_title VARCHAR(255),
      quantity INTEGER,
      status VARCHAR(20),
      created_at TIMESTAMPTZ,
      updated_at TIMESTAMPTZ
  );
  ```
- [ ] **Event-driven sync:** Command side publishes event → query side updates view
- [ ] **Eventually consistent:** Query model might be slightly stale — acceptable for read side
- [ ] **Senior Insight:** CQRS overhead = justified khi:
  - Read/write ratio > 10:1
  - Read queries complex (joins, aggregations)
  - Different scaling needs for read vs write
  - **NOT justified** for simple CRUD. Don't over-engineer.

---

### 9.2 Event Sourcing (Conceptual + Partial Implementation)

**Service:** `order-service`

**Tại sao Senior cần biết:**
Audit trail, time-travel debugging, replay events. Senior hiểu concept dù không phải default choice.

**Tasks:**
- [ ] Hiểu Event Sourcing vs CRUD:
  ```
  CRUD: Store current state
  ├── orders table: { id: 1, status: CONFIRMED }
  └── History lost: how did it get to CONFIRMED?

  Event Sourcing: Store sequence of events
  ├── events table:
  │   ├── OrderPlaced { orderId: 1, items: [...] }
  │   ├── StockReserved { orderId: 1, stockId: X }
  │   └── OrderConfirmed { orderId: 1 }
  └── Current state = replay all events → CONFIRMED
  ```
- [ ] Implement **partial event sourcing** — event log table alongside current state:
  ```sql
  CREATE TABLE domain_events (
      id            BIGSERIAL PRIMARY KEY,
      aggregate_id  VARCHAR(100) NOT NULL,
      aggregate_type VARCHAR(50) NOT NULL,
      event_type    VARCHAR(100) NOT NULL,
      payload       JSONB NOT NULL,
      metadata      JSONB,
      version       INTEGER NOT NULL,
      created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_events_aggregate ON domain_events (aggregate_type, aggregate_id, version);
  ```
- [ ] **Snapshot optimization:** Không replay 1000 events — tạo periodic snapshot
- [ ] **Senior Insight:** Event Sourcing trade-offs:
  - **Pros:** Perfect audit trail, time-travel, event replay, natural fit for messaging
  - **Cons:** Complexity, event schema evolution, eventual consistency, storage growth
  - **Rule:** Don't event source everything. Event source **aggregates with complex state transitions** (Order is a good candidate).

---

### 9.3 Hexagonal Architecture Compliance

**Service:** `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Architecture rot — code "works" nhưng coupled. Senior enforce architecture boundaries.

**Tasks:**
- [ ] Audit current package structure:
  ```
  order-service/src/main/java/
  └── com/bookstore/order/
      ├── domain/         ← Business logic, NO framework dependencies
      ├── application/    ← Use cases, ports (interfaces)
      ├── adapter/
      │   ├── in/web/     ← Controllers (driving adapter)
      │   ├── in/messaging/ ← Kafka consumers (driving adapter)
      │   └── out/persistence/ ← Repository implementations (driven adapter)
      └── bootstrap/      ← Spring config, wiring
  ```
- [ ] **ArchUnit test** to enforce boundaries:
  ```java
  @AnalyzeClasses(packages = "com.bookstore.order")
  public class HexagonalArchitectureTest {
      @ArchTest
      static final ArchRule domain_should_not_depend_on_framework =
          noClasses()
              .that().resideInAPackage("..domain..")
              .should().dependOnClassesThat()
              .resideInAPackage("org.springframework..");

      @ArchTest
      static final ArchRule domain_should_not_depend_on_adapter =
          noClasses()
              .that().resideInAPackage("..domain..")
              .should().dependOnClassesThat()
              .resideInAPackage("..adapter..");
  }
  ```
- [ ] **Senior Insight:** Architecture tests prevent "quick fixes" that violate boundaries. CI fails = no merge.

---

## ☁️ MODULE 10 — DevOps & Production Deployment
> **Thời gian:** 3–4 tuần
> **Senior differentiator:** Deploy được lên production thật, với quy trình chuyên nghiệp.

---

### 10.1 Helm Charts

**Tasks:**
- [ ] Tạo Helm chart cho mỗi service:
  ```
  helm/
  ├── bookstore/
  │   ├── Chart.yaml
  │   ├── values.yaml
  │   ├── values-staging.yaml
  │   ├── values-prod.yaml
  │   └── templates/
  │       ├── deployment.yaml
  │       ├── service.yaml
  │       ├── configmap.yaml
  │       ├── secret.yaml
  │       ├── hpa.yaml
  │       └── poddisruptionbudget.yaml
  ```
- [ ] Parameterize: image tag, replica count, resource limits, probes
- [ ] `helm lint` + `helm template` pass clean
- [ ] Deploy lên local Minikube

**Verify:**
```bash
helm install bookstore-dev ./helm/bookstore -f values-staging.yaml
kubectl get pods -n bookstore
# Kỳ vọng: all pods Running
```

---

### 10.2 Contract Testing với Pact

**Tasks:**
- [ ] **Consumer side** (`order-service`): Pact test defining expectations:
  ```java
  @Pact(consumer = "order-service", provider = "catalog-service")
  RequestResponsePact getBookByIsbn(PactDslWithProvider builder) {
      return builder
          .given("book with isbn exists")
          .uponReceiving("get book by isbn")
          .path("/books/978-3-16-148410-0")
          .method("GET")
          .willRespondWith()
          .status(200)
          .body(new PactDslJsonBody()
              .stringType("isbn", "978-3-16-148410-0")
              .stringType("title", "Test Book")
              .numberType("price", 29.99))
          .toPact();
  }
  ```
- [ ] **Provider side** (`catalog-service`): Verification test
- [ ] Publish contracts lên Pact Broker (Docker self-hosted)
- [ ] Integrate vào CI pipeline
- [ ] **Senior Insight:** Contract test ≠ Integration test:
  - Contract test: verifies API shape (fast, no infrastructure)
  - Integration test: verifies behavior end-to-end (slow, needs infrastructure)
  - Both are needed. Contract test catches **breaking changes** before deploy.

---

### 10.3 SonarQube Quality Gate

**Tasks:**
- [ ] SonarQube server local via Docker
- [ ] Gradle plugin `org.sonarqube` cho mỗi service
- [ ] Quality Gate thresholds:
  | Metric | Threshold |
  |--------|-----------|
  | Coverage | >= 80% |
  | Duplicated Lines | < 3% |
  | Maintainability Rating | A |
  | Reliability Rating | A |
  | Security Rating | A |
- [ ] Integrate vào GitHub Actions CI

---

### 10.4 AWS Production Deployment

**Architecture:**
```
Internet → Route53 → ALB → EKS Cluster
                            ├── edge-service pods
                            ├── catalog-service pods
                            ├── order-service pods
                            ├── inventory-service pods
                            └── dispatcher-service pods
                     RDS PostgreSQL (Multi-AZ)
                     MSK (Managed Kafka)
                     ElastiCache Redis
                     ECR (Container Registry)
```

**Tasks:**
- [ ] ECR: Push images → `./gradlew bootBuildImage`
- [ ] RDS: PostgreSQL Multi-AZ (prod), Single-AZ (staging)
- [ ] MSK: Managed Kafka cluster
- [ ] ElastiCache: Redis cluster
- [ ] EKS: `eksctl create cluster` + ArgoCD + Knative
- [ ] Secrets Manager → External Secrets Operator
- [ ] Route53 + ACM + ALB Ingress Controller
- [ ] kube-prometheus-stack (Grafana + Prometheus + AlertManager)

---

## 📈 Tracker Tiến Độ

| Module | Topic | Status |
|--------|-------|--------|
| 1 | Global Exception Handling (RFC 7807) | ⬜ |
| 1 | Validation — Multi-Layer Strategy | ⬜ |
| 1 | Structured Logging & Correlation | ⬜ |
| 1 | API Versioning & Documentation | ⬜ |
| 2 | Connection Pool Tuning (HikariCP) | ⬜ |
| 2 | Indexing Strategy & Query Optimization | ⬜ |
| 2 | Zero-Downtime DB Migrations | ⬜ |
| 2 | DB Connection Failover | ⬜ |
| 3 | Kafka Internals (Partitions, Consumer Groups) | ⬜ |
| 3 | Dead Letter Queue (DLQ) | ⬜ |
| 3 | Exactly-Once & Idempotent Consumer | ⬜ |
| 3 | Message Ordering & Priority | ⬜ |
| 4 | Outbox Pattern | ⬜ |
| 4 | Circuit Breaker + Retry + Bulkhead | ⬜ |
| 4 | Caching (Read-Through & Cache-Aside) | ⬜ |
| 4 | Saga Pattern (Choreography) | ⬜ |
| 5 | Reactive Deep Dive (Backpressure) | ⬜ |
| 5 | Virtual Threads vs Reactive | ⬜ |
| 6 | Security Headers & OWASP Top 10 | ⬜ |
| 6 | Downstream JWT Validation | ⬜ |
| 6 | Rate Limiting & Throttling | ⬜ |
| 7 | Distributed Tracing (OpenTelemetry) | ⬜ |
| 7 | Custom Business Metrics | ⬜ |
| 7 | Health Checks, Probes & Graceful Shutdown | ⬜ |
| 8 | JVM Tuning & GC Selection | ⬜ |
| 8 | Load Testing & Performance Baseline | ⬜ |
| 8 | GraalVM Native Image | ⬜ |
| 9 | CQRS | ⬜ |
| 9 | Event Sourcing | ⬜ |
| 9 | Hexagonal Architecture (ArchUnit) | ⬜ |
| 10 | Helm Charts | ⬜ |
| 10 | Contract Testing (Pact) | ⬜ |
| 10 | SonarQube | ⬜ |
| 10 | Deploy AWS Production | ⬜ |

---

## 💡 Thứ Tự Thực Hiện Khuyến Nghị

```
Module 1 (Foundation)
  ↓
Module 7 (Observability) ← EARLY để có tools debug
  ↓
Module 2 (Database Mastery)
  ↓
Module 3 (Kafka Deep Dive)
  ↓
Module 5 (Reactive & Concurrency)
  ↓
Module 4 (Distributed Patterns)
  ↓
Module 8 (Performance Engineering + Native Image)
  ↓
Module 6 (Security)
  ↓
Module 9 (Architecture Patterns)
  ↓
Module 10 (DevOps & AWS)
```

**Lý do thứ tự:**
1. **Module 1** — Foundation, dễ nhưng tạo thói quen đúng
2. **Module 7** — Observability EARLY vì khi debug Modules sau cần tracing + metrics
3. **Module 2** — DB là bottleneck #1, tối ưu trước khi thêm patterns
4. **Module 3** — Kafka deep dive trước Saga/Outbox vì cần hiểu Kafka trước
5. **Module 5** — Reactive/Virtual threads — hiểu concurrency model
6. **Module 4** — Distributed patterns cần foundation từ Modules 2, 3, 5
7. **Module 8** — Performance + Native Image — đo trước/khi tối ưu
8. **Module 6** — Security — cần hệ thống ổn định trước khi harden
9. **Module 9** — Architecture patterns — refactor sau khi có đủ context
10. **Module 10** — DevOps/AWS — deploy last vì cần đủ features

---

## 🔥 Senior Checklist — "Am I Ready?"

Sau khi hoàn thành tất cả modules, trả lời được các câu hỏi sau:

- [ ] **Explain trade-off** của mỗi pattern đã implement — khi nào KHÔNG dùng?
- [ ] **Draw system architecture** từ memory — tất cả services, databases, message flows
- [ ] **Debug production issue:** "Order placed but stock not reserved" — trace qua logs, traces, metrics
- [ ] **Capacity planning:** "How many orders/second can this system handle?" — có baseline numbers
- [ ] **Cost optimization:** "Which service costs most to run? How to reduce?"
- [ ] **Security audit:** "What happens if JWT is stolen? If Kafka is exposed?"
- [ ] **Incident response:** "DB connection pool exhausted at 2AM — what do you do?"
- [ ] **Explain to junior:** Can you teach each module to someone with 1 year experience?

> **Nếu trả lời được tất cả — bạn ready cho Senior.**
