# AGENTS.md (Unified)

Single source of truth for AI coding assistants. Tool-specific wrappers (`CLAUDE.md`, `GEMINI.md`) only contain reminders that do not exist here.

---

## Project Overview

Spring Boot 4.0.3, Java 21, Gradle, Hexagonal architecture.

| Service | Port | Tech Stack | Notes |
|---------|------|------------|-------|
| `config-service` | 8888 | Spring Cloud Config | Serves `config/`; native profile |
| `edge-service` | 9000 | Gateway + Redis | Circuit breaker, OAuth2 client |
| `catalog-service` | 9001 | MVC + Data JDBC + Flyway | DB: `5432/polardb_catalog` |
| `order-service` | 9002 | WebFlux + R2DBC + Flyway + jOOQ | DB: `5433/polardb_order` |
| `dispatcher-service` | 9003 | Stream (Kafka) | Pure event consumer/producer |
| `inventory-service` | 9004 | WebFlux + R2DBC + Flyway + jOOQ | DB: `5434/polardb_inventory` |
| `search-service` | 9005 | WebFlux + Data ES | Kafka; SB `4.0.6`, SC `2025.1.1` |

* No monorepo root. Each service has independent `./gradlew`.
* Config server endpoint: `http://localhost:8888`. Edit `config/*.yml` for shared runtime settings; no local hardcode.
* Shared scripts: `gradle/java-base.gradle`, `spotless.gradle`, `boot-image.gradle`, `observability.gradle`, `jooq.gradle`.

---

## Architecture Conventions

* Hexagonal layout: `domain/` → `application/` (ports/use cases) → `adapter/` (in/out) → `bootstrap/` (Spring wiring).
* Domain: clean Java types, business invariants, no framework dependencies.
* Application: ports, use cases, commands/queries.
* Adapter: inbound REST controllers + DTOs; outbound repositories, HTTP clients, Kafka bindings.
* Restrict DTO/repository models within adapters; do not leak to domain.
* Jakarta Bean Validation on REST request DTOs.

---

## Operational Commands

* Build all: `make build` | Build one: `make build-<service>`
* Test all: `make test` | Test one: `make test-<service>`
* Spotless: `make spotless-apply` (apply) or `make spotless` (check)
* Run infra: `make infra-up`
* Run all + UI: `make compose-up`
* Run service: `make run-<service>` (starts config server first)
* Kind cluster: `make cluster-create`
* Generate jOOQ after migration changes: `./gradlew generateJooq`

### Tracing Profile (PROD)

* Default: default profile (no trace, Keycloak `localhost:8080`)
* Prod (Tempo/Grafana Alloy trace `localhost:4318`, Keycloak `polar-keycloak:8080`):
  `make run-<service> PROFILE=prod`

---

## Development Standards

* **jOOQ**: run `./gradlew generateJooq` after Flyway migration changes. Committed to `src/main/generated-jooq/`.
* **Testing**: prefer JUnit 5 Spring test slices over `@SpringBootTest`. Testcontainers need Docker.
* **Flyway**: NEVER edit historical migrations (`V<next>__description.sql` only).
* **Secrets**: Never commit `.env*` or hardcoded credentials. Use env variables.

---

## Testing Strategy

* Test pyramid: Domain Unit → App Service (mocked ports) → Adapter Integration → Web Slice → E2E.
* Deterministic, isolated by profile. Testcontainers need running Docker.
* Run targeted tests: `./gradlew test --tests 'package.ClassName[.method]'` from service directory.
* Write failing test reproducing bugs before fixing prod code.
* Run tests and formatting before committing.

---

## Formatting (Spotless)

* Applied automatically. Check: `./gradlew spotlessCheck` | Fix: `./gradlew spotlessApply`.
* Matches `spotless { java { googleJavaFormat('1.17.0').aosp() } }`.
* Excludes generated jOOQ folder `src/main/generated-jooq/`.

---

## Infrastructure

All services run via Docker Compose (`polar-deployment/docker/docker-compose.yml`) or local K8s (`polar-deployment/kubernetes/local/`):

* Kafka (`9092`), Postgres, Keycloak (`8080`), Redis (`6379`), ES (`9200`).
* Observability: Prometheus, Grafana, Tempo, Loki, Fluent Bit.
* Tilt root delegates to `polar-deployment/kubernetes/local/Tiltfile`.

---

## MCP Setup

Templates: `mcp/`

* Claude Desktop: `mcp/claude_desktop_config.json`
* Windsurf: `~/.codeium/windsurf/mcp_config.json` (template: `mcp/windsurf_mcp_config.json`)
* OpenCode: `opencode.json` (template: `mcp/opencode.json`)
* Claude Code: `.mcp.json`
* VS Code / Cursor: `.vscode/mcp.json`
* Codex: `~/.codex/config.toml`

