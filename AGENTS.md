# AGENTS.md (CAVEMAN COMPRESSED)

## Spring Microservices (Spring Boot 4.0.3, Java 21, Gradle, Hexagonal)
| Service | Port | Tech Stack | Notes |
|---------|------|------------|-------|
| `config-service` | 8888 | Spring Cloud Config | Serves `config/`; native profile |
| `edge-service` | 9000 | Gateway + Redis | Circuit breaker, OAuth2 client |
| `catalog-service` | 9001 | MVC + Data JDBC + Flyway | DB: `5432/polardb_catalog` |
| `order-service` | 9002 | WebFlux + R2DBC + Flyway + jOOQ | DB: `5433/polardb_order` |
| `dispatcher-service` | 9003 | Stream (Kafka) | Pure event consumer/producer |
| `inventory-service` | 9004 | WebFlux + R2DBC + Flyway + jOOQ | DB: `5434/polardb_inventory` |
| `search-service` | 9005 | WebFlux + Data ES | Kafka; SB `4.0.6`, SC `2025.1.1` |

* No monorepo root. Each has independent `./gradlew`.
* Hexagonal layout: `adapter/` (in/out) ➔ `application/` (ports) ➔ `domain/` (free rules).
* Config server endpoint: `http://localhost:8888`. Edit `config/*.yml` for shared runtime settings; no local hardcode.
* Shared scripts: `gradle/java-base.gradle`, `spotless.gradle`, `boot-image.gradle`, `observability.gradle`, `jooq.gradle`.

---

## Infrastructure
All services run via Docker Compose (`polar-deployment/docker/docker-compose.yml`) or local K8s (`polar-deployment/kubernetes/local/`):
* Kafka (`9092`), Postgres, Keycloak (`8080`), Redis (`6379`), ES (`9200`).
* Observability: Prometheus, Grafana, Tempo, Loki, Fluent Bit.
* Tilt root delegates to `polar-deployment/kubernetes/local/Tiltfile`.

---

## Operational Commands
* Build all: `make build` | Build one: `make build-<service>`
* Test all: `make test` | Test one: `make test-<service>`
* Spotless: `make spotless-apply` (apply) or `make spotless` (check)
* Run infra: `make infra-up`
* Run all + UI: `make compose-up`
* Run service: `make run-<service>` (starts config server first)
* Kind cluster: `make cluster-create`

### Tracing Profile (PROD)
* Default: default profile (no trace, Keycloak `localhost:8080`)
* Prod (Tempo/Grafana Alloy trace `localhost:4318`, Keycloak `polar-keycloak:8080`):
  `make run-<service> PROFILE=prod`

---

## Code Intelligence & Platform (MCP)
*12 servers, identical across all CLIs. Configs: `opencode.json`, `.mcp.json`, `.kiro/settings/mcp.json`, `.cursor/mcp.json`, `.vscode/mcp.json`; templates in `mcp/` (Codex/Claude Desktop/Windsurf). Setup: `mcp/README.md`. Start platform first: `make infra-up`.*

### Code intelligence
1. **codegraph**: Indexed structural relationships.
   * `codegraph_search` (find), `codegraph_callers` / `codegraph_callees` (flow)
   * `codegraph_trace` (path between nodes), `codegraph_impact` (impact before change)
   * `codegraph_explore` (source of multiple nodes)
   * Rule: No edit without `codegraph_impact` check first! Report blast radius.
2. **code-review-graph** (CRG): persistent code review graph. `uvx code-review-graph serve`. Rebuild: `uvx code-review-graph build`.
3. **claude-mem**: Session long-term memory. `node /Users/locpham/.claude/plugins/marketplaces/thedotmack/plugin/scripts/mcp-server.cjs`.
4. **java-lsp**: Java symbol nav, diagnostics, rename previews, hover/type, call/type hierarchy. Needs `jls` on PATH (`mcp/lsp-mcp.json`); else fall back to codegraph + Gradle.
5. **java-app-modernization**: Java/Spring upgrade & migration analysis only. Telemetry off (`APPMOD_MCP_COLLECT_TELEMETRY=false`).

### Platform inspection (need `make infra-up`)
6. **bookstore-postgres-catalog** (`:5432`), 7. **bookstore-postgres-order** (`:5433`), 8. **bookstore-postgres-inventory** (`:5434`): `postgres-mcp --access-mode=restricted` (read-only).
9. **bookstore-redis** (`:6379`): cache/session/idempotency inspection.
10. **bookstore-keycloak** (`:8080`): realms/clients/roles admin.
11. **bookstore-kafka** (`:9092`): topics, consume/produce, consumer groups (read-only by default).
12. **bookstore-elasticsearch** (`:9200`): indices, mappings, search, ES|QL.

* Rule: prefer codegraph for code, the matching DB server for persistence questions, kafka for event-flow, before reading raw files.

---

## Development Quirks & Standards
* **jOOQ**: run `./gradlew generateJooq` after Flyway migration changes. Committed to `src/main/generated-jooq/`.
* **Testing**: prefer JUnit 5 Spring test slices over `@SpringBootTest`. Testcontainers need Docker.
* **Flyway**: NEVER edit historical migrations (`V<next>__description.sql` only).
* **Secrets**: Never commit `.env*` or hardcoded credentials. Use env variables.

---

## LLM Coding Guidelines
*Bias caution over speed.*

### 1. Think First
* No assumptions. If confused, ask.
* Multiple options? Present all. No silent picking.
* Simpler way exists? Suggest. Push back.
* Unclear? Stop. Name confusion. Ask.

### 2. Simplicity First
* Minimum code. No speculative features.
* No single-use abstractions. No extra configurations.
* No dead-end error checks.
* Short over long: 200 lines ➔ 50 lines. Overcomplicated? Simplify.

### 3. Surgical Edits
* Touch only what requested. No "improvement" of surrounding code.
* No unsolicited refactoring. Match existing style.
* Dead code found? Mention only. Do not delete.
* Remove unused imports/vars caused by YOUR edits.

### 4. Goal-Driven
* Plan: `[Step] ➔ verify: [check]`.
* "Add validation" ➔ write failing test, make pass.
* "Fix bug" ➔ write reproducing test, make pass.
* "Refactor" ➔ run tests before/after.
