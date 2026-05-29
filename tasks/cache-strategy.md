# Cache Strategy — Pragmatic per-service (không bắt buộc AP)

> Bản thay thế thực dụng cho `cache-ap-strategy.md`. Companion của `saga-outbox-plan.md`.
>
> **Triết lý**: cache là *tối ưu hiệu năng*, không phải nguồn sự thật. Mỗi service chọn mức consistency phù hợp với cost của việc sai:
> - Đọc catalog/search sai vài giây → vô hại → cache mạnh, eventual.
> - Reserve tồn kho sai → mất tiền → KHÔNG dùng cache để quyết định, dùng Postgres atomic.
>
> Bỏ ràng buộc "AP everywhere". Không ép stale-if-error / soft-expire toàn hệ thống. Chỉ thêm khi đo được lợi ích.

## 0. Nguyên tắc chung

1. **Cache không bao giờ là điều kiện đúng/sai của ghi tiền** (order accept, inventory reserve). Những path đó đi thẳng Postgres + outbox.
2. **Evict/refresh bằng event** (Book events qua outbox/Kafka), không "delete cache rồi update DB" đồng bộ.
3. **Cache adapter không được throw** khi Redis lỗi → log + metric + fallback (origin hoặc snapshot local). Đây là điều duy nhất giữ lại từ tư duy AP vì nó rẻ.
4. **TTL là chặn trên của staleness**, không phải cơ chế chính.
5. Chỉ thêm L2 Redis khi có >1 instance cần chia sẻ, hoặc cold-start L1 đắt. Không thêm vì "cho đẹp".

## 1. `catalog-service` — cache mạnh cho read (origin của Book)

Đây là origin dữ liệu sách → cache để giảm tải Postgres, không cần consistency mạnh.

- **L1 Caffeine + L2 Redis** cho `GET /books` (page) và `GET /books/{isbn}`.
  - Hiện `@/home/dgwa/Workspaces/spring-native-bookstore/catalog-service/src/main/java/com/locpham/bookstore/catalogservice/config/CacheConfig.java:11-17` mới khai báo Caffeine `books`. Cần:
    - Wire `@Cacheable("books", key=isbn)` vào `findByIsbn`, `@Cacheable("booksPage", key=pageable)` vào `findAll`.
    - Thêm L2 Redis qua `RedisCacheManager` hoặc composite (Caffeine near-cache + Redis). Vì catalog là MVC blocking (Data JDBC), dùng `spring-boot-starter-cache` + `spring-boot-starter-data-redis` đơn giản nhất.
  - TTL: books `10m`, booksPage `2m` (page dễ stale hơn).
- **Evict on write**: `@CacheEvict` trên create/update/delete; với page cache dùng `@CacheEvict(allEntries=true, cacheNames="booksPage")` (đơn giản, page query khó key chính xác).
- **Publish Book events via outbox**: thay `KafkaBookEventPublisher` (Sinks) trong `@/home/dgwa/Workspaces/spring-native-bookstore/catalog-service/src/main/java/com/locpham/bookstore/catalogservice/adapter/out/messaging/KafkaBookEventPublisher.java` bằng ghi `outbox_event` cùng tx với DB write (theo `saga-outbox-plan.md §3`). Event Book là nguồn nuôi cả search-service và snapshot của order-service.

## 2. `search-service` — index từ event, cache query TTL ngắn

- **Build search index từ Book events**: đã có `BookEventConsumer` → `BookIndexService` → ES. Giữ nguyên. **Không gọi catalog trực tiếp** — đã đúng.
- **Cache query result TTL ngắn**: thêm cache cho các method trong `@/home/dgwa/Workspaces/spring-native-bookstore/search-service/src/main/java/com/locpham/bookstore/searchservice/adapter/out/search/ElasticsearchSearchQueryAdapter.java`.
  - Service là WebFlux reactive → KHÔNG dùng `@Cacheable` (không hỗ trợ `Mono`/`Flux` tốt). Dùng cache thủ công kiểu `ReactiveTwoLevelCache` (đã có pattern ở inventory) hoặc `CaffeineCache` + `Mono.cacheInvalidateWhen`.
  - Key = `(loại query, term, pageable)`. TTL `30–60s`. Autocomplete (`suggestByTitle/Author`) TTL `5m`.
  - Search stale vài chục giây vô hại → không cần evict theo event, để TTL tự hết.

## 3. `order-service` — KHÔNG gọi catalog trong happy path (SNAPSHOT)