Start platform first: `make infra-up`.

### Active MCP Servers & Commands

1. **codegraph**: serve codebase graph. Command: `codegraph serve --mcp`
2. **code-review-graph**: persistent codebase relationships, blast radius. Command: `uvx code-review-graph serve`
3. **claude-mem**: persistent session memory. Command: `node /Users/locpham/.claude/plugins/marketplaces/thedotmack/plugin/scripts/mcp-server.cjs`
4. **java-lsp**: semantic Java navigation. Command: `npx -y lsp-mcp-server`. *Needs `jls` on PATH, config `mcp/lsp-mcp.json`*
5. **java-app-modernization**: upgrade analysis. Command: `npx -y @microsoft/github-copilot-app-modernization-mcp-server@latest` (env `APPMOD_MCP_COLLECT_TELEMETRY=false`)
6. **bookstore-postgres-catalog**: DB. Command: `uvx postgres-mcp --access-mode=restricted` (env `DATABASE_URI=postgresql://user:password@localhost:5432/polardb_catalog`)
7. **bookstore-postgres-order**: DB. Command: `uvx postgres-mcp --access-mode=restricted` (env `DATABASE_URI=postgresql://user:password@localhost:5433/polardb_order`)
8. **bookstore-postgres-inventory**: DB. Command: `uvx postgres-mcp --access-mode=restricted` (env `DATABASE_URI=postgresql://user:password@localhost:5434/polardb_inventory`)
9. **bookstore-redis**: Cache. Command: `uvx --from redis-mcp-server@latest redis-mcp-server --url redis://localhost:6379/0`
10. **bookstore-keycloak**: Auth. Command: `npx -y keycloak-model-context-protocol` (env `KEYCLOAK_URL=http://localhost:8080`, `KEYCLOAK_ADMIN=user`, `KEYCLOAK_ADMIN_PASSWORD=password`)
11. **bookstore-kafka**: Stream. Command: `uvx mcp-kafka` (env `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`)
12. **bookstore-elasticsearch**: Search. Command: `uvx elasticsearch-mcp-server-es9` (env `ELASTICSEARCH_HOSTS=http://localhost:9200`)

### Code Intelligence Rules

* Prefer **codegraph** for code questions.
* Prefer matching **DB server** for persistence questions.
* Prefer **kafka** server for event-flow questions.
* Fall back to raw files only when the above are unavailable.

### CodeGraph / Code-Review-Graph Rules

* **Before editing symbol**: MUST run `codegraph_impact` or CRG `detect-changes` check. Report blast radius (callers, callees, risk level).
* **If risk HIGH / CRITICAL**: MUST warn user first.
* **Symbol path between X & Y**: Use `codegraph_trace`.
* **Survey multiple symbols**: Use `codegraph_explore`.
* **Search symbol name**: Use `codegraph_search`.
* **Map task area**: Use `codegraph_context`.
* **Find affected test files**: run `git diff --name-only | codegraph affected --stdin`.
* **Index missing**: run `codegraph init && codegraph index`. Run `uvx code-review-graph build` to re-index CRG SQLite graph database.
* **No Find-and-Replace**: use `java-lsp` rename or IDE refactoring.

---

## RTK (Rust Token Killer)

Token-optimized CLI proxy for shell commands.

* Always prefix shell commands with `rtk`. Saves 60-99% tokens.
* E.g. `rtk git status`, `rtk git diff`, `rtk cargo test`, `rtk npm run <script>`.
* Works with `&&`: `rtk git add . && rtk git commit -m "msg" && rtk git push`.
* Check savings: `rtk gain`. View history: `rtk gain --history`.
* Setup global hook: `rtk init -g`. Local setup: `rtk init`.

---

## Claude-Mem Tools

Use long-term session memory tools for query:

* `search`: semantic search over memories
* `timeline`: session events chronological
* `get_observations`: list captured knowledge
* `smart_search` / `smart_outline` / `smart_unfold`: context-aware navigation
* `build_corpus` / `query_corpus` / `prime_corpus`: corpus knowledge management

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
* Short over long: 200 lines → 50 lines. Overcomplicated? Simplify.

### 3. Surgical Edits

* Touch only what requested. No "improvement" of surrounding code.
* No unsolicited refactoring. Match existing style.
* Dead code found? Mention only. Do not delete.
* Remove unused imports/vars caused by YOUR edits.

### 4. Goal-Driven

* Plan: `[Step] → verify: [check]`.
* "Add validation" → write failing test, make pass.
* "Fix bug" → write reproducing test, make pass.
* "Refactor" → run tests before/after.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, invoke the `skill` tool with `skill: "graphify"` before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
