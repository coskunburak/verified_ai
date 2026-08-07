# Phase 2 Implementation Map

## Scope

Phase 2 creates the production repository and platform foundation only. It does not implement product solving, AI orchestration, tutoring, mastery, billing, subscriptions, or Phase 13 ML capabilities.

## Sprint 2.1 - Monorepo Bootstrap and Developer Experience

| Dimension | Decision |
|---|---|
| Owning context/module | Platform repository governance |
| Domain states/invariants touched | None; preserves Phase 1 invariants |
| iOS touched | None beyond future root command wiring |
| Backend touched | None beyond future root command wiring |
| Database migrations/indexes | None |
| API/event/schema changes | None |
| AI implications | No AI calls or model dependencies |
| Security/privacy | `.gitignore`, `.env.example`, secret scan, `.DS_Store` removal |
| Telemetry/SLO/cost | No runtime telemetry yet |
| Tests/evidence | `make doctor`, shell syntax, docs check, secret scan |
| Rollout/flag strategy | Repository-only change |
| Rollback/recovery | Revert root files and scripts before code depends on them |

## Sprint 2.2 - iOS Workspace, App Shell, DI, and Navigation

| Dimension | Decision |
|---|---|
| Owning context/module | iOS platform shell |
| Domain states/invariants touched | Client remains non-authoritative for entitlement, mastery, verification, billing, and exam scoring |
| iOS touched | SwiftUI app entry, environment selection, composition root, router/deep links, network baseline, secure storage, OSLog, design tokens, launch shell |
| Backend touched | Reads API health contract only |
| Database migrations/indexes | None |
| API/event/schema changes | Health endpoint client shape only |
| AI implications | No direct AI or verifier calls |
| Security/privacy | Keychain abstraction, no secrets in source, safe failure copy |
| Telemetry/SLO/cost | Correlation-aware client logging foundation |
| Tests/evidence | Swift unit tests for environment/router/network/error; UI launch smoke where Xcode permits |
| Rollout/flag strategy | Development shell only |
| Rollback/recovery | Remove iOS target without affecting backend or docs |

## Sprint 2.3 - Spring Boot Modular Monolith Bootstrap

| Dimension | Decision |
|---|---|
| Owning context/module | Backend platform plus declared bounded contexts |
| Domain states/invariants touched | No business state transitions implemented |
| iOS touched | Consumes only public health contract |
| Backend touched | `com.verifiedai` app, module packages, Spring Modulith verification, health/readiness, error envelope, correlation ID |
| Database migrations/indexes | None beyond connection readiness until Sprint 2.4 |
| API/event/schema changes | `/api/v1/platform/health` and `/api/v1/platform/readiness` |
| AI implications | AI module placeholder only; no provider adapters or SDK calls |
| Security/privacy | Security baseline deny-by-default except health/actuator, safe error handling |
| Telemetry/SLO/cost | Micrometer/Actuator, structured logging, correlation IDs |
| Tests/evidence | Maven compile/test, context smoke, module verification, API smoke where Java 21 permits |
| Rollout/flag strategy | Local/profile configuration only |
| Rollback/recovery | Remove service or disable API container before product endpoints exist |

## Sprint 2.4 - PostgreSQL, Flyway, Persistence, and Testcontainers

| Dimension | Decision |
|---|---|
| Owning context/module | Data/platform foundation |
| Domain states/invariants touched | No learner/product business records |
| iOS touched | None |
| Backend touched | DataSource configuration, Flyway, migration validation, repository smoke |
| Database migrations/indexes | Add minimal platform foundation schema/table for migration proof only |
| API/event/schema changes | Readiness reflects database dependency |
| AI implications | None |
| Security/privacy | No production credentials; local/test env vars only |
| Telemetry/SLO/cost | Database pool metrics through Actuator/Micrometer |
| Tests/evidence | Testcontainers PostgreSQL migration test where Docker and Java 21 permit |
| Rollout/flag strategy | Local/test/staging/prod profiles |
| Rollback/recovery | Forward corrective migration in future production; no applied production state exists now |