> **Phần trọng tâm theo yêu cầu.** Hiện `SubmitOrderService` gọi `catalogBookPort.loadBook()` = HTTP đồng bộ sang catalog (`@/home/dgwa/Workspaces/spring-native-bookstore/order-service/src/main/java/com/locpham/bookstore/orderservice/adapter/out/catalog/CatalogWebClientAdapter.java:33`). Đây là coupling đồng bộ cần loại bỏ.

### 3.1 Local `catalog_book_snapshot` từ Book events

- **Flyway** `V<next>__create_catalog_book_snapshot.sql` (R2DBC + Postgres `polardb_order`):
  ```sql
  CREATE TABLE catalog_book_snapshot (
    isbn        TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    price       NUMERIC(10,2) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_ts    TIMESTAMPTZ            -- timestamp của Book event để chống out-of-order
  );
  ```
  Chạy `./gradlew generateJooq` sau migration (theo AGENTS.md quirk).
- **Consumer mới** `adapter/in/messaging/BookEventConsumer` (binding `book-events`, dùng chung topic với search):
  - `book.created` / `book.updated` → upsert snapshot (chỉ ghi nếu `event_ts >= updated_at` để idempotent + chống reorder).
  - `book.deleted` → đánh dấu hoặc xóa snapshot (sách bị xóa → đơn mới reject).
- **`CatalogBookPort` đổi implementation**: bỏ HTTP, đọc từ snapshot local.
  - Giữ nguyên interface `@/home/dgwa/Workspaces/spring-native-bookstore/order-service/src/main/java/com/locpham/bookstore/orderservice/application/port/out/CatalogBookPort.java:6-8` (`Mono<BookSnapshot> loadBook(isbn)`), chỉ thay adapter → `SnapshotBookAdapter` đọc bảng `catalog_book_snapshot`.
  - `SubmitOrderService` không đổi: snapshot rỗng → `switchIfEmpty` đã build rejected order sẵn (`@/home/dgwa/Workspaces/spring-native-bookstore/order-service/src/main/java/com/locpham/bookstore/orderservice/application/service/SubmitOrderService.java:44`).
  - **Xóa** `CatalogWebClientAdapter` + `CatalogClientConfig` + `polar.catalog-service-url` sau khi cutover (giữ lại như fallback tùy chọn ở §3.3).

### 3.2 Cache snapshot bằng L1/L2 riêng

- Snapshot đã là local DB (rất nhanh), nhưng nếu cần thêm: `ReactiveTwoLevelCache<BookSnapshot>` (tái dùng pattern inventory) — L1 Caffeine per-pod + L2 Redis dùng riêng key prefix `order:booksnap:{isbn}`.
- Evict cache snapshot khi consumer nhận `book.updated/deleted`.
- TTL L1 `5m`, L2 `30m`. Vì đã có DB snapshot làm origin → cache lỗi chỉ tốn 1 query DB local, không gọi mạng ngoài.

### 3.3 Fallback (tùy chọn, không bắt buộc)

- Trường hợp cực hiếm: đặt mua sách vừa được tạo ở catalog nhưng event chưa tới (eventual lag). Có 2 lựa chọn, chọn 1:
  - **Đơn giản (khuyến nghị)**: reject đơn với lý do "book not available yet", client retry. Chấp nhận eventual.
  - **Có fallback HTTP**: nếu snapshot miss → 1 lần gọi catalog HTTP (giữ `CatalogWebClientAdapter` làm fallback sau snapshot). Đánh đổi: tái lập coupling một phần. Chỉ làm nếu nghiệp vụ yêu cầu.

### 3.4 Idempotency cho `POST /orders` bằng Redis

- Client gửi header `Idempotency-Key`. `SubmitOrderService` (hoặc filter trước) check Redis `SETNX order:idem:{key}` TTL `24h`:
  - Tồn tại → trả lại `orderId` đã tạo (lưu mapping `key -> orderId` trong Redis).
  - Chưa có → xử lý, lưu mapping.
- Redis down → **log + bypass** (chấp nhận rủi ro trùng đơn ngắn hạn) HOẶC chặt hơn: unique constraint `(idempotency_key)` trên bảng order. Chọn theo khẩu vị; mặc định bypass + log.

### 3.5 Outbox cho order events

- Theo `saga-outbox-plan.md §3.1`: `OrderCreated/Accepted/Cancelled` ghi `outbox_event` cùng tx với order save thay vì publish trực tiếp. Không liên quan cache nhưng là tiền đề cho evict bằng event đáng tin.

