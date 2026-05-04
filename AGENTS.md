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

This project is indexed by GitNexus as **spring-native-bookstore** (1197 symbols, 2050 relationships, 30 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

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

# [spring-native-bookstore] recent context, 2026-04-28 6:05pm GMT+9

Legend: 🎯session 🔴bugfix 🟣feature 🔄refactor ✅change 🔵discovery ⚖️decision 🚨security_alert 🔐security_note
Format: ID TIME TYPE TITLE
Fetch details: get_observations([IDs]) | Search: mem-search skill

Stats: 50 obs (20,995t read) | 0t work

### Apr 28, 2026
157 4:23p 🔵 ConnectionFactoryOptionsInitializer not found in Spring Boot autoconfigure JAR
158 " 🔵 ConnectionFactoryOptionsInitializer not found in Gradle cache
159 " 🔵 Testcontainers disabledWithoutDocker Configuration Explained
S2 Explained @Testcontainers(disabledWithoutDocker = true) purpose and completed final code formatting fixes for order-service tests (Apr 28, 4:23 PM)
S1 Explained @Testcontainers(disabledWithoutDocker = true) annotation and continued order-inventory integration test configuration work (Apr 28, 4:23 PM)
S3 Explained @Testcontainers(disabledWithoutDocker = true) annotation purpose and completed final code formatting fixes for order-service tests (Apr 28, 4:25 PM)
160 4:31p 🔵 Spring Boot JDBC Auto-Configuration Classes in 4.0.3
161 4:32p 🔴 Added JDBC DataSource Auto-Configuration Exclusions to Kafka Test
162 4:33p 🔴 Fixed Spotless Formatting Violations in Order-Service
163 4:39p 🔵 Docker Available and Running on Development Machine
164 " ✅ Removed @Testcontainers disabledWithoutDocker Parameter from OrderControllerTest
S4 Removed @Testcontainers disabledWithoutDocker parameter from all test classes, encountered Docker API version mismatch failure, investigated root cause (Apr 28, 4:39 PM)
165 4:40p 🔴 Upgraded Testcontainers from 1.19.7 to 1.21.4 to Fix Docker API Compatibility
167 " 🔴 Testcontainers 1.21.4 Upgrade Fixes Docker API Compatibility
166 4:41p 🔵 Running Testcontainers Test After Upgrading to 1.21.4
168 4:43p 🔵 InputDestination getChannelByName Returns Null for handleInventoryDecision-in-0
169 " 🔵 Test Resources Application.yml Does Not Override Spring Cloud Function Definition
170 4:45p 🔴 Added Spring Cloud Function Definition to Test Application Configuration
171 " 🔴 Added Spring Cloud Stream Test Binder Configuration to Fix Test Binding
172 4:46p 🔴 Fixed Order Version Bumping in accept() and reject() Methods
173 " 🔴 OrderServiceApplicationTests Pass After Fixing Order Version Increment
174 4:47p 🔵 OrderControllerTest and JooqOrderRepositoryImplTest Fail After Version Increment Fix
175 " 🔵 Spring Assert.state() Failure at Spring Core Assert.java:80
176 " 🔵 Spring Cloud Stream Binding Failure Due to Missing Test Binder
177 4:48p 🔵 Root Cause: Unknown Binder Configuration test - Missing TestChannelBinderConfiguration Import
178 " 🔴 Removed spring.cloud.stream.defaultBinder: test from Test Application Configuration
180 " 🔵 Inventory-Service Tests Fail with NullPointerException After Testcontainers Upgrade
179 4:49p 🔴 All Order-Service Tests Pass After Removing Invalid Test Binder Configuration
181 4:50p 🔵 Gradle test execution blocked by network permission error
182 5:01p 🔵 Gradle network permission error rooted in Java SocketException
183 5:15p 🔵 Gradle network permission issue resolved with elevated permissions
184 " 🔵 OrderEventConsumerTest duplicate handling test fails with AssertionError
185 5:16p 🔴 Order-service test binding configuration fixed
186 5:17p 🔴 Inventory-service test flakiness resolved with output channel draining
187 5:34p 🔵 Infrastructure mismatch: Services use Kafka but local K8s deployment configures RabbitMQ
188 " 🔵 Kafka manifest exists but RabbitMQ reference remains in skaffold.yml
189 " 🔵 Kubernetes service deployments correctly configured for Kafka
190 " ✅ Skaffold configuration updated to use Kafka instead of RabbitMQ
191 " 🔵 All three Kafka-using services confirmed in Kubernetes deployments
192 5:35p 🔵 PostgreSQL databases use separate Kubernetes deployments for each service
193 " ✅ Infrastructure migration from RabbitMQ to Kafka completed across all configuration files
194 " ✅ Kafka deployment integrated into cluster creation script
195 " ✅ README.md documentation updated for inventory-service and dispatcher-service
196 5:37p ✅ inventory-service port changed from 9003 to 9004 to resolve port conflict
197 5:38p ✅ Docker Compose configuration updated for inventory-service port change
198 " 🔴 OrderEventConsumerTest refactored to verify state-level idempotency instead of event publication behavior
200 5:55p 🔴 OrderEventConsumerTest updated to use Spring Cloud Stream binding names instead of destination names
202 " 🔵 InputDestination.getChannelByName() returns null for binding names
201 5:56p 🔵 Spring Cloud Stream Test Binder binding name usage causes NullPointerException
204 5:57p 🔴 OrderEventConsumerTest passes after reverting to destination names and refactoring for state-level idempotency
205 " 🔴 OrderEventConsumerTest fully passes with both tests succeeding after destination name revert and test structure improvements
203 5:58p 🔴 OrderEventConsumerTest reverted to use destination names after binding names caused NullPointerException
206 6:02p 🔵 Full inventory-service test suite shows 1 failure out of 31 tests
207 " 🔵 OrderEventConsumerTest fails in full suite but passes in isolation due to test interference
</claude-mem-context>
