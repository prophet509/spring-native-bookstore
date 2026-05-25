# AGENTS

## Mission
- Build and operate Spring bookstore services (`catalog-service`, `order-service`, `config-service`) with deterministic local and CI behavior.
- Favor correctness, reproducibility, and incremental changes over clever shortcuts.
- Keep runtime config centralized through Config Server and `config/` to avoid drift.
- Run the smallest useful verification for every change and document any skipped checks.
- Keep this file current whenever toolchains, workflows, or service boundaries change.

## Repository Layout
- `catalog-service/`: REST catalog API, Spring MVC + Spring Data JDBC + Flyway.
- `order-service/`: reactive order API, Spring WebFlux + Spring Data R2DBC + Flyway.
- `config-service/`: Spring Cloud Config Server.
- `config/`: config data consumed by Config Server (per service/profile YAML files).
- `polar-deployment/docker/`: Docker Compose stack.
- `polar-deployment/kubernetes/local/`: local Kubernetes manifests (currently Postgres only).
- No monorepo Gradle root build; each service is a standalone Gradle project with its own `./gradlew`.

## Runtime and Configuration
- Service defaults: config-service `8888`, catalog `9001`, order `9002`.
- `catalog-service` and `order-service` import config from `http://localhost:8888`.
- Local DB defaults:
  - catalog: `jdbc:postgresql://localhost:5432/polardb_catalog`
  - order: `r2dbc:postgresql://localhost:5433/polardb_order`
- Prefer editing `config/*.yml` for shared runtime behavior instead of hardcoding service-local values.
- Never commit real secrets; use environment variables and keep `.env*` ignored.

## Toolchain and Versions
- Java 21 via Gradle toolchains.
- Spring Boot `4.0.3` across all services.
- Spring Cloud BOM `2025.1.0`.
- Testcontainers BOM `1.19.7` in services that run container-backed tests.
- Spotless `6.25.0` is enabled in `catalog-service` and `order-service`.

## Build and Run Commands
- Per-service builds:
  - `cd catalog-service && ./gradlew clean build`
  - `cd order-service && ./gradlew clean build`
  - `cd config-service && ./gradlew clean build`
- Per-service run:
  - `cd config-service && ./gradlew bootRun`
  - `cd catalog-service && ./gradlew bootRun`
  - `cd order-service && ./gradlew bootRun`
- Root Makefile shortcuts:
  - `make build`, `make test`, `make clean`
  - `make run-config`, `make run-catalog`, `make run-order`
  - `make infra-up`, `make infra-down`, `make compose-up`, `make compose-down`
  - `make k8s-up`, `make k8s-down`, `make k8s-status`
  - `make skaffold-dev|skaffold-run|skaffold-delete` (requires `skaffold.yml`)

## Testing Strategy
- Use JUnit 5 and Spring test slices where possible; reserve full `@SpringBootTest` for cross-layer behavior.
- Keep tests deterministic and isolated by profile.
- Catalog tests may use JDBC Testcontainers profile (`integration`) and require Docker.
- Order tests may use R2DBC/Testcontainers stack and require Docker.
- For single tests, run from the target service directory using `./gradlew test --tests 'package.ClassName[.method]'`.

## Formatting and Style
- Run Spotless where configured:
  - `cd catalog-service && ./gradlew spotlessApply spotlessCheck`
  - `cd order-service && ./gradlew spotlessApply spotlessCheck`
- `config-service` has no Spotless plugin yet; keep style consistent with existing code.
- Prefer constructor injection, explicit imports, and thin controllers with domain logic in `domain`.

## API and Layer Conventions
- Controllers live in `web` packages and use REST resource naming (e.g., `/books`, `/orders`).
- Keep business rules in service/domain layer; controllers orchestrate request/response mapping.
- Use dedicated exception handling via `@RestControllerAdvice` where present.
- Keep validation with Jakarta Bean Validation annotations close to request/domain models.

## Persistence Conventions
- Catalog uses Spring Data JDBC; Flyway migrations under `catalog-service/src/main/resources/db/migration`.
- Order uses Spring Data R2DBC and Flyway; keep schema evolution incremental and versioned.
- Add migrations as `V<next>__description.sql`; do not edit historical migration files.

## CI/CD Expectations
- Workflows live in `.github/workflows/`:
  - `ci-config-pipeline.yml`
  - `ci-catalog-pipeline.yml`
  - `ci-order-pipeline.yml`
  - `ci-bookstore.yml` (orchestrator on `main` pushes)
- Keep local commands aligned with CI steps (`./gradlew build`, image packaging via `bootBuildImage`).

## Git and Agent Workflow
- Do not revert or rewrite unrelated user changes.
- Avoid destructive Git commands unless explicitly requested.
- Before commit/PR, run relevant tests and formatting checks for touched services.
- In commit/PR notes, mention:
  - touched service(s)
  - executed checks
  - whether Docker/Testcontainers tests were run
- Keep this document concise and accurate; prune outdated rules when adding new ones.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **spring-native-bookstore** (2146 symbols, 3624 relationships, 65 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/spring-native-bookstore/context` | Codebase overview, check index freshness |
| `gitnexus://repo/spring-native-bookstore/clusters` | All functional areas |
| `gitnexus://repo/spring-native-bookstore/processes` | All execution flows |
| `gitnexus://repo/spring-native-bookstore/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->


<claude-mem-context>
# Memory Context

# [spring-native-bookstore] recent context, 2026-05-10 3:32pm GMT+7

No previous sessions found.
</claude-mem-context>
