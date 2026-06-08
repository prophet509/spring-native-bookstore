# Spring Native Bookstore

Multi-service Spring Boot bookstore platform — Spring Cloud Config, an OAuth2 hybrid edge gateway,
seven domain services, Kafka-backed messaging, Postgres + Debezium transactional outbox, and a full
Grafana/Tempo/Loki/Mimir observability stack.

## Services

| Service | Port | Stack | Notes |
|---|---|---|---|
| `config-service` | 8888 | Spring Cloud Config Server | Serves `config/`; native profile |
| `edge-service` | 9000 | Spring Cloud Gateway + Redis session | OAuth2 client (browser) **and** OAuth2 resource server (Bearer); circuit breakers, rate-limit, TokenRelay |
| `catalog-service` | 9001 | Spring MVC + Data JDBC + Flyway | DB `5432/polardb_catalog`; outbox publisher |
| `order-service` | 9002 | WebFlux + R2DBC + Flyway + jOOQ | DB `5433/polardb_order`; outbox publisher |
| `inventory-service` | 9004 | WebFlux + R2DBC + Flyway + jOOQ | DB `5434/polardb_inventory`; saga consumer + idempotency (Redis + DB) |
| `dispatcher-service` | 9003 | Spring Cloud Stream (Kafka) | Pure event consumer/producer |
| `search-service` | 9005 | WebFlux + Spring Data Elasticsearch | Spring Boot 4.0.6, Spring Cloud 2025.1.1 |

Architecture: Hexagonal — `adapter/` (in/out) → `application/` (ports) → `domain/`.
There is **no monorepo Gradle root**; each service has its own `./gradlew`.

## Platform Dependencies

- **Apache Kafka** (broker + Kafka Connect with Debezium) — outbox-based event publishing
- **PostgreSQL** — separate DB per service (catalog/order/inventory) with logical replication slots
- **Redis** — edge session store, edge rate-limiter buckets, inventory idempotency keys
- **Elasticsearch** — search-service index
- **Keycloak** — OAuth2/OIDC identity provider (realm `PolarBookshop`)
- **Observability stack**:
  - Prometheus / Mimir (metrics)
  - Grafana (dashboards)
  - Tempo (traces, OTLP)
  - Loki (logs)
  - Grafana Alloy (OTLP collector for traces + logs)

## Tech Stack

- Java 21 (Gradle toolchains)
- Spring Boot `4.0.3` (search-service: `4.0.6`)
- Spring Cloud `2025.1.0` (search-service: `2025.1.1`)
- PostgreSQL `14.12`
- Redis `7.2`
- Apache Kafka `4.2.0`
- Testcontainers for integration tests

## Repository Layout

```text
.
├── config-service/         Spring Cloud Config Server
├── edge-service/           Gateway (OAuth2 hybrid)
├── catalog-service/        Catalog domain
├── order-service/          Orders + saga starter
├── inventory-service/      Inventory + reservation saga
├── dispatcher-service/     Pure event service
├── search-service/         Elasticsearch read model
│
├── config/                 Shared runtime config served by config-service
├── polar-deployment/       Docker Compose, Postgres init, Debezium connectors, k8s manifests
├── gradle/                 Shared Gradle scripts (java-base, jooq, observability, spotless, ...)
├── scripts/                Stress / load-test scripts
├── bruno/                  Bruno API collections (Keycloak, Catalog, Orders, Inventory, Search, Edge)
├── mcp/                    MCP server config templates (Codex, Claude Desktop, Windsurf)
│
└── docs/
    ├── api-error-catalog.md
    ├── observability.md
    ├── tasks/              Engineering plans (saga-outbox, security-devops, roadmap, ...)
    ├── diagram/            Mermaid system + flow diagrams
    └── archive/            Superseded plans + original AI agent docs
```

## Prerequisites

- JDK 21
- Docker (for Postgres / Kafka / Testcontainers)
- GNU Make
- `kind` + `kubectl` (for the local Kubernetes flow)
- `skaffold` (optional)
- `jq` (used by load-test scripts)

## Quick Start

Bring infra up first, then config-service, then the rest of the services:

```bash
make infra-up           # Postgres x3, Kafka, Kafka-Connect, Redis, ES, Keycloak, observability
make services-up        # config + edge + catalog + order + inventory + dispatcher + search
```

Or one-shot everything (infra + services + frontend):

```bash
make compose-up
```

Smoke test through the edge gateway:

```bash
# Public catalog read (no auth)
curl http://localhost:9000/books

# Authenticated POST /orders requires a JWT issued by Keycloak (see "Security & Auth" below)
TOKEN=$(docker run --rm --network polar-network curlimages/curl:latest \
  -sf -X POST "http://polar-keycloak:8080/realms/PolarBookshop/protocol/openid-connect/token" \
  -d "grant_type=password" -d "client_id=edge-service" \
  -d "username=bjorn" -d "password=bjorn" -d "scope=openid roles" | jq -r '.access_token')

curl -X POST http://localhost:9000/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"9781617296956","quantity":1}'
```

