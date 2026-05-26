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
- `gradle/` shared scripts: `java-base.gradle` (all), `spotless.gradle` (all), `boot-image.gradle` (all), `observability.gradle` (all), `jooq.gradle` (order + inventory).
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

### Local tracing mode
To run a service with OTEL tracing (sends traces to Tempo via Grafana Alloy on `localhost:4318`),
use the `PROFILE=prod` flag:

```bash
make run-order PROFILE=prod
make run-catalog PROFILE=prod
```

Without the flag, services run with default profile (no tracing):

```bash
make run-order    # default (no tracing)
make run-catalog  # default (no tracing)
```

The shared OTEL config lives in `config/application.yml` — all services that import from Config
Server get it automatically. Use `KEYCLOAK_URL` env var (defaults vary by profile) to toggle the
Keycloak endpoint:

| Profile | Default `KEYCLOAK_URL` | Resolves to |
|---------|------------------------|-------------|
| default | `http://localhost:8080` | keycloak on host |
| prod    | `http://polar-keycloak:8080` | keycloak via Docker DNS |

All prod configs (`config/*-prod.yml`) use `KEYCLOAK_URL` consistently — no hardcoded hostnames.

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
- CodeGraph MCP usage guide is in `CLAUDE.md`.

<!-- CODEGRAPH_START -->
## CodeGraph

This project has a CodeGraph MCP server (`codegraph_*` tools) configured. CodeGraph is a tree-sitter-parsed knowledge graph of every symbol, edge, and file. Reads are sub-millisecond and return structural information grep cannot.

### When to prefer codegraph over native search

Use codegraph for **structural** questions — what calls what, what would break, where is X defined, what is X's signature. Use native grep/read only for **literal text** queries (string contents, comments, log messages) or after you already have a specific file open.

| Question | Tool |
|---|---|
| "Where is X defined?" / "Find symbol named X" | `codegraph_search` |
| "What calls function Y?" | `codegraph_callers` |
| "What does Y call?" | `codegraph_callees` |
| "How does X reach/become Y? / trace the flow from X to Y" | `codegraph_trace` (one call = the whole path, incl. callback/React/JSX dynamic hops) |
| "What would break if I changed Z?" | `codegraph_impact` |
| "Show me Y's signature / source / docstring" | `codegraph_node` |
| "Give me focused context for a task/area" | `codegraph_context` |
| "See several related symbols' source at once" | `codegraph_explore` |
| "What files exist under path/" | `codegraph_files` |
| "Is the index healthy?" | `codegraph_status` |

### Rules of thumb

- **Answer directly — don't delegate exploration.** For "how does X work" / architecture questions, answer with 2-3 codegraph calls: `codegraph_context` first, then ONE `codegraph_explore` for the source of the symbols it surfaces. For a specific **flow** ("how does X reach Y") start with `codegraph_trace` from→to — one call returns the whole path with dynamic hops bridged — then ONE `codegraph_explore` for the bodies; don't rebuild the path with `codegraph_search` + `codegraph_callers`. Codegraph IS the pre-built index, so spawning a separate file-reading sub-task/agent — or running a grep + read loop — repeats work codegraph already did and costs more for the same answer.
- **Trust codegraph results.** They come from a full AST parse. Do NOT re-verify them with grep — that's slower, less accurate, and wastes context.
- **Don't grep first** when looking up a symbol by name. `codegraph_search` is faster and returns kind + location + signature in one call.
- **Don't chain `codegraph_search` + `codegraph_node`** when you just want context — `codegraph_context` is one call.
- **Don't loop `codegraph_node` over many symbols** — one `codegraph_explore` call returns several symbols' source grouped in a single capped call, while each separate node/Read call re-reads the whole context and costs far more.
- **Index lag**: the file watcher debounces ~500ms behind writes; don't re-query immediately after editing a file in the same turn.

### If `.codegraph/` doesn't exist

The MCP server returns "not initialized." Ask the user: *"I notice this project doesn't have CodeGraph initialized. Want me to run `codegraph init -i` to build the index?"*
<!-- CODEGRAPH_END -->
