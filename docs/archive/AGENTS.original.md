# AGENTS

You are senior java developer with experience in building microservices with Spring Boot and Spring Cloud. 
Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
## Mission
- Build and operate Spring bookstore services (`catalog-service`, `order-service`, `dispatcher-service`, `edge-service`, `config-service`) with deterministic local and CI behavior.
- Refactor services incrementally toward the same architectural direction used in `order-service`: explicit boundaries, use-case-oriented application services, framework-isolated adapters, and configuration-only bootstrap wiring.
- Favor correctness, reproducibility, and incremental changes over clever shortcuts.
- Keep runtime config centralized through Config Server and `config/` to avoid drift.
- Run the smallest useful verification for every change and document any skipped checks.
- Keep this file current whenever toolchains, workflows, or service boundaries change.

## Working Principles
- Think before coding: state assumptions explicitly, surface uncertainty, and do not silently pick one interpretation when multiple are plausible.
- Prefer the simplest implementation that satisfies the request; avoid speculative abstractions, premature configurability, and unnecessary fallback logic.
- Make surgical changes: touch only files and lines required by the task, and do not opportunistically refactor adjacent code.
- Every changed line should trace directly to the requested outcome or to keeping the build/tests green.
- Define success in verifiable terms before making substantial changes: what should compile, what tests should pass, and what behavior should change.
- For unclear or risky changes, stop and clarify instead of guessing.

## Repository Layout
- `catalog-service/`: REST catalog API, Spring MVC + Spring Data JDBC + Flyway.
- `order-service/`: reactive order API, Spring WebFlux + Spring Data R2DBC + Flyway.
- `dispatcher-service/`: stream processor that consumes accepted orders and publishes dispatched events.
- `edge-service/`: API gateway for catalog and order APIs.
- `config-service/`: Spring Cloud Config Server.
- `config/`: config data consumed by Config Server (per service/profile YAML files).
- `polar-deployment/docker/`: Docker Compose stack split into infra (`docker-compose.yml`) and app services (`service.yml`).
- `polar-deployment/kubernetes/local/`: local Kubernetes manifests for platform dependencies (Postgres, Kafka, Keycloak, Redis, observability).
- No monorepo Gradle root build; each service is a standalone Gradle project with its own `./gradlew`.

## Runtime and Configuration
- Service defaults: config-service `8888`, catalog `9001`, order `9002`.
- `catalog-service` and `order-service` import config from `http://localhost:8888`.
- Local DB defaults:
  - catalog: `jdbc:postgresql://localhost:5432/polardb_catalog`
  - order: `r2dbc:postgresql://localhost:5433/polardb_order`
- Prefer editing `config/*.yml` for shared runtime behavior instead of hardcoding service-local values.
- Never commit real secrets; use environment variables and keep `.env*` ignored.

## Target Architecture Direction
- Use `order-service` as the reference style for future refactors across services.
- Favor a hexagonal structure with clear package boundaries:
  - `domain`: framework-free domain types and business invariants
  - `application`: inbound/outbound ports, commands/queries, and use-case services
  - `adapter/in`: controllers, messaging consumers, request/response DTOs
  - `adapter/out`: persistence, HTTP clients, messaging publishers
  - `bootstrap`: Spring wiring and framework configuration only
- Keep business logic in domain objects or use-case services, not in controllers, repositories, WebClient clients, or configuration classes.
- Shape ports around domain types, not framework types.
- When refactoring an existing service, move from inside out:
  1. clean domain model
  2. define ports and use-case services
  3. isolate adapters
  4. simplify bootstrap wiring
- Do not apply cross-service architectural rewrites in one pass; refactor one service or one boundary at a time.

## Toolchain and Versions
- Java 21 via Gradle toolchains.
- Spring Boot `4.0.3` across all services.
- Spring Cloud BOM `2025.1.0`.
- Testcontainers BOM `1.19.7` in services that run container-backed tests.
- Spotless `6.25.0` is enabled in `catalog-service` and `order-service`.

## Build and Run Commands
- Per-service builds:
  - `cd catalog-service && ./gradlew build`
  - `cd order-service && ./gradlew build`
  - `cd config-service && ./gradlew build`
  - `cd order-service && ./gradlew generateJooq` when Flyway migrations change and checked-in jOOQ sources need refresh
- Per-service run:
  - `cd config-service && ./gradlew bootRun`
  - `cd catalog-service && ./gradlew bootRun`
  - `cd order-service && ./gradlew bootRun`
- Root Makefile shortcuts:
  - `make build`, `make test`, `make clean`
  - `make run-config`, `make run-catalog`, `make run-order`, `make run-dispatcher`, `make run-edge`
  - `make infra-up`, `make infra-down`, `make compose-up`, `make compose-down`
  - `make k8s-up`, `make k8s-down`, `make k8s-status`
  - `make skaffold-dev|skaffold-run|skaffold-delete` (requires `skaffold.yml`)

## Testing Strategy
- Use JUnit 5 and Spring test slices where possible; reserve full `@SpringBootTest` for cross-layer behavior.
- Keep tests deterministic and isolated by profile.
- Catalog tests may use JDBC Testcontainers profile (`integration`) and require Docker.
- Order tests may use R2DBC/Testcontainers stack and require Docker.
- For single tests, run from the target service directory using `./gradlew test --tests 'package.ClassName[.method]'`.
- Prefer the following test pyramid during refactors:
  - domain unit tests for pure business rules
  - application service tests with mocked ports
  - adapter integration tests with real infrastructure only where the adapter boundary needs it
  - web slice tests for controller validation and mapping
  - end-to-end tests only for the most critical flows
- For bugs, start by writing or updating a failing test that reproduces the behavior before changing production code.
- For refactors, preserve behavior with tests before and after structural changes whenever practical.

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
- Request/response DTOs belong to inbound adapters, not domain or application packages.
- External client DTOs belong to outbound adapters, not domain packages.

## Persistence Conventions
- Catalog uses Spring Data JDBC; Flyway migrations under `catalog-service/src/main/resources/db/migration`.
- Order uses Spring Data R2DBC and Flyway; keep schema evolution incremental and versioned.
- Add migrations as `V<next>__description.sql`; do not edit historical migration files.
- When introducing jOOQ or other persistence adapters, keep generated or framework-specific types inside the adapter layer and out of `domain` and `application`.

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
- Keep diffs narrow; do not "clean up" unrelated code while refactoring.
- If a simpler solution exists than the one initially considered, prefer it and document the tradeoff if needed.
- If requirements are ambiguous, ask or state assumptions clearly before making broad structural changes.
- For multi-step work, define a short plan with verification points and update it as the task evolves.
- In commit/PR notes, mention:
  - touched service(s)
  - executed checks
  - whether Docker/Testcontainers tests were run
- Keep this document concise and accurate; prune outdated rules when adding new ones.