## Common Commands

`make help` lists every target. The most-used ones:

```bash
make build              # Build all services
make test               # Run all tests
make spotless-apply     # Apply Spotless formatting
make infra-up           # Compose up infra only
make infra-down         # Stop infra
make services-up        # Compose up the seven app services
make services-down      # Stop app services
make compose-up         # Bring up frontend + services + infra
make compose-down       # Tear down everything
make outbox-cleanup     # Run outbox-event retention SQL across all 3 DBs

# Build / test / clean / run one service:
make build-<svc>        # e.g. make build-order
make test-<svc>
make clean-<svc>
make run-<svc>          # runs config-service first, then the target service
make run-<svc> PROFILE=prod   # prod profile (Tempo, Keycloak via container hostname)
```

Use `clean` only when you actually need to discard Gradle caches — prefer incremental builds:

```bash
cd order-service && ./gradlew build
cd order-service && ./gradlew test
cd order-service && ./gradlew generateJooq   # after Flyway migration changes
```

## Security & Auth

Keycloak realm `PolarBookshop` ships two test users:

| User | Password | Roles |
|---|---|---|
| `bjorn` | `bjorn` | `employee` |
| `isabelle` | `isabelle` | `customer` |

`edge-service` is an **OAuth2 hybrid gateway** with two filter chains:

1. **Browser flow** — `oauth2Login` (authorization code), session in Redis, CSRF protected, used by
   the SPA.
2. **API flow** — Resource Server with JWT validation; matches any request whose `Authorization`
   header starts with `Bearer `, maps the realm `roles` claim to `ROLE_*`, principal name comes
   from `preferred_username` (so the per-user `RequestRateLimiter` still buckets correctly), CSRF
   disabled (stateless).

Authorization rules apply uniformly across both chains:

- `GET /books/**`, `GET /search/**` — public
- `POST/PUT/DELETE /books/**` — `ROLE_employee`
- `POST /orders/**` — `ROLE_customer` or `ROLE_employee`
- `GET /orders/**` — authenticated

The downstream services validate the same JWT issuer
(`http://polar-keycloak:8080/realms/PolarBookshop`). For load-test or in-network scripts, fetch the
token via `curlimages/curl` running inside the Compose network so the `iss` claim matches what the
resource servers expect — see `scripts/load-test-orders-edge.sh` for the canonical pattern.

## Outbox + Saga

Order placement uses a **transactional outbox** with Debezium Postgres connectors:

```
POST /orders → orders + outbox_event (same TX)
                      │
                      ▼ Debezium Postgres CDC (logical replication)
                Kafka topic: order-created-events
                      │
                      ▼ inventory-service consumer
                inventory + reservation + outbox_event (same TX)
                      │
                      ▼ Debezium → inventory-events → order-service
                order status update (RESERVED / REJECTED)
```

Connectors are registered in `polar-deployment/docker/connect/` and auto-register on first
`make infra-up`. The connector configs use `slot.name=<svc>_outbox_slot`, so each service has its
own logical replication slot. The outbox row carries a `trace_id` column populated from the active
W3C `traceparent` header; the Outbox SMT writes it to a Kafka header so consumers can stitch
traces.

Run retention manually any time:

```bash
make outbox-cleanup     # DELETE FROM outbox_event WHERE created_at < now() - interval '7 days'
```

## Load & Stress Testing

`scripts/` contains three load tools:

```bash
# Hammer order-service directly (no gateway, no auth filter chain)
./scripts/load-test-orders.sh 1000 50

# Hammer through the edge gateway with Bearer auth — same code path browsers will use
./scripts/load-test-orders-edge.sh 1000 50

# k6 scenario for sustained millions-of-requests runs
k6 run scripts/load-test-k6.js
```

The edge script fetches the token in-network so `iss` resolves correctly, asserts
`availableQuantity >= 0` after the burst (no oversell), and prints HTTP code distribution + outbox
row delta + RPS. With the default `RequestRateLimiter` (10 r/s replenish, 20 burst) and
`orderCircuitBreaker` (sliding-window 20 / 50% failure threshold), expect 503s when concurrency
exceeds backend capacity — that is the gateway protecting the saga, not a defect.

## Local Ports

App services:

- `config-service` 8888
- `edge-service` 9000
- `catalog-service` 9001
- `order-service` 9002
- `dispatcher-service` 9003
- `inventory-service` 9004
- `search-service` 9005

Infra (Docker Compose under `polar-deployment/docker/docker-compose.yml`):

- Kafka: `9092` (host) / `19092` (in-network advertised)
- Kafka Connect REST: `8083`
- Postgres: catalog `5432`, order `5433`, inventory `5434`
- Redis: `6379`
- Elasticsearch: `9200`
- Keycloak: `8080`
- Grafana: `3000`
- Prometheus: `9090`
- Loki: `3100`
- Tempo (OTLP gRPC): `4317`
- Mimir: `9009`
- Grafana Alloy (OTLP HTTP): `4318`