## 4. `inventory-service` — KHÔNG cache để quyết định reserve

- **Reserve = Postgres atomic update** (optimistic version) — đã đúng ở `@/home/dgwa/Workspaces/spring-native-bookstore/inventory-service/src/main/java/com/locpham/bookstore/inventoryservice/application/service/ReserveStockService.java`. **Không** đọc `ReactiveTwoLevelCache` trong path reserve.
- **`ReactiveTwoLevelCache` hiện tại** (`@/home/dgwa/Workspaces/spring-native-bookstore/inventory-service/src/main/java/com/locpham/bookstore/inventoryservice/config/ReactiveCacheConfig.java:28-31`) chỉ được dùng cho **availability display** (`GET` hiển thị "còn hàng/hết hàng"), TTL ngắn `5–10s`. Rà soát lại đảm bảo nó KHÔNG bị dùng trong `reserveForOrder`.
- **Redis cho idempotency**: thay/bổ trợ check `reservationPort.findByOrderId` bằng Redis `SETNX inv:idem:{orderId}` để dedupe nhanh trước khi chạm DB; DB vẫn là chốt chặn cuối (reservation theo orderId).
- Hiển thị "còn hàng" có thể stale → khi reserve thật, Postgres reject → user nhận `REJECTED` (đã có cơ chế qua event).

## 5. `edge-service`

- **Redis cho rate limit / session** nếu cần (Spring Cloud Gateway `RequestRateLimiter`). Redis down → fail-open + header cảnh báo.
- **KHÔNG cache order** (dữ liệu cá nhân, thay đổi nhanh).
- **CloudFront / CDN** cache public GET catalog & search (`Cache-Control: public, max-age=30`). Edge chỉ set header, không tự cache order.

## 6. Thứ tự triển khai (cutover)

1. **Outbox trước** (catalog + order) → đảm bảo Book/Order events đáng tin (theo `saga-outbox-plan.md`).
2. **order-service snapshot**: tạo bảng + `BookEventConsumer` + `SnapshotBookAdapter`. Chạy song song HTTP một thời gian (shadow read) để verify snapshot khớp.
3. Chuyển `CatalogBookPort` sang snapshot, theo dõi reject rate. Ổn → xóa HTTP adapter.
4. **catalog-service** wire L1+L2 + `@CacheEvict`.
5. **search-service** query cache TTL ngắn.
6. **inventory-service** Redis idempotency + tách cache khỏi reserve.
7. **edge-service** rate limit + CDN headers.

## 7. Tests cần thêm

- `BookEventConsumerIT` (order): nhận `book.created/updated/deleted` → snapshot upsert/xóa đúng; event out-of-order (`event_ts` cũ) → bỏ qua.
- `SubmitOrderServiceTest`: snapshot tồn tại → PENDING; snapshot rỗng → REJECTED; **không** còn gọi HTTP catalog.
- `IdempotencyIT` (order): cùng `Idempotency-Key` 2 lần → 1 order; Redis down → không vỡ (bypass + log).
- `ReserveStockServiceTest`: khẳng định reserve KHÔNG đọc cache; chỉ Postgres.
- `CatalogCacheEvictIT`: update book → cache `books` bị evict, lần đọc sau lấy giá mới.
- `SearchQueryCacheIT`: 2 query giống nhau trong TTL → ES chỉ bị gọi 1 lần.

## 9. Resilience (chống lỗi & quá tải)

> Nguyên tắc: **mọi cuộc gọi ra ngoài (Redis, ES, Kafka, HTTP) phải có timeout + fallback rõ ràng**. Cache/snapshot lỗi → degrade, không sập. Tái dùng resilience4j (đã có ở `edge-service`) thay vì tự chế.

### 9.1 Dependencies (các service reactive: order/inventory/search/catalog)

```gradle
implementation 'io.github.resilience4j:resilience4j-reactor'
implementation 'io.github.resilience4j:resilience4j-spring-boot3' // dùng annotation @CircuitBreaker/@Retry/@TimeLimiter
```

### 9.2 Config dùng chung (`config/application.yml` qua Config Server)

Mở rộng từ block đã có ở `@/home/dgwa/Workspaces/spring-native-bookstore/config/edge-service.yml:99-110`:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      cache:        # bao quanh Redis L2 / ES query
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        slow-call-duration-threshold: 300ms
        slow-call-rate-threshold: 80
  timelimiter:
    configs:
      cache: { timeout-duration: 300ms }   # Redis/ES fail-fast → fallback
      origin: { timeout-duration: 2s }
  retry:
    configs:
      transient:
        max-attempts: 3
        wait-duration: 100ms
        enable-exponential-backoff: true
