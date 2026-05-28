# Cache Strategy — AP-first (Availability + Partition Tolerance)

> Companion document to `saga-outbox-plan.md`. This file replaces §12 of that plan.
>
> **CAP choice**: under network partition we **always serve a response** (possibly stale) instead of failing. Consistency is *eventual*, enforced by event-driven invalidation, not by synchronous coordination.

## 0. Decision (TL;DR)

- **AP everywhere on the read path.** A cache miss + origin failure → serve last known value (stale-while-error). Never return 5xx because the cache is degraded.
- **CP only on the write path for sagas** (orders, inventory reservations). Those bypass cache entirely and go straight to Postgres + outbox. Cache strategy and saga durability are **orthogonal**.
- **Two-tier**: L1 Caffeine (per-pod) + L2 Redis Cluster (shared). Both are AP by configuration.
- **Stale-tolerant semantics**: every cached object has a `staleness_grade` (FRESH / STALE_OK / STALE_BUT_USABLE / DO_NOT_USE) and the application decides per call site.
- **Reject CP cache designs**: no synchronous write-through, no `Cache-Aside with strong consistency`, no distributed locks for invalidation. All forbidden because they sacrifice A under partition.

## 1. Why Caffeine + Redis is the right AP combo

You asked: *"with 1M users, why Caffeine?"* — here's the AP-framed answer.

| Layer | A under partition | P tolerance | Scale to 1M users |
|-------|-------------------|-------------|-------------------|
| **L1 Caffeine** (per-pod, in-process) | Perfect — no network, can't partition from itself | Perfect — each pod is its own island | Scales with pod count. 100 pods × 10 MB = 1 GB aggregate, zero coordination cost. Hit ratio per-pod is what matters, not global. |
| **L2 Redis Cluster** (shared, 3+ shards × 2 replicas) | High — replica auto-failover, `cluster-require-full-coverage no` lets surviving shards keep serving | High — split-brain handled via Sentinel/Cluster; we accept some stale reads | 100k+ ops/s per shard; shard by key prefix. 3 shards handles 300k ops/s comfortably. |
| **L3 Origin** (Postgres, catalog HTTP, ES) | CP by nature — can refuse writes during partition | Limited — Postgres primary failover takes seconds | Bottleneck — that's why we cache aggressively. |

**Caffeine is not an alternative to Redis** — they handle different failure modes:

- **Redis partition / outage** → L1 Caffeine keeps serving from RAM. System stays up.
- **Pod restart / cold L1** → L2 Redis fills the gap in 0.5 ms.
- **Both down** → fall through to origin (still A, just slower).
- **Origin down** → serve last cached value with `X-Cache-Stale: true` header.

Removing L1 means a Redis outage = total system outage for cached reads. **Bad for AP.**

## 2. AP rules (non-negotiable)

1. **Cache adapters must NEVER throw on Redis errors.** Catch, log, increment `cache.error` metric, fall through to next tier. Wrap in `onErrorResume(e -> origin.call())`.
2. **Stale-while-revalidate** is the default. If TTL expired but origin is slow/down, return stale value and refresh async.
3. **Stale-if-error**: even past `expireAfterWrite`, keep entry in a "soft-expired" zone (use `Caffeine.expireAfter(Expiry)`) for up to 1 h. Serve it if origin fails.
4. **No distributed locks** for cache fill. Use `Caffeine.refreshAfterWrite` for single-flight per pod; accept that N pods may each fetch once concurrently (N × origin load, bounded by pod count, not user count).
5. **Eventual consistency via events.** All cache invalidation is by Kafka event (`book-events`, etc.). No synchronous "delete cache then update DB" — that's the CP pattern we reject.
6. **No `Cache-Aside with read-through-write-through`** as commonly described in textbooks — those require strong coordination. We do **read-aside with event-driven invalidation**, which is the AP variant.
7. **Bounded staleness via TTL** as the *upper bound* on inconsistency, not as the primary mechanism. Default: 5 min for catalog data, 30 s for order detail, 5 s for stock.
8. **Idempotency keys MUST survive cache failures.** Idempotency check goes to Redis; if Redis is down, the idempotency check is **bypassed and logged** (we prefer accepting a possible duplicate over rejecting a real order — AP). Alternative for CP-strict use cases: persist idempotency key in Postgres in the same tx as the order; slower but consistent.

## 3. Per-service cache plan (AP-tuned)

### 3.1 `edge-service` (Spring Cloud Gateway)

- **JWK cache** — Nimbus default 5 min. On Keycloak partition: serve from cache; if expired AND Keycloak unreachable, **accept tokens until cache age = 1 h** (configurable `jwk-set-cache.lifespan`). Beyond that, return 503. *Trade-off: accept some risk of expired key being used during outage.*
- **`LocalResponseCache` on public GETs** — 30 s TTL, stale-if-error 5 min. Use `Cache-Control: public, max-age=30, stale-if-error=300` headers.
- **Rate-limit buckets** — Redis. On Redis partition: **fail open** (allow request through with `X-RateLimit-Degraded: true` header). Better to risk abuse for a few seconds than reject all traffic.

