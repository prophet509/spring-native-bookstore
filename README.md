# Spring Native Bookstore

Spring Native Bookstore is a multi-service Spring Boot project with a central Config Server, an Edge Service gateway, and domain services for catalog and ordering.

## Services

- `config-service` (port `8888`): Spring Cloud Config Server serving shared config from `config/`
- `edge-service` (port `9000`): Spring Cloud Gateway for routing, resilience, and rate limiting
- `catalog-service` (port `9001`): Catalog REST API backed by PostgreSQL + Flyway + Spring Data JDBC
- `order-service` (port `9002`): Order API backed by PostgreSQL + Flyway + Spring Data R2DBC/WebFlux
- `inventory-service` (port `9004`): Inventory service for reserving/releasing stock, backed by PostgreSQL + Flyway + Spring Data R2DBC/WebFlux
- `dispatcher-service` (port `9003`): Dispatcher service consuming order events and producing dispatch events via Spring Cloud Stream (Kafka)

## Platform Dependencies

- Kafka (Apache Kafka): Spring Cloud Stream binder used by `order-service`, `inventory-service`, `dispatcher-service` (and Config bus if enabled)
- PostgreSQL (per service): separate databases for catalog/order/inventory
- Keycloak: OAuth2/OIDC identity provider (resource server integration in `edge-service`/`order-service`)
- Observability stack: Prometheus + Grafana + Tempo + Loki (logs shipped via Fluent Bit)

## Tech Stack

- Java 21 (Gradle toolchains)
- Spring Boot `4.0.3`
- Spring Cloud `2025.1.0`
- PostgreSQL `14.12`
- Redis `7.2`
- Testcontainers for integration tests

## Repository Layout

```text
.
|- catalog-service/
|- dispatcher-service/
|- edge-service/
|- config-service/
|- inventory-service/
|- order-service/
|- config/
|- specs/
`- polar-deployment/
```

## Prerequisites

- JDK 21
- Docker (for PostgreSQL and Testcontainers-backed tests)
- GNU Make
- kind (for local Kubernetes cluster)
- `kubectl` (for Kubernetes targets)
- `skaffold` (optional, for Skaffold targets)

## Quick Start

1. Run Config Server:

   ```bash
   make run-config
   ```

2. In separate terminals, run app services:

   ```bash
   make run-catalog
   make run-order
   make run-inventory
   make run-dispatcher
   make run-edge
   ```

3. Test through edge-service:

   ```bash
   curl http://localhost:9000/books
   curl http://localhost:9000/orders
   ```

## Common Commands

Use `make help` to list all available targets.

```bash
make build            # Build all services
make test             # Run all tests
make clean            # Clean all build outputs
make cluster-create   # Create kind cluster + install ingress-nginx
make cluster-delete   # Delete kind cluster
make platform-up      # Deploy local Postgres + Redis manifests
make platform-down    # Remove local Postgres + Redis manifests
make edge-up          # Apply edge-service Deployment/Service/Ingress
make edge-down        # Delete edge-service Deployment/Service/Ingress
make k8s-status       # Show deployments/services/ingress/pods
make skaffold-run     # Build and deploy via Skaffold profile kind
make skaffold-dev     # Run Skaffold dev loop via profile kind
make skaffold-delete  # Remove Skaffold-managed resources
```

Use `clean` only when you actually need to discard Gradle outputs and caches for a service. Normal local work should prefer incremental builds:

```bash
cd order-service && ./gradlew build
cd order-service && ./gradlew test
cd order-service && ./gradlew generateJooq   # run after Flyway migration changes
```

## Service-Level Commands

Examples:

```bash
make build-catalog
make test-order
make run-config
make run-edge
```

Pattern targets are available for each service (`config`, `catalog`, `order`, `edge`):

```bash
make build-edge
make test-config
make clean-order
make run-catalog
```

Additional services also follow the same pattern:

```bash
make build-inventory
make test-dispatcher
make run-inventory
make run-dispatcher
```

## Local Kubernetes Flow

```bash
make cluster-create
make platform-up
make edge-up
```

Then add this host entry and access ingress:

```text
127.0.0.1 edge.bookstore.local
```

```bash
curl http://edge.bookstore.local/books
```

## API Endpoints

### catalog-service (`http://localhost:9001`)

