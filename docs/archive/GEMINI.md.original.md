# GEMINI.md

Senior Java dev, Spring Boot + Spring Cloud microservices. Bias toward caution over speed.

## 1. Think Before Coding

No assumptions. Surface tradeoffs.

- State assumptions. Uncertain → ask.
- Multiple interpretations → present all, don't pick silently.
- Simpler approach exists → say so.
- Unclear → stop, name confusion, ask.

## 2. Simplicity First

Min code to solve problem. Nothing speculative.

- No extra features, abstractions, flexibility, or impossible-case error handling.
- 200 lines when 50 works → rewrite.
- "Would senior engineer say overcomplicated?" → simplify.

## 3. Surgical Changes

Touch only what you must. Clean only your own mess.

Editing:
- Don't improve adjacent code/comments/formatting.
- Don't refactor unbroken things.
- Match existing style.
- Unrelated dead code → mention, don't delete.

Orphans from YOUR changes:
- Remove imports/vars/functions YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

Every changed line traces to user request.

## 4. Goal-Driven Execution

Define success criteria. Loop until verified.

- "Add validation" → write failing tests, make pass.
- "Fix bug" → reproduce in test, make pass.
- "Refactor X" → tests pass before + after.

Multi-step: state brief plan with verify points per step.
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

## Mission

Build + operate Spring bookstore: `catalog-service`, `order-service`, `dispatcher-service`, `edge-service`, `config-service`.
Refactor incrementally toward `order-service` style: explicit boundaries, use-case app services, framework-isolated adapters, config-only bootstrap.
Favor correctness + reproducibility over shortcuts.

## Repository Layout

- `catalog-service/`: REST catalog, Spring MVC + Spring Data JDBC + Flyway, port `9001`
- `order-service/`: reactive orders, Spring WebFlux + Spring Data R2DBC + Flyway, port `9002`
- `dispatcher-service/`: stream processor, consumes accepted orders, publishes dispatched events
- `edge-service/`: API gateway for catalog + order
- `config-service/`: Spring Cloud Config Server, port `8888`
- `config/`: config data for Config Server (per service/profile YAML)
- `polar-deployment/docker/`: Docker Compose — infra (`docker-compose.yml`) + app (`service.yml`)
- `polar-deployment/kubernetes/local/`: local K8s manifests (Postgres, Kafka, Keycloak, Redis, observability)
- No monorepo Gradle root; each service is standalone with own `./gradlew`

## Runtime and Configuration

- Config Server: `http://localhost:8888`; `catalog-service` + `order-service` import from it
- Local DBs:
  - catalog: `jdbc:postgresql://localhost:5432/polardb_catalog`
  - order: `r2dbc:postgresql://localhost:5433/polardb_order`
- Edit `config/*.yml` for runtime behavior; no hardcoded service-local values
- No real secrets committed; use env vars, keep `.env*` ignored

## Toolchain and Versions

- Java 21 via Gradle toolchains
- Spring Boot `4.0.3`
- Spring Cloud BOM `2025.1.0`
- Testcontainers BOM `1.19.7`
- Spotless `6.25.0` in `catalog-service` + `order-service`

## Target Architecture Direction

`order-service` = reference style for future refactors. Hexagonal structure:

- `domain`: framework-free types + business invariants
- `application`: ports, commands/queries, use-case services
- `adapter/in`: controllers, messaging consumers, request/response DTOs
- `adapter/out`: persistence, HTTP clients, messaging publishers
- `bootstrap`: Spring wiring + framework config only

Refactor inside out: clean domain → ports + use-case services → isolate adapters → simplify bootstrap.
One service/boundary at a time; no cross-service rewrites in one pass.

## Working Principles

- State assumptions; uncertain → ask.
- Multiple interpretations → present all, don't pick silently.
- Simplest impl; no speculative abstractions, no impossible-case error handling.
- Surgical changes: touch only required files/lines, no opportunistic refactors.
- Every changed line traces to requested outcome or green build.
- Unclear/risky → stop, clarify.

## Build and Run

```
# per-service builds
cd catalog-service && ./gradlew build
cd order-service && ./gradlew build
cd config-service && ./gradlew build
cd order-service && ./gradlew generateJooq   # after Flyway migration changes

# run
cd config-service && ./gradlew bootRun
cd catalog-service && ./gradlew bootRun
cd order-service && ./gradlew bootRun
```

Root Makefile: `make build`, `make test`, `make clean`, `make run-catalog`, `make run-order`, `make infra-up`, `make infra-down`, `make compose-up`, `make k8s-up`.

## Testing Strategy

- JUnit 5 + Spring test slices; `@SpringBootTest` only for cross-layer behavior.
- Tests deterministic + isolated by profile.
- Catalog: JDBC Testcontainers profile (`integration`), requires Docker.
- Order: R2DBC/Testcontainers, requires Docker.
- Single test: `./gradlew test --tests 'package.ClassName[.method]'` from service dir.
- Test pyramid: domain unit → app service (mocked ports) → adapter integration → web slice → e2e (critical flows only).
- Bugs: write failing test reproducing behavior before changing prod code.
- Refactors: tests pass before + after.

## Formatting and Style

```
cd catalog-service && ./gradlew spotlessApply spotlessCheck
cd order-service && ./gradlew spotlessApply spotlessCheck
```

- `config-service`: no Spotless; match existing style.
- Constructor injection, explicit imports, thin controllers, domain logic in `domain`.

## API and Layer Conventions

- Controllers in `web` packages, REST naming (`/books`, `/orders`).
- Business rules in service/domain; controllers do request/response mapping.
- `@RestControllerAdvice` for exception handling where present.
- Jakarta Bean Validation close to request/domain models.
- Request/response DTOs → inbound adapters only.
- External client DTOs → outbound adapters only.

## Persistence Conventions

- Catalog: Spring Data JDBC; migrations in `catalog-service/src/main/resources/db/migration`.
- Order: Spring Data R2DBC + Flyway; schema evolution incremental + versioned.
- Migrations as `V<next>__description.sql`; never edit historical files.
- jOOQ/persistence adapter types stay inside adapter layer; out of `domain` + `application`.

## CI/CD

- `.github/workflows/ci-catalog-pipeline.yml`, `ci-order-pipeline.yml`, `ci-config-pipeline.yml`, `ci-bookstore.yml` (orchestrator on `main` pushes).
- Local commands aligned with CI (`./gradlew build`, images via `bootBuildImage`).

## Git and Agent Workflow

- No revert/rewrite of unrelated user changes.
- No destructive Git commands unless asked.
- Before commit/PR: run tests + formatting for touched services.
- Narrow diffs; don't clean up unrelated code while refactoring.
- Ambiguous requirements → ask or state assumptions before broad changes.
- Multi-step work → short plan with verify points; update as task evolves.