## Sprint 2.5 - Internal Python Math Verifier Bootstrap

| Dimension | Decision |
|---|---|
| Owning context/module | Verification runtime, internal-only |
| Domain states/invariants touched | Deterministic evidence only; does not assign overall `VERIFIED` |
| iOS touched | None; iOS never calls verifier |
| Backend touched | Future internal verifier client contract only |
| Database migrations/indexes | None; verifier owns no canonical data |
| API/event/schema changes | Internal `/internal/v1/verify/equivalence` plus `/health` |
| AI implications | No LLM/model use; SymPy deterministic equivalence only |
| Security/privacy | Internal header authentication baseline, parser allowlist, resource limits |
| Telemetry/SLO/cost | Structured logs, correlation ID propagation, health response |
| Tests/evidence | Unit/contract/security tests for valid, invalid, auth failure, timeout/resource errors |
| Rollout/flag strategy | Compose-internal service only |
| Rollback/recovery | Remove verifier container; API remains healthy but verifier-dependent future paths degrade |

## Sprint 2.6 - Local Infrastructure

| Dimension | Decision |
|---|---|
| Owning context/module | Platform infrastructure |
| Domain states/invariants touched | None |
| iOS touched | Local development API URL only |
| Backend touched | Compose wiring for API to PostgreSQL, Redis, MinIO, verifier |
| Database migrations/indexes | Runs Sprint 2.4 migrations on API startup |
| API/event/schema changes | None |
| AI implications | No provider routes |
| Security/privacy | Local synthetic credentials only; services documented as local-only |
| Telemetry/SLO/cost | Health checks and inspectable logs |
| Tests/evidence | `docker compose config`, optional `up`/health/down where Docker permits |
| Rollout/flag strategy | Local development only |
| Rollback/recovery | `make down`; named volumes are local/dev artifacts |

## Sprint 2.7 - Continuous Integration Baseline

| Dimension | Decision |
|---|---|
| Owning context/module | Platform CI |
| Domain states/invariants touched | None |
| iOS touched | CI build/test workflow |
| Backend touched | CI compile/test/Flyway/Testcontainers workflow |
| Database migrations/indexes | CI validates migrations |
| API/event/schema changes | Contract workflow validates OpenAPI files |
| AI implications | No AI evaluation workflow enabled for Phase 2 runtime because prompts/models are untouched |
| Security/privacy | Secret scan and dependency audit baseline |
| Telemetry/SLO/cost | CI evidence artifacts only |
| Tests/evidence | Workflow files, local syntax/config checks where possible |
| Rollout/flag strategy | Pull request and main branch validation |
| Rollback/recovery | Revert workflow files if CI environment needs adjustment |

## Sprint 2.8 - Observability, Logging, Tracing, and Diagnostics

| Dimension | Decision |
|---|---|
| Owning context/module | Platform observability |
| Domain states/invariants touched | No raw student content in logs; no operational record becomes product truth |
| iOS touched | OSLog/correlation foundation |
| Backend touched | structured logs, correlation ID filter, Actuator metrics, tracing-compatible configuration |
| Database migrations/indexes | None |
| API/event/schema changes | Health/readiness and diagnostics docs only |
| AI implications | Future metrics placeholders only; no AI calls |
| Security/privacy | Logging redaction policy documented; no token/content logging |
| Telemetry/SLO/cost | HTTP, JVM, DB pool, verifier-call metric names prepared |
| Tests/evidence | Unit/smoke tests for correlation and health; docs for local diagnostics |
| Rollout/flag strategy | Local/staging/prod logging configuration by profile |
| Rollback/recovery | Disable exporter endpoints/config without changing domain code |

## Phase 2 AI Economics Answer

This phase adds zero external AI inference calls. Expected invocation rate per learner action is zero. Deterministic code is sufficient for all Phase 2 validation. Secondary solving is not introduced. Cost-regression telemetry is prepared structurally but has no runtime AI traffic in this phase. No production student data is created or made training eligible.