### 3.2 `catalog-service`

- **L1 Caffeine** on `findByIsbn`, `findAll(page)`. 10 min TTL + 1 h stale-if-error.
- **No L2** (catalog is the origin of book data — caching its own data in Redis is pointless and adds a failure mode).
- On Postgres partition: serve stale from Caffeine. Write endpoints return 503. Reads stay up.

### 3.3 `order-service`

- **`CatalogBookPort` two-tier cache** (this is where AP matters most for latency):

  ```
  loadBook(isbn):
    1. Caffeine.get(isbn) → if FRESH, return.
    2. If STALE_OK and refresh-needed → async refresh, return stale value now.
    3. Redis.get(book:{isbn}) → populate L1, return.
    4. If Redis fails → log, fall through.
    5. HTTP catalog.findByIsbn → populate L1 + L2, return.
    6. If catalog HTTP fails:
       a. If Caffeine has soft-expired value (within 1 h) → return with stale flag, log.
       b. If Redis has any value → return with stale flag.
       c. Otherwise → return Optional.empty(), let SubmitOrderService reject the order.
  ```

  This is the AP gold standard: every layer of failure has a fallback.

- **Order detail cache** (`GET /orders/{id}`): Caffeine 30 s, keyed `(orderId, createdBy)`. Stale-if-error 5 min. Event-driven evict on saga transitions.
- **Idempotency keys**: Redis primary, Postgres fallback (see §2 rule 8).

### 3.4 `inventory-service`

- **Read endpoint stock cache**: Caffeine 5 s. Very short because oversells are real money lost — here we lean slightly toward C even in AP design. Stale-if-error: **none** (return 503 if origin down). Inventory reads are the one place where stale data has dollar cost.
- **Reservation lookup**: no cache (consistency-critical, Postgres buffer pool is enough).

### 3.5 `search-service`

- **Top-N query cache**: Caffeine 60 s, stale-if-error 10 min. Search staleness is harmless.
- **Autocomplete**: Caffeine 5 min, stale-if-error 1 h.
- **Consumer dedupe**: Redis 24 h. On Redis down: **process the event anyway** (idempotent ES upsert handles duplicates).

### 3.6 `dispatcher-service`, `config-service`

- No cache. Stateless / already cached internally.

## 4. Redis Cluster topology for AP at 1M users

