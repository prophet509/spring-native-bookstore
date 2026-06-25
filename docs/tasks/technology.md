# 🔬 Technology Deep-Dive Plan — spring-native-bookstore

> Mỗi mục dưới đây là một **task cụ thể** cần implement trong project này.
> Format: **[Service cần làm]** → **[Công việc]** → **[Cách verify đã làm đúng]**
> Cập nhật `⬜ → 🔄 → ✅` khi tiến hành.

---

## 📦 MODULE 1 — Foundation Professionalism
> **Mục tiêu:** Những thứ "dễ" nhưng thể hiện tư duy engineer chuyên nghiệp ngay từ đầu.
> **Thời gian:** 1–2 tuần

---

### 1.1 Global Exception Handling

**Service:** `catalog-service`, `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
API trả về lỗi `500 Internal Server Error` là dấu hiệu của junior. Senior đảm bảo mọi lỗi đều có cấu trúc nhất quán, dễ debug từ phía client.

**Tasks:**
- [ ] Tạo `GlobalExceptionHandler` với `@RestControllerAdvice` trong mỗi service
- [ ] Định nghĩa class `ApiError` chuẩn RFC 7807 (Problem Details):
  ```json
  {
    "type": "https://bookstore.api/errors/bookOld-not-found",
    "title": "Book Not Found",
    "status": 404,
    "detail": "Book with ISBN 978-3-16-148410-0 does not exist",
    "instance": "/books/978-3-16-148410-0",
    "timestamp": "2026-04-29T05:00:00Z",
    "traceId": "abc123"
  }
  ```
- [ ] Map các exception cụ thể của domain:
  - `BookNotFoundException` → 404
  - `InsufficientStockException` → 422 Unprocessable Entity
  - `OrderAlreadyProcessedException` → 409 Conflict
  - `ConstraintViolationException` → 400 Bad Request
- [ ] Xử lý riêng cho Reactive (WebFlux) trong `order-service` dùng `@ControllerAdvice` + `WebExceptionHandler`
- [ ] Đảm bảo stack trace **không bao giờ** lộ ra response body trên production profile

**Verify:**
```bash
# Gọi API với ISBN không tồn tại
curl -v http://localhost:9001/books/isbn-fake
# Kỳ vọng: 404 với body ApiError chuẩn, không phải Whitelabel Error Page
```

---

### 1.2 Validation

**Service:** `catalog-service`, `order-service`, `inventory-service`

**Tại sao Senior cần biết:**
Validate đúng tầng, đúng loại. Business validation khác constraint validation. Senior không validate random chỗ nào.

**Tasks:**
- [ ] **Bean Validation (Jakarta):** Annotate các request DTOs:
  - `@NotBlank`, `@NotNull` cho required fields
  - `@ISBN` cho ISBN fields
  - `@Positive` cho price, quantity
  - `@Size(max = 255)` cho các text fields
- [ ] Enable validation trong controllers với `@Valid` / `@Validated`
- [ ] Tạo custom validator `@ValidIsbn` để validate ISBN-13 checksum bằng tay (logic tự viết, không dùng thư viện)
- [ ] Phân biệt rõ: **Constraint Violation** (format sai → 400) vs **Business Rule Violation** (stock không đủ → 422)
- [ ] Test: Viết test cho `GlobalExceptionHandler` để đảm bảo response đúng cấu trúc khi validation fail

**Verify:**
```bash
# Gửi request thiếu field bắt buộc
curl -X POST http://localhost:9001/books -d '{"title":""}' -H "Content-Type: application/json"
# Kỳ vọng: 400 với danh sách field errors rõ ràng
```

---

### 1.3 Structured Logging

**Service:** Tất cả services

**Tại sao Senior cần biết:**
Khi có incident lúc 3 giờ sáng, log dễ tìm kiếm trên ELK/CloudWatch là sự khác biệt giữa fix nhanh và fix cả đêm.

**Tasks:**
- [ ] Cấu hình Logback với `logstash-logback-encoder` để output JSON:
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
- [ ] Thêm MDC (Mapped Diagnostic Context) filter để tự động inject `traceId`, `userId` vào mọi log:
  ```java
  // MDCFilter.java — chạy mọi request
  MDC.put("traceId", extractTraceId(request));
  MDC.put("userId", extractUserId(request));
  ```
- [ ] Tạo log profile: `local` dùng human-readable, `prod` dùng JSON
- [ ] Đảm bảo **không bao giờ** log: password, token JWT, thông tin thẻ tín dụng (data masking)
- [ ] Thêm log ở các business milestone quan trọng: order placed, stock reserved, order dispatched

**Verify:**
```bash
# Chạy service và kiểm tra log format
cd order-service && ./gradlew bootRun --args='--spring.profiles.active=prod'
# Kỳ vọng: mỗi dòng log là một JSON object hợp lệ
```

---

## 🏗️ MODULE 2 — Distributed Systems Thinking
> **Mục tiêu:** Chứng tỏ tư duy hệ thống phân tán, xử lý failure gracefully.
> **Thời gian:** 3–4 tuần

---

### 2.1 Outbox Pattern ✅

**Service:** `order-service`, `inventory-service`, `catalog-service`

**Tại sao Senior cần biết:**
Vấn đề kinh điển: save DB thành công, publish Kafka fail → dữ liệu không nhất quán. Outbox giải quyết điều này.

**Đã hoàn thành:** Dùng **Debezium CDC** (không phải polling-based). Xem `docs/tasks/saga-outbox-plan.md` cho chi tiết đầy đủ.

**Kiến trúc đã implement:**
- Mỗi service có `outbox_event` table (Flyway `V6__create_outbox_event.sql`)
- Publisher adapter (`OutboxXxxEventPublisher`) insert row vào cùng transaction với aggregate save
- Debezium PostgreSQL connector tail WAL → Outbox Event Router SMT → publish to Kafka topic theo `destination` column
- `trace_id` column lưu W3C traceparent, Debezium map thành Kafka header
- Outbox retention qua `make outbox-cleanup` (SQL-based, không dùng `pg_cron`)

**Không dùng polling-based:** Không có `OutboxPoller`, không có `status`/`attempts`/`processed_at` columns. Debezium đọc trực tiếp từ WAL → at-least-once delivery.

**Verify:**
```bash
# Outbox đã được stress-tested: 1000 req / 50 concurrency = 100% success
# Xem saga-outbox-plan.md §3.2 cho kết quả chi tiết
```

---

### 2.2 Circuit Breaker

**Service:** `order-service` (khi gọi `catalog-service`), `inventory-service` (khi gọi external)

**Tại sao Senior cần biết:**
Một service chậm có thể làm toàn bộ hệ thống chậm theo (cascading failure). Circuit Breaker ngăn điều này.

**Tasks:**
- [ ] Thêm dependency `spring-boot-starter-aop` + `resilience4j-spring-boot3`
- [ ] Cấu hình Circuit Breaker cho `CatalogClient` trong `order-service`:
  ```yaml
  # config/order-service.yml
  resilience4j:
    circuitbreaker:
      instances:
        catalog-service:
          sliding-window-size: 10
          failure-rate-threshold: 50       # 50% lỗi → mở circuit
          wait-duration-in-open-state: 30s # chờ 30s trước khi half-open
          permitted-calls-in-half-open-state: 3
  ```
- [ ] Implement fallback method: khi circuit mở → trả về cached bookOld data hoặc `BookNotAvailableException`
- [ ] Thêm Retry với exponential backoff (3 lần, wait 1s → 2s → 4s)
- [ ] Thêm Timeout: request catalog không quá 2 giây
- [ ] Expose circuit breaker state qua `/actuator/circuitbreakers`
- [ ] Viết test: mock catalog service trả về lỗi → verify circuit breaker mở sau ngưỡng

**Verify:**
```bash
# Kill catalog-service
# Đặt 10 đơn hàng liên tiếp
# Kiểm tra actuator
curl http://localhost:9002/actuator/circuitbreakers
# Kỳ vọng: state = OPEN sau khi vượt threshold
```

---

### 2.3 Caching

**Service:** `catalog-service`, `order-service`

**Tại sao Senior cần biết:**
Database là bottleneck thường gặp nhất. Cache đúng chỗ giảm latency và load DB đáng kể.

**Tasks:**
- [ ] **Read-Through Cache** với Redis trong `catalog-service`:
  ```java
  @Cacheable(value = "books", key = "#isbn", unless = "#result == null")
  public Book findByIsbn(String isbn) { ... }
  
  @CacheEvict(value = "books", key = "#bookOld.isbn")
  public Book updateBook(Book bookOld) { ... }
  ```
- [ ] Cấu hình Redis TTL: 5 phút cho bookOld data (hay thay đổi không nhiều)
- [ ] **Write-Behind Cache** cho API đọc lịch sử đơn hàng trong `order-service`:
  - Khi đọc orders: check Redis trước → miss → đọc DB → lưu Redis
  - Khi update order status: invalidate cache ngay lập tức
- [ ] Cấu hình Redis serialization dùng Jackson JSON (không dùng Java serialization)
- [ ] Xử lý **cache stampede**: dùng `@DistributedLock` hoặc probabilistic early expiration
- [ ] Test: Đo response time trước và sau khi có cache (cache hit < 5ms, cache miss < 50ms)

**Verify:**
```bash
# First call (cache miss)
time curl http://localhost:9001/books/isbn-123
# Second call (cache hit)
time curl http://localhost:9001/books/isbn-123
# Kỳ vọng: lần 2 nhanh hơn đáng kể
```

---

### 2.4 Saga Pattern ✅

**Service:** `order-service`, `inventory-service`, `dispatcher-service`

**Tại sao Senior cần biết:**
Không có distributed transaction trong microservices. Saga là cách đảm bảo data consistency qua nhiều services với khả năng rollback (compensation).

**Đã hoàn thành:** Choreography-based saga với Outbox + Debezium. Xem `docs/tasks/saga-outbox-plan.md`.

**Luồng Saga đã implement:**
```
PlaceOrder Saga (Choreography-based):
  Step 1: order-service     → Save Order + outbox row → Debezium → order-created-events
  Step 2: inventory-service → Reserve Stock + outbox row → Debezium → inventory-events
  Step 3: order-service     → [reserved] → ACCEPTED + outbox row → Debezium → order-accepted
  Step 3: order-service     → [rejected] → CANCELLED (compensation)
  Step 4: dispatcher-service → [order-accepted] → dispatch → order-dispatched
  Step 5: order-service     → [order-dispatched] → DISPATCHED