```

Redis timeout client cũng phải ngắn (theo edge: `timeout: 500ms`) để CB/timelimiter có tác dụng.

### 9.3 Per-service

**catalog-service**
- L2 Redis bọc CircuitBreaker `cache` + TimeLimiter `cache` → Redis lỗi/chậm thì fallback xuống L1 Caffeine → Postgres. **Không** để Redis làm sập read.
- Postgres read: TimeLimiter `origin`; write lỗi → 5xx bình thường (không cache write).

**search-service**
- ES query bọc CircuitBreaker + TimeLimiter `cache`. CB open → trả page rỗng kèm header `X-Search-Degraded: true` HOẶC last-known từ cache (tùy chọn), không 5xx.
- Cache Redis (nếu dùng cho query result) bọc fallback về gọi ES.

**order-service** (trọng tâm — đã cắt HTTP catalog)
- Snapshot đọc từ **DB local** → đã resilient; chỉ cần TimeLimiter `origin`. Đây là lợi ích chính của snapshot: bỏ phụ thuộc mạng ngoài.
- `BookEventConsumer`: `retryWhen(Retry.backoff(...))` cho lỗi transient (giống pattern `@/home/dgwa/Workspaces/spring-native-bookstore/inventory-service/src/main/java/com/locpham/bookstore/inventoryservice/adapter/in/messaging/OrderEventConsumer.java:24-26`); lỗi không phục hồi → DLQ (`enableDlq` của Spring Cloud Stream) để không chặn partition.
- Redis idempotency bọc CircuitBreaker `cache` + TimeLimiter `cache`; CB open → theo lựa chọn đã chốt (bypass+log hoặc unique constraint DB).
- Nếu giữ fallback HTTP (§3.3): bọc CircuitBreaker riêng `catalogCircuitBreaker` + TimeLimiter, fallback cuối → reject đơn.

**inventory-service**
- Giữ `Retry.backoff` cho `OptimisticLockingFailureException` (đã có). Reserve **không** CB (phải đi tới Postgres; lỗi DB → reject thật, không degrade).
- Redis idempotency + availability cache bọc CB `cache`; lỗi → bypass idempotency Redis (DB reservation theo orderId là chốt) / ẩn số "còn hàng".
- Kafka consumer reserve: retry transient + DLQ.

**edge-service** (đã có sẵn — giữ & tinh chỉnh)
- `Retry` filter (GET, SERVER_ERROR) + `CircuitBreaker` per-route + `RequestRateLimiter` (Redis) đã có ở `edge-service.yml`. Bổ sung: controller `/catalog-fallback`, `/search-fallback` trả **stale từ CDN/last-known** thay vì lỗi cứng cho GET public.
- `RequestRateLimiter` Redis down → fail-open (đã nêu §5).

### 9.4 Bulkhead / quá tải

- Tách connection pool: Redis pool riêng cho cache vs session/rate-limit để một bên quá tải không kéo bên kia (edge đã có Lettuce pool; order/inventory cấu hình tương tự).
- Search ES: `Bulkhead` (`maxConcurrentCalls`) giới hạn truy vấn nặng để không vắt kiệt ES.

### 9.5 Tests resilience

- `RedisDownIT` (order/catalog): tắt Redis giữa test → read vẫn trả từ L1/snapshot/origin; idempotency theo lựa chọn đã chốt.
- `CircuitBreakerOpenIT` (search): ép ES chậm > threshold → CB open → trả degraded, không 5xx.
- `BookEventRetryIT` (order): event lỗi transient → retry; lỗi vĩnh viễn → vào DLQ, không chặn các event sau.
- `OptimisticLockRetryTest` (inventory): xác nhận retry 3 lần rồi mới reject.

## 10. So với `cache-ap-strategy.md` — bỏ gì

- Bỏ "AP everywhere", bỏ `staleness_grade` 4 mức, bỏ stale-if-error toàn cục, bỏ ép Redis Cluster 6 node ngay từ đầu.
- Giữ: cache adapter không throw (fallback), evict bằng event, reserve/accept là CP qua Postgres+outbox.
- Thêm mới: **catalog_book_snapshot** ở order-service để cắt hẳn HTTP đồng bộ sang catalog trong happy path.