## API Endpoints

### catalog-service (`http://localhost:9001`)

- `GET /` — configured greeting
- `GET /books` — list books
- `GET /books/{isbn}` — get one book
- `POST /books` — create a book (`201`) [requires `ROLE_employee`]
- `PUT /books/{isbn}` — update a book [requires `ROLE_employee`]
- `DELETE /books/{isbn}` — delete a book (`204`) [requires `ROLE_employee`]

```bash
curl -X POST http://localhost:9001/books \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "isbn": "1234567890",
    "title": "Cloud Native Spring in Action",
    "author": "Thomas Vitale",
    "price": 49.90,
    "publisher": "Manning"
  }'
```

### order-service (`http://localhost:9002`)

- `GET /orders` — list orders
- `POST /orders` — submit an order

### inventory-service (`http://localhost:9004`)

- `GET /inventory/{isbn}` — current inventory snapshot

### search-service (`http://localhost:9005`)

- `GET /search?q=...` — full-text search across the indexed catalog

### edge-service (`http://localhost:9000`)

Routes traffic to all of the above with circuit breakers and OAuth2:

- `/books`, `/books/**` → catalog
- `/orders`, `/orders/**` → order
- `/inventory`, `/inventory/**` → inventory
- `/search`, `/search/**` → search
- `/catalog-fallback`, `/order-fallback`, `/inventory-fallback`, `/search-fallback` — circuit
  breaker fallback endpoints

### config-service (`http://localhost:8888`)

```bash
curl http://localhost:8888/catalog-service/default
curl http://localhost:8888/{application}/{profile}/{label}
```

## Local Kubernetes Flow

```bash
make cluster-create     # kind cluster + ingress-nginx
make platform-up        # Postgres + Redis + Kafka manifests
make edge-up            # edge-service Deployment / Service / Ingress
```

Add the host entry and call ingress:

```text
127.0.0.1 edge.bookstore.local
```

```bash
curl http://edge.bookstore.local/books
```

Skaffold:

```bash
make skaffold-run       # Build + deploy via the kind profile
make skaffold-dev       # Live dev loop
make skaffold-delete    # Tear down skaffold-managed resources
```

## CI/CD

GitHub Actions workflows in `.github/workflows/`:

- `ci-config-pipeline.yml`
- `ci-catalog-pipeline.yml`
- `ci-order-pipeline.yml`
- `ci-inventory-pipeline.yml`
- `ci-dispatcher-pipeline.yml`
- `ci-edge-pipeline.yml`
- `ci-search-pipeline.yml`
- `ci-bookstore.yml` — aggregates the per-service pipelines

## MCP (Model Context Protocol)

The repo ships a shared set of 12 MCP servers so AI coding assistants can inspect the codebase and
the running platform (Postgres x3, Redis, Kafka, Elasticsearch, Keycloak). All supported CLIs are
configured with the same servers.

| CLI | Config file | Pre-configured |
|---|---|---|
| OpenCode | `opencode.json` | yes |
| Claude Code | `.mcp.json` | yes |
| Kiro | `.kiro/settings/mcp.json` | yes |
| Cursor | `.cursor/mcp.json` | yes |
| VS Code | `.vscode/mcp.json` | yes |
| Codex | `~/.codex/config.toml` | template: `mcp/codex_config.toml` |
| Claude Desktop | `~/Library/Application Support/Claude/claude_desktop_config.json` | template: `mcp/claude_desktop_config.json` |
| Windsurf | `~/.codeium/windsurf/mcp_config.json` | template: `mcp/windsurf_mcp_config.json` |

Start the platform first (`make infra-up`) so the database/broker servers can connect. See
[`mcp/README.md`](mcp/README.md) for the full server list and per-CLI setup steps.

## Documentation

Engineering plans, diagrams, and historical docs live under `docs/`:

- [`docs/api-error-catalog.md`](docs/api-error-catalog.md) — REST error code reference
- [`docs/observability.md`](docs/observability.md) — tracing/logging/metrics setup
- [`docs/tasks/`](docs/tasks/) — saga-outbox, security-devops, senior-roadmap, technology plans
- [`docs/diagram/`](docs/diagram/) — Mermaid system + flow diagrams (system architecture, order
  saga, inventory flow, catalog cache, edge routing, search)
- [`docs/archive/`](docs/archive/) — superseded plans + original agent docs

## Notes

- Each service uses its own `./gradlew`. There is no monorepo Gradle root build.
- `config-service` reads from this repo's `config/` directory through Spring Cloud Config.
- Keep shared runtime configuration in `config/*.yml`, not service-local YAML.
- `edge-service` fetches `edge-service.yml` and `edge-service-prod.yml` from the config source.
- After Flyway migrations on `order-service` / `inventory-service`, run `./gradlew generateJooq` to
  refresh `src/main/generated-jooq/`.
- Never edit historical migrations. Add new `V<next>__description.sql` only.