```

**Idempotency đã implement:**
- inventory: `reservationPort.findByOrderId` short-circuit + Redis claim
- search: idempotent upsert/delete by ISBN

**Stress test đã pass:** 1000 orders / 50 concurrency = 100% DISPATCHED, exactly 1000 inventory consumed.

**Verify:**
- Xem `docs/tasks/saga-outbox-plan.md` §3.2 cho kết quả stress test chi tiết

---

## 🔐 MODULE 3 — Security & Advanced Data Patterns
> **Mục tiêu:** Production-grade security + performance data patterns.
> **Thời gian:** 2–3 tuần

---

### 3.1 Complete Security với Keycloak

**Service:** `edge-service`, `order-service`, `catalog-service`

**Tasks:**
- [ ] **Authentication flow:** Client → Keycloak (OIDC) → JWT → edge-service validate → forward to services
- [ ] Phân quyền chi tiết:
  | Role | Quyền |
  |------|-------|
  | `ROLE_CUSTOMER` | Đọc catalog, tạo/xem đơn hàng của mình |
  | `ROLE_EMPLOYEE` | Tất cả catalog CRUD, xem tất cả đơn hàng |
  | `ROLE_ADMIN` | Full access + user management |
- [ ] Implement `@PreAuthorize` tại method level (không chỉ URL level)
- [ ] **CSRF protection** cho stateless APIs: verify JWT trên mỗi request
- [ ] **Rate limiting** tại edge-service: max 100 req/min per user (dùng Redis token bucket)
- [ ] Test: Viết security integration test cho tất cả các endpoint với đủ 3 roles

---

### 3.2 Write-Behind Cache

**Service:** `order-service`

**Tại sao Senior cần biết:**
Một số data không cần write đến DB ngay lập tức (ví dụ: view counts, audit logs, session state). Write-Behind giảm tải DB đáng kể.

**Use case trong dự án:** Order activity log (mỗi lần đọc order → ghi audit log)

**Tasks:**
- [ ] Implement Write-Behind cho `OrderAuditLog`:
  - API gọi → ghi ngay vào Redis Queue
  - Background worker (mỗi 5s) → batch flush Queue → insert vào PostgreSQL
- [ ] Đảm bảo không mất data khi service restart: Redis persistence hoặc fallback sync write
- [ ] Cấu hình batch size: tối đa 100 records mỗi flush
- [ ] Monitor queue depth qua custom metric

---

## 📡 MODULE 4 — Observability & Production Readiness
> **Mục tiêu:** Hệ thống "nhìn thấy được" từ bên trong — prerequisite cho mọi production system.
> **Thời gian:** 2 tuần

---

### 4.1 Distributed Tracing Hoàn Chỉnh

**Service:** Tất cả services

**Tasks:**
- [ ] Thêm `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` vào tất cả services
- [ ] Cấu hình propagation: `traceId` tự động truyền qua Kafka headers và HTTP headers
- [ ] Đảm bảo trace cover toàn bộ luồng:
  ```
  HTTP Request → edge-service → order-service → [Kafka] → inventory-service
       ↑______________________________________traceId xuyên suốt_____________↑
  ```
- [ ] Tạo custom `Span` cho các business operations quan trọng:
  ```java
  Span span = tracer.nextSpan().name("reserve-stock").start();
  try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
      stockService.reserve(orderId, items);
  } finally {
      span.end();
  }
  ```
- [ ] Cấu hình sampling rate: 100% cho dev, 10% cho production (performance-aware)
- [ ] Deploy Zipkin UI qua Docker Compose và verify traces hiển thị đúng

**Verify:**
- Đặt một đơn hàng và mở Zipkin UI
- Thấy trace duy nhất từ HTTP request → Kafka event → inventory processing

---

### 4.2 Custom Metrics

**Service:** `order-service`, `inventory-service`

**Tasks:**
- [ ] Implement các business metrics sau với Micrometer:
  ```java
  // Counter: tổng số đơn hàng được đặt
  Counter ordersPlaced = Counter.builder("bookstore.orders.placed")
      .tag("status", "success")
      .description("Total orders placed")
      .register(meterRegistry);
  
  // Gauge: số stock hiện tại theo ISBN
  Gauge.builder("bookstore.stock.available", stockService, StockService::getTotalAvailableStock)
      .register(meterRegistry);
  
  // Timer: thời gian xử lý reservation
  Timer reservationTimer = Timer.builder("bookstore.stock.reservation.duration")
      .register(meterRegistry);
  
  // Distribution Summary: giá trị đơn hàng
  DistributionSummary orderValue = DistributionSummary.builder("bookstore.orders.value")
      .baseUnit("vnd")
      .register(meterRegistry);
  ```
- [ ] Cấu hình Prometheus scrape endpoint (`/actuator/prometheus`)
- [ ] Viết Grafana dashboard JSON với 4 panels: order rate, error rate, stock levels, p99 latency
- [ ] Thêm alerts: nếu error rate > 5% trong 5 phút → alert

**Verify:**
```bash
curl http://localhost:9002/actuator/prometheus | grep bookstore
# Kỳ vọng: thấy tất cả custom metrics với labels
```

---

## ☁️ MODULE 5 — DevOps Nâng Cao & Production AWS
> **Mục tiêu:** Deploy được lên production thật sự, với quy trình chuyên nghiệp.
> **Thời gian:** 3–4 tuần

---

### 5.1 Helm Charts

**Tại sao Senior cần biết:**
K8s raw YAML không scalable. Helm là tiêu chuẩn để package và deploy applications.

**Tasks:**
- [ ] Tạo Helm chart template cho mỗi service:
  ```
  helm/
  ├── bookstore/
  │   ├── Chart.yaml
  │   ├── values.yaml          ← defaults
  │   ├── values-staging.yaml  ← staging overrides
  │   ├── values-prod.yaml     ← production overrides
  │   └── templates/
  │       ├── deployment.yaml
  │       ├── service.yaml
  │       ├── configmap.yaml
  │       ├── secret.yaml
  │       └── hpa.yaml         ← HorizontalPodAutoscaler
  ```
- [ ] Parameterize: image tag, replica count, resource limits, environment
- [ ] Tạo umbrella chart `bookstore-stack` bao gồm tất cả services + infra (PostgreSQL, Kafka)
- [ ] `helm lint` và `helm template` phải pass clean
- [ ] Deploy lên local Minikube và verify tất cả pods healthy

**Verify:**
```bash
helm install bookstore-dev ./helm/bookstore-stack -f values-staging.yaml
kubectl get pods -n bookstore
# Kỳ vọng: tất cả pods = Running
```

---

### 5.2 SonarQube

**Tại sao Senior cần biết:**
Code quality gate là prerequisite của nhiều enterprise project. Senior biết đọc và cải thiện SonarQube report.

**Tasks:**
- [ ] Chạy SonarQube server local bằng Docker:
  ```bash
  docker run -d --name sonarqube -p 9000:9000 sonarqube:community
  ```
- [ ] Cấu hình Gradle plugin `org.sonarqube` cho mỗi service
- [ ] Thiết lập Quality Gate tối thiểu:
  | Metric | Ngưỡng |
  |--------|--------|
  | Coverage | ≥ 80% |
  | Duplicated Lines | < 3% |
  | Maintainability Rating | A |
  | Reliability Rating | A |
  | Security Rating | A |
- [ ] Fix tất cả Bugs và Vulnerabilities (severity Critical/Blocker)
- [ ] Tích hợp SonarQube scan vào GitHub Actions CI pipeline

---

### 5.3 Contract Testing với Pact

**Tại sao Senior cần biết:**
Integration tests kiểm tra end-to-end quá chậm và flaky. Contract test đảm bảo API compatibility giữa services mà không cần chạy cả stack.

**Contract: `order-service` (consumer) ↔ `catalog-service` (provider)**

**Tasks:**
- [ ] **Consumer side** (`order-service`): Viết Pact test định nghĩa những gì `CatalogClient` kỳ vọng:
  ```java
  @Pact(consumer = "order-service", provider = "catalog-service")
  RequestResponsePact getBookByIsbn(PactDslWithProvider builder) {
      return builder
          .given("bookOld with isbn exists")
          .uponReceiving("get bookOld by isbn")
          .path("/books/978-3-16-148410-0")
          .method("GET")
          .willRespondWith()
          .status(200)
          .body(/* expected body */)
          .toPact();
  }
  ```
- [ ] **Provider side** (`catalog-service`): Viết provider verification test
- [ ] Publish contracts lên Pact Broker (có thể self-host bằng Docker)
- [ ] Tích hợp Pact verification vào CI pipeline của `catalog-service`
- [ ] Test scenario: thay đổi API response của `catalog-service` → contract test fail → catch bug trước khi deploy

---

### 5.4 Deploy Lên AWS Production

**Architecture mục tiêu trên AWS:**

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

**Tasks theo thứ tự:**
- [ ] **ECR:** Push tất cả Docker images lên Amazon ECR
  ```bash
  ./gradlew bootBuildImage --imageName=<ecr-url>/catalog-service:latest
  docker push <ecr-url>/catalog-service:latest
  ```
- [ ] **RDS:** Tạo PostgreSQL RDS instance (Multi-AZ cho production, Single-AZ cho staging)
- [ ] **MSK:** Tạo Amazon MSK (Kafka managed service) cluster
- [ ] **ElastiCache:** Tạo Redis cluster cho caching
- [ ] **EKS:** Tạo Kubernetes cluster với `eksctl`:
  ```bash
  eksctl create cluster --name bookstore-prod --region ap-southeast-1 --nodegroup-name workers --node-type t3.medium --nodes 3
  ```
- [ ] **Secrets Manager:** Lưu DB passwords, Kafka credentials trong AWS Secrets Manager, inject vào pods qua External Secrets Operator
- [ ] **Deploy Helm:** `helm install bookstore ./helm/bookstore-stack -f values-prod.yaml`
- [ ] **DNS + TLS:** Cấu hình Route53 + ACM certificate + ALB Ingress Controller
- [ ] **Monitoring:** Cài kube-prometheus-stack (Grafana + Prometheus + AlertManager)

**Verify:**
```bash
# Gọi production endpoint
curl https://api.bookstore.yourdomain.com/books
# Kỳ vọng: 200 OK với dữ liệu thật
```

---

## 📈 Tracker Tiến Độ Technology

| Module | Topic | Status |
|--------|-------|--------|
| 1 | Global Exception Handling | ⬜ |
| 1 | Validation | ⬜ |
| 1 | Structured Logging | ⬜ |
| 2 | Outbox Pattern | ✅ |
| 2 | Circuit Breaker | ⬜ |
| 2 | Caching (Read-Through) | ⬜ |
| 2 | Saga Pattern | ✅ |
| 3 | Security hoàn chỉnh | ⬜ |
| 3 | Write-Behind Cache | ⬜ |
| 4 | Distributed Tracing | ⬜ |
| 4 | Custom Metrics | ⬜ |
| 5 | Helm Charts | ⬜ |
| 5 | SonarQube | ⬜ |
| 5 | Contract Testing (Pact) | ⬜ |
| 5 | Deploy AWS Production | ⬜ |

---

## 💡 Thứ Tự Thực Hiện Khuyến Nghị

```
Module 1 (Foundation) → Module 2 (Distributed) → Module 4 (Observability) → Module 3 (Security) → Module 5 (DevOps/AWS)
```

> **Lý do:** Module 1 dễ nhưng tạo nền tảng cho mọi thứ.
> Module 4 (tracing, metrics) nên làm sớm để khi debug Module 2, 3 có công cụ nhìn vào system.
> Module 5 làm cuối để có đủ features để deploy lên production có ý nghĩa.
