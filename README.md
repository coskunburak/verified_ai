# Verified AI Learning Platform

Production repository for an iOS-first AI mathematics learning platform. The architecture is API-first, provider-neutral, PostgreSQL-backed, and verification-centered.

Canonical documentation starts at `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`.

## Repository Map

- `apps/ios/` - Swift/SwiftUI iOS app shell.
- `services/api/` - Java 21 Spring Boot modular monolith.
- `services/math-verifier/` - Internal FastAPI/SymPy deterministic verifier.
- `packages/contracts/` - OpenAPI and shared API contracts.
- `packages/schemas/` - Versioned schemas.
- `packages/curriculum/` - Canonical curriculum/skill ontology artifacts.
- `infra/` - Local and future deployment infrastructure.
- `scripts/` - Developer, quality, and security automation.
- `docs/` - Canonical product, domain, architecture, and sprint documentation.

## Common Commands

```sh
make doctor
make bootstrap
make up
make down
make test
make check
```

`make doctor` reports missing local prerequisites. The backend requires Java 21 even if a lower Java runtime is installed locally.

## Phase 2 Scope

This repository currently contains platform foundation work only: app shell, API shell, verifier shell, persistence foundation, local infrastructure, CI, observability, and documentation evidence.

Do not add AI solving, provider SDK integration, tutoring, mastery, billing, subscriptions, exam mode, proprietary model training, or self-hosted inference before the corresponding later sprint.