- **3 shards minimum**, each with 1 replica (6 nodes total).
- `cluster-require-full-coverage no` → if 1 shard partitions, the other 2 keep serving their key ranges.
- `cluster-allow-reads-when-down yes` → replicas serve reads during failover (~30 s window where reads might be stale by a few writes — acceptable AP).
- **Sentinel** (or Redis Cluster's built-in failover) for primary election.
- **Client config**: Lettuce with `topology-refresh.adaptive=true`, `read-from=REPLICA_PREFERRED` for cache reads (writes always to primary).
- **Sizing**: 1M users, ~10k RPS reads, ~80% cache hit ratio → 8k Redis ops/s. Each shard handles 100k ops/s. **3 shards is 10× over-provisioned** — fine, leaves headroom.
- **Memory**: 1M users × 5 KB cached profile = 5 GB. Per-shard with replication: 5 GB ÷ 3 × 2 = ~3.3 GB per node. Run on `cache.r6g.large` (13 GB) → plenty of room.
- **maxmemory-policy**: `allkeys-lru` (evict least recently used when full — AP-friendly, never refuses writes due to memory).

## 5. Caffeine sizing for 1M users

Per pod, assuming 50 pods of order-service:

| Cache | maxSize | Entry size | Memory/pod | Hit ratio target |
|-------|---------|------------|------------|------------------|
| `books` | 10k | 1 KB | 10 MB | >85% (hot book skew) |
| `orders` | 5k | 2 KB | 10 MB | >70% (recent orders) |
| `idempotency` (local mirror of recent Redis hits) | 1k | 100 B | 100 KB | n/a |

Total per pod: ~25 MB Caffeine RAM. 50 pods × 25 MB = 1.25 GB aggregate "free" cache.

For 1M users with Pareto skew (top 10% = 80% of traffic): 100k "hot" books fits in Caffeine across the fleet (100 pods × 10k entries each, ~100k unique entries with overlap). **Hit ratio stays high.**

## 6. Failure modes & AP responses

| Failure | What happens | AP response |
|---------|-------------|-------------|
| 1 pod's Caffeine cold (restart) | L1 miss | Goes to L2 Redis, <1 ms |
| Redis shard down | L2 miss for that key range | Goes to origin; L1 still serves hot keys |
| Entire Redis cluster down | All L2 misses | L1 serves what it has; new requests go to origin; system stays up at degraded latency |
| Catalog-service partition | Book lookups fail | Order-service serves stale book from L1/L2 (up to 1 h old); only reject orders for ISBNs never seen before |
| Postgres primary failover | Writes fail 5–30 s | Order acceptance returns 503; reads continue from cache |
| Kafka partition | Outbox events queue in Postgres (`outbox_event` rows accumulate); Debezium catches up when partition heals | API still accepts orders (writes to Postgres + outbox in one tx); saga progresses when partition heals |
| Network split between AZs | Each side keeps serving from its own L1 + L2 replica | Eventually reconciled via outbox event replay |

## 7. Observability for AP

Metrics to dashboard and alert on:

- `cache.gets{result=hit/miss/error, cache_name, tier=l1/l2}` — hit ratio per tier.
- `cache.stale_served_total{cache_name}` — how often we served stale-on-error. Spikes = origin problems.
- `cache.refresh_failures_total` — async refresh failures.
- `redis.cluster.partition_active` — alert immediately, but no PagerDuty (system still up).
- `origin.fallback_total{service}` — full-stack fallback to origin.
- HTTP response header `X-Cache-Status: HIT-L1 | HIT-L2 | MISS | STALE-WHILE-ERROR` for debugging.

SLO targets at 1M users:

- p99 latency `POST /orders`: **<15 ms** under normal load, **<50 ms** with Redis down, **<200 ms** with catalog down (stale).
- Availability: **99.95%** monthly (allows ~22 min downtime; cache AP design eats partition events without counting against SLO).
- Cache hit ratio (L1+L2 combined): **>90%** for books, **>70%** for orders.

## 8. What we deliberately give up (be honest)

- **Read-your-writes consistency across pods**: after you update a book in catalog, other pods' Caffeine may return old price for up to 30 s (until `book-events` propagates + invalidation pub/sub fires). Document this in API.
- **Strict idempotency under Redis failure**: see §2 rule 8 — duplicate orders possible during Redis outage; mitigated by deduping in `outbox_event` on `(aggregate_id, type, idempotency_key)` unique constraint at DB level.
- **No global cache view**: you can't ask "what's in cache right now?" cluster-wide. Each pod has its own truth. This is fine for AP.
- **Stale stock in inventory reads** (5 s window): may cause UI showing "in stock" when actually sold out. The reservation step (write path) is still CP and will reject — user gets `REJECTED` status via SSE. Trade-off documented in product spec.

## 9. Build & deploy delta

### Dependencies (all four services)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-cache'
implementation 'com.github.ben-manes.caffeine:caffeine'
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
implementation 'io.lettuce:lettuce-core'  // explicit for cluster config
implementation 'io.github.resilience4j:resilience4j-reactor'  // circuit breaker around origin
```

### Redis Cluster config (`config/application.yml` — shared via Config Server)

```yaml
spring.data.redis:
  cluster:
    nodes: redis-0:6379, redis-1:6379, redis-2:6379, redis-3:6379, redis-4:6379, redis-5:6379
    max-redirects: 3
  lettuce:
    cluster:
      refresh:
        adaptive: true
        period: 30s
    pool:
      enabled: true
      max-active: 16
      max-idle: 8
  timeout: 200ms  # short — fail fast and fall through to origin
```

### Caffeine defaults (per service)

```yaml
spring.cache.type: caffeine
spring.cache.caffeine.spec: maximumSize=10000,expireAfterWrite=10m,recordStats
# Stale-if-error handled in code via custom CacheLoader, not spec
```

## 10. Cutover sequence

Same dependency ordering as §6 of `saga-outbox-plan.md`:

1. Outbox must land first (so cache invalidation events are reliable).
2. Catalog L1 Caffeine.
3. Redis Cluster deploy (replace single-node Redis already used by edge-service rate limiting — backward compatible with cluster client).
4. Order-service two-tier cache with full AP fallback chain.
5. Inventory read cache (5 s TTL).
6. Search caches.
7. Edge `LocalResponseCache`.
8. Chaos tests: kill Redis shards, kill catalog, network-partition AZs — assert read SLO holds.

## 11. Tests to add

- `CacheFallthroughIT` per service: Testcontainers Redis + Postgres; stop Redis mid-test, assert reads still succeed from L1 / origin.
- `StaleOnErrorIT`: stop origin, assert cache serves stale value with correct header.
- `RedisClusterFailoverIT`: stop 1 shard, assert keys in other shards still served.
- `CacheStampedeIT`: 100 concurrent requests on cold cache → origin called ≤ pod-count times, not 100 times.
- Latency benchmark with JMeter: 10k RPS sustained, p99 must hold under chaos.

## 12. Why this is AP, not CP

Quick CAP check per cached read:

- **Consistency**: NO — different pods may return different values for up to 30 s after a write.
- **Availability**: YES — every request gets a response (possibly stale) as long as at least one of {L1, L2, origin} is reachable.
- **Partition tolerance**: YES — by construction; every tier has a fallback.

Writes (orders, inventory reservations) are still CP via Postgres + outbox — that's where money lives. **Cache is AP, saga is CP.** Two different problems, two different answers, both correct.
