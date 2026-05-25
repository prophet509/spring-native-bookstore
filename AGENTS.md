# AGENTS

## Mission
Build and operate 7 Spring Boot services with deterministic local and CI behavior. Favor correctness, incremental changes, and centralized config over shortcuts.

## Services (all Spring Boot 4.0.3, Java 21, Gradle toolchains)
| Service | Port | Tech | Notes |
|---------|------|------|-------|
| `config-service` | 8888 | Spring Cloud Config Server | Serves `config/*.yml`; native profile |
| `edge-service` | 9000 | Spring Cloud Gateway + Redis | Routes, rate limiting, circuit breaker, OAuth2 client |
| `catalog-service` | 9001 | Spring MVC + Data JDBC + Flyway | PostgreSQL `5432/polardb_catalog` |
| `order-service` | 9002 | Spring WebFlux + Data R2DBC + Flyway + jOOQ | PostgreSQL `5433/polardb_order` |
| `dispatcher-service` | 9003 | Spring Cloud Function + Stream (Kafka) | No DB; pure event consumer/producer |
| `inventory-service` | 9004 | Spring WebFlux + Data R2DBC + Flyway + jOOQ | PostgreSQL `5434/polardb_inventory` |
| `search-service` | 9005 | Spring WebFlux + Data Elasticsearch | Kafka event consumer; SB `4.0.6`, SC `2025.1.1` |

- No monorepo root — each service has its own `./gradlew` in its directory.
- Most services follow Hexagonal Architecture: `adapter/` (in/out), `application/` (port/service), `domain/`.
- `gradle/observability.gradle` shared by all services (sets `otelLogbackAppenderVersion`).
- Config Server serves `config/` directory; services import config from `http://localhost:8888`.
- Prefer editing `config/*.yml` for shared runtime behavior; never hardcode service-local values that change across environments.

## Infrastructure
All infra via Docker Compose (`polar-deployment/docker/docker-compose.yml`) or K8s manifests (`polar-deployment/kubernetes/local/`):
- Kafka (port `9092`), PostgreSQL per service, Keycloak (`8080`), Redis (`6379`)
- Observability: Prometheus, Grafana, Tempo, Loki, Fluent Bit
- Tilt at root delegates to `polar-deployment/kubernetes/local/Tiltfile`

## Commands
```bash
make build                  # All 7
make test                   # All 7
make build-<service>        # make build-catalog
make test-<service>         # make test-order
make run-<service>          # make run-config (starts config-service first)
make infra-up               # Docker Compose infra (Kafka, Postgres, Keycloak, etc.)
make compose-up             # infra + services + frontend
make cluster-create         # kind cluster + ingress-nginx; use `platform-up` for backing services
```
All make targets for `config|catalog|order|edge|inventory|dispatcher|search`.

## Run Order
1. `make infra-up` (or `compose-up` to also start app containers)
2. `make run-config` (Config Server must be available before other services)
3. `make run-edge` `make run-catalog` `make run-order` `make run-dispatcher` `make run-inventory` `make run-search`

## Testing
- JUnit 5 with Spring test slices preferred over `@SpringBootTest`.
- Tests using Testcontainers require Docker. Affected services: catalog, order, inventory (Postgres + Kafka), search (Elasticsearch + Kafka), edge (Keycloak).
- Single test: `cd <service> && ./gradlew test --tests 'com.locpham.bookstore.<service>.<TestClass>[.<method>]'`
- Order-service and inventory-service set `systemProperty 'user.timezone', 'UTC'` in test task.

## Formatting (Spotless)
**All 7 services** use `spotless { java { googleJavaFormat('1.17.0').aosp() } }`:
```bash
make spotless-apply   # or: cd <service> && ./gradlew spotlessApply
make spotless         # or: cd <service> && ./gradlew spotlessCheck
```
order-service and inventory-service exclude `src/main/generated-jooq/`.

## jOOQ Codegen
order-service and inventory-service use jOOQ + Flyway. After changing a Flyway migration:
```bash
cd order-service && ./gradlew generateJooq    # depends on flywayMigrate
cd inventory-service && ./gradlew generateJooq
```
Generated sources go to `src/main/generated-jooq/`. Committed to repo. Re-run after migration changes or schema updates.

## CI/CD
- `.github/workflows/` has 7 per-service pipelines + `ci-bookstore.yml` (aggregator on `main` pushes).
- Each pipeline: `build` (compile+test) → `package` (`bootBuildImage --publishImage` with registry credentials).
- Image name pattern: `pxloc97/<service-name>:0.0.1-SNAPSHOT`.

## Key Quirks
- search-service uses Spring Boot `4.0.6` / Spring Cloud `2025.1.1` (others: `4.0.3` / `2025.1.0`).
- Never edit historical Flyway migrations (`V<next>__description.sql` only).
- Never commit `.env*` files or real secrets.
- GitNexus MCP usage guide is in `CLAUDE.md`.
