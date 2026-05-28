# GEMINI.md (CAVEMAN COMPRESSED)

## Core Mandate
Senior Java Dev, Spring Boot + Spring Cloud microservices. Prioritize correctness and caution.
Reference style for refactoring: `order-service` (Hexagonal style: domain ➔ application ➔ adapter ➔ bootstrap).

---

## Services & Ports
* `config-service`: Config Server (`8888`). Serve shared configurations in `config/`.
* `catalog-service`: JDBC + Flyway (`9001`). DB: `jdbc:postgresql://localhost:5432/polardb_catalog`
* `order-service`: WebFlux + R2DBC + Flyway + jOOQ (`9002`). DB: `r2dbc:postgresql://localhost:5433/polardb_order`
* `inventory-service`: WebFlux + R2DBC + Flyway + jOOQ (`9004`). DB: `r2dbc:postgresql://localhost:5434/polardb_inventory`
* `edge-service`: Spring Cloud Gateway + Redis (`9000`)
* `dispatcher-service`: Stream processor (Kafka) (`9003`)
* `search-service`: WebFlux + Elasticsearch (`9005`)

---

## Operational Commands
```bash
# Build
./gradlew build
# Run
./gradlew bootRun
# Generate jOOQ (after migration)
./gradlew generateJooq
```
Root targets: `make build`, `make test`, `make run-<service>`, `make compose-up`, `make infra-up`.

---

## Working Rules
* State assumptions immediately. Ask if uncertain.
* If multiple interpretations: present all options, do NOT choose silently.
* Simplest implementation: no speculative code, no unnecessary abstraction.
* Narrow, surgical changes. Limit touched files to task.
* Run tests and formatting before committing.

---

## Architecture Conventions
* **Domain**: clean Java types, business invariants, no frameworks.
* **Application**: ports, use cases, commands/queries.
* **Adapter**: inbound (REST Web controllers, DTOs) and outbound (jOOQ/JDBC repos, HTTP clients).
* **Bootstrap**: Spring config, dependency injection wiring.
* Restrict DTO/repository models within adapters; do not leak to domain.
* Jakarta Bean Validation on REST request DTOs.
* Flyway migrations: `V<next>__desc.sql` only. Do NOT edit history.

---

## Testing Strategy
* Test pyramid: Domain Unit ➔ App Service (mocked ports) ➔ Adapter Integration ➔ Web Slice ➔ E2E.
* Deterministic, isolated by profile. Testcontainers need running Docker.
* Run test: `./gradlew test --tests 'package.ClassName[.method]'` from service directory.
* Write failing test reproducing bugs before fixing prod code.

---

## Formatting (Spotless)
* Applied automatically. Check: `./gradlew spotlessCheck` | Fix: `./gradlew spotlessApply`.
* Matches `spotless { java { googleJavaFormat('1.17.0').aosp() } }`.
* Excludes generated jOOQ folder `src/main/generated-jooq/`.

---

## MCP Tools Configured
Use MCP servers for developer workflows:
* **codegraph**: structural analysis (context, trace, explore, impact). Must check impact before edit.
* **code-review-graph (CRG)**: persistent codebase SQLite relationships. `uvx code-review-graph serve`.
* **claude-mem**: session long-term memory. `node /Users/locpham/.claude/plugins/marketplaces/thedotmack/plugin/scripts/mcp-server.cjs`.
* **java-lsp**: editor-like Java navigation, refactoring, hover. Expects `jls` on `PATH`.
* **java-app-modernization**: Spring Boot/Java upgrades. `APPMOD_MCP_COLLECT_TELEMETRY=false`.
* **bookstore-postgres-catalog / -order / -inventory**: restricted read-only SQL inspection.
* **bookstore-redis**: Redis CLI tool calls.
* **bookstore-keycloak**: Keycloak client.
* **bookstore-kafka**: Kafka topics / broker inspector.
* **bookstore-elasticsearch**: Elasticsearch ES|QL search tool calls.

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
