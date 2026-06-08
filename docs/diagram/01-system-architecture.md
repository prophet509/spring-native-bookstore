# System Architecture

Spring Native Bookstore is a microservices system (Spring Boot 4.0.3, Java 21, hexagonal
architecture). Clients reach the system through the Edge gateway. Domain services communicate
synchronously over REST and asynchronously over Kafka. Each stateful service owns its data store.

```mermaid
graph TB
    client[Client / Browser]

    subgraph edge_layer[Edge Layer]
        edge["edge-service :9000<br/>Spring Cloud Gateway<br/>OAuth2 client, rate limit,<br/>circuit breaker, session"]
    end

    subgraph platform[Platform]
        config["config-service :8888<br/>Spring Cloud Config"]
        keycloak["Keycloak :8080<br/>OAuth2 / OIDC"]
        redis[("Redis :6379<br/>session, rate-limit,<br/>idempotency, cache")]
        kafka{{"Kafka :9092<br/>event bus"}}
    end

    subgraph domain[Domain Services]
        catalog["catalog-service :9001<br/>MVC + JDBC + Flyway<br/>Caffeine + Redis cache"]
        order["order-service :9002<br/>WebFlux + R2DBC + jOOQ"]
        inventory["inventory-service :9004<br/>WebFlux + R2DBC + jOOQ"]
        dispatcher["dispatcher-service :9003<br/>Kafka Streams (pack|label)"]
        search["search-service :9005<br/>WebFlux + Elasticsearch<br/>Caffeine cache"]
    end

    subgraph stores[Data Stores]
        pgc[("PostgreSQL<br/>polardb_catalog :5432")]
        pgo[("PostgreSQL<br/>polardb_order :5433")]
        pgi[("PostgreSQL<br/>polardb_inventory :5434")]
        es[("Elasticsearch :9200")]
    end

    client --> edge
    edge -->|TokenRelay| catalog
    edge -->|TokenRelay| order
    edge -->|TokenRelay| inventory
    edge -->|TokenRelay| search

    edge -.session/rate-limit.-> redis
    edge -.authenticate.-> keycloak

    catalog --> pgc
    order --> pgo
    inventory --> pgi
    search --> es

    catalog -.cache.-> redis
    order -.idempotency.-> redis
    inventory -.idempotency.-> redis
    search -.cache.-> redis

    order <-->|REST: load book| catalog

    catalog -->|book.created/updated/deleted| kafka
    order -->|order-created / order-cancelled| kafka
    inventory -->|inventory-events| kafka
    order -->|order-accepted| kafka
    dispatcher -->|order-dispatched| kafka

    kafka -->|order-created/cancelled| inventory
    kafka -->|inventory-events / order-dispatched| order
    kafka -->|order-accepted| dispatcher
    kafka -->|book.*| search
    kafka -->|book.*| order

    config -.serves config.-> catalog
    config -.serves config.-> order
    config -.serves config.-> inventory
    config -.serves config.-> dispatcher
    config -.serves config.-> search
    config -.serves config.-> edge

    keycloak -.JWT validation.-> catalog
    keycloak -.JWT validation.-> order
    keycloak -.JWT validation.-> inventory
    keycloak -.JWT validation.-> search
```

## Component responsibilities

| Service | Port | Role | Storage | Sync API | Async (Kafka) |
|---------|------|------|---------|----------|---------------|
| config-service | 8888 | Centralized config | Git/filesystem `config/` | serves config | — |
| edge-service | 9000 | API gateway, auth, resilience | Redis (session) | routes to all | config bus |
| catalog-service | 9001 | Book catalog CRUD | PostgreSQL + Caffeine/Redis | `/books` | produces `book.*` |
| order-service | 9002 | Order lifecycle / saga orchestration | PostgreSQL + Redis | `/orders` | produces order events, consumes inventory + dispatch + book events |
| inventory-service | 9004 | Stock reservation / release | PostgreSQL + Redis | `/inventory` | consumes order events, produces `inventory-events` |
| dispatcher-service | 9003 | Pack & label dispatch | — (stateless stream) | — | consumes `order-accepted`, produces `order-dispatched` |
| search-service | 9005 | Full-text book search | Elasticsearch + Caffeine | `/search` | consumes `book.*` |

## Cross-cutting concerns

- **Authentication**: Keycloak issues JWTs. `edge-service` is the OAuth2 client (authorization-code
  flow) and relays bearer tokens downstream (`TokenRelay`). Domain services act as resource servers
  validating the JWT.
- **Configuration**: All services pull configuration from `config-service` at startup.
- **Resilience** (edge): per-route circuit breakers with fallbacks, retries, and a Redis-backed
  rate limiter keyed by user.
- **Observability**: Actuator + Micrometer + OpenTelemetry export to the Prometheus/Grafana/Tempo/Loki
  stack.
