# Phase 2 — Repository & Platform Foundation
## Phase objective
This phase groups the production delivery units required for **Repository & Platform Foundation**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
## Phase-wide quality rules
- Preserve all domain invariants and accepted ADRs.
- Update canonical documentation when semantics or contracts change.
- Add automated tests at the cheapest reliable layer.
- Add observability before relying on a behavior in production.
- Never expose AI provider credentials or internal verifier endpoints to the mobile client.
- All user-visible failure states require a recovery path.
- All durable schema changes use reviewed Flyway migrations.
- All AI behavior changes require traceable prompt/model versions.
## Sprint sequence
- `SPRINT_2.1` — **Monorepo Bootstrap and Developer Experience** — Create the canonical repository root, deterministic local commands, environment templates, formatting, linting, and developer bootstrap.
- `SPRINT_2.2` — **iOS Workspace, App Shell, Dependency Injection, and Navigation Foundation** — Create a production Swift/SwiftUI application shell with environment configuration, composition root, router, launch state, and testable dependency boundaries.
- `SPRINT_2.3` — **Spring Boot Modular Monolith Bootstrap** — Create the Java 21/Spring Boot modular monolith with Spring Modulith verification, package rules, environment profiles, and health endpoints.
- `SPRINT_2.4` — **PostgreSQL, Flyway, Persistence, and Testcontainers Foundation** — Create production-like relational persistence locally and in tests, with immutable migrations and no H2 behavior divergence.
- `SPRINT_2.5` — **Internal Python Math Verifier Bootstrap** — Create the isolated FastAPI/SymPy verifier with internal authentication, strict schemas, resource limits, health checks, and contract tests.
- `SPRINT_2.6` — **Local Infrastructure with PostgreSQL, Redis, and Object Storage** — Make the entire backend stack reproducibly runnable with Docker Compose and safe local seed data.
- `SPRINT_2.7` — **Continuous Integration Baseline** — Implement separate CI pipelines for iOS, API, verifier, contracts, architecture checks, migrations, and documentation.
- `SPRINT_2.8` — **Observability, Structured Logging, Tracing, and Developer Diagnostics** — Establish correlation IDs, privacy-safe logs, metrics, traces, health probes, and local diagnostic workflows before feature complexity grows.

## Phase exit gate
The phase is complete only when every sprint-specific acceptance criterion is satisfied, phase-level integration tests pass, documentation is current, no unresolved P0/P1 defects remain in the phase scope, and the next phase can consume the delivered contracts without relying on undocumented assumptions.

## Required review at phase close
- Architecture drift review.
- Security/privacy delta review.
- AI cost/quality delta review when applicable.
- Database migration and data-retention review.
- iOS accessibility/localization review for user-visible work.
- Observability and supportability review.
- Backlog reprioritization using evidence collected during the phase.

<!-- PHASE_PRODUCTION_V3:START -->
# Phase Production Contract v3

## Phase objective alignment

**Platform foundation**

- Make local/staging environments reproducible and observable from day one.
- Capture AI usage provenance/cost schema early even before traffic exists.
- Keep future ML tooling isolated and optional; do not contaminate V1 runtime dependencies.

## Hybrid-AI rule

This phase must preserve the accepted API-first/provider-neutral architecture. Proprietary training/self-hosting is forbidden before Phase 13 entry gates, except for isolated evaluation research that never affects production and uses eligible data. All AI-affecting work must measure quality, latency and cost together.

## Phase exit evidence

The phase exit review must include:

- completed sprint acceptance evidence;
- unresolved P0/P1 risks;
- architecture/documentation drift check;
- security/privacy review for changed trust boundaries;
- AI quality/cost trend if AI routes were touched;
- migration/rollback readiness;
- next-phase dependency statement.
<!-- PHASE_PRODUCTION_V3:END -->