- `GET /` - returns configured greeting
- `GET /books` - list all books
- `GET /books/{isbn}` - get one bookOld by ISBN
- `POST /books` - create a bookOld (returns `201`)
- `PUT /books/{isbn}` - update a bookOld
- `DELETE /books/{isbn}` - delete a bookOld (returns `204`)

Create a bookOld:

```bash
curl -X POST http://localhost:9001/books \
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

- `GET /orders` - list all orders
- `POST /orders` - submit an order

Submit an order:

```bash
curl -X POST http://localhost:9002/orders \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"1234567890","quantity":1}'
```

### edge-service (`http://localhost:9000`)

- `GET /books` and `GET /books/**` - routed to catalog-service
- `GET /orders` and `POST /orders` - routed to order-service
- `GET /catalog-fallback` - circuit breaker fallback endpoint

Examples:

```bash
curl http://localhost:9000/books
curl -X POST http://localhost:9000/orders \
  -H 'Content-Type: application/json' \
  -d '{"isbn":"1234567890","quantity":1}'
```

### config-service (`http://localhost:8888`)

- `GET /{application}/{profile}` - fetch app config (for example `catalog-service/default`)
- `GET /{application}/{profile}/{label}` - fetch app config from a specific Git label/branch

Example:

```bash
curl http://localhost:8888/catalog-service/default
```

## Kubernetes and Skaffold

- kind cluster config: `polar-deployment/kubernetes/local/kind-config.yml`
- Local platform manifests: `polar-deployment/kubernetes/local/postgresql.yml`, `polar-deployment/kubernetes/local/postgresql-order.yml`, `polar-deployment/kubernetes/local/postgresql-inventory.yml`, `polar-deployment/kubernetes/local/redis.yml`, `polar-deployment/kubernetes/local/kafka.yml`
- Edge Kubernetes manifests (same layout as other services): `edge-service/k8s/deployment.yml`, `edge-service/k8s/service.yml`, `edge-service/k8s/ingress.yml`
- Root Skaffold config: `skaffold.yml` (kind profile targets `kind-bookstore`)

## Local Ports (Defaults)

Services:

- config-service: `8888`
- edge-service: `9000`
- catalog-service: `9001`
- order-service: `9002`
- dispatcher-service: `9003`
- inventory-service: `9004`

Dependencies (Docker Compose defaults under `polar-deployment/docker/docker-compose.yml`):

- Kafka: `9092`
- PostgreSQL: catalog `5432`, order `5433`, inventory `5434`
- Keycloak: `8080`
- Grafana: `3000`
- Prometheus: `9090`
- Loki: `3100`
- Tempo (OTLP gRPC): `4317`
- Fluent Bit: `24224`

## MCP (Model Context Protocol)

This repo ships a shared set of 12 MCP servers so AI coding assistants can inspect the codebase
and the running platform (Postgres, Redis, Kafka, Elasticsearch, Keycloak). All supported CLIs are
configured with the same servers.

| CLI | Config file | Pre-configured |
|-----|-------------|----------------|
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

## CI/CD

GitHub Actions workflow definitions are in `.github/workflows/`:

- `ci-config-pipeline.yml`
- `ci-catalog-pipeline.yml`
- `ci-order-pipeline.yml`
- `ci-edge-pipeline.yml`
- `ci-bookstore.yml` (aggregates the service pipelines)

## Notes

- There is no monorepo Gradle root build; each service uses its own `./gradlew`.
- `config-service` points to this repository's `config/` directory through Spring Cloud Config.
- Keep config changes in `config/*.yml` for shared runtime configuration.
- Edge service fetches runtime configuration from Config Server and expects `edge-service.yml` and `edge-service-prod.yml` in the config source.
