# Backend Architecture and File Hierarchy

## Stack

- Java 21 baseline
- Spring Boot 4.x line
- Spring Modulith
- Spring Security
- Spring Data JPA / Hibernate
- Flyway
- PostgreSQL
- Redis
- provider-neutral AI adapters
- Micrometer/OpenTelemetry

Pin actual versions in build files and upgrade deliberately.

## Repository hierarchy

```text
services/api/
├── pom.xml
├── Dockerfile
├── README.md
└── src/
    ├── main/
    │   ├── java/com/verifiedai/
    │   │   ├── VerifiedAiApplication.java
    │   │   ├── identity/
    │   │   ├── profile/
    │   │   ├── curriculum/
    │   │   ├── problem/
    │   │   ├── solving/
    │   │   ├── verification/
    │   │   ├── tutoring/
    │   │   ├── attempt/
    │   │   ├── mistake/
    │   │   ├── mastery/
    │   │   ├── studyplan/
    │   │   ├── exam/
    │   │   ├── billing/
    │   │   ├── notification/
    │   │   ├── ai/
    │   │   ├── admin/
    │   │   └── sharedkernel/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-local.yml
    │       ├── application-test.yml
    │       ├── application-prod.yml
    │       ├── db/migration/
    │       └── prompts/
    └── test/
```

## Per-module pattern

```text
problem/
├── api/
│   ├── asset/
│   ├── preprocessing/
│   ├── recognition/
│   ├── parse/
│   ├── canonicalization/
│   ├── classification/
│   └── session/
├── application/
│   ├── asset/
│   ├── recognition/
│   ├── parse/
│   ├── canonicalization/
│   ├── classification/
│   └── session/
├── domain/
│   ├── model/
│   │   ├── asset/
│   │   ├── recognition/
│   │   ├── parse/
│   │   ├── canonicalization/
│   │   ├── classification/
│   │   └── session/
│   └── port/
├── infrastructure/
│   ├── persistence/
│   │   ├── entity/
│   │   └── repository/
│   ├── preprocessing/
│   ├── recognition/
│   ├── parser/
│   ├── classification/
│   ├── storage/
│   └── mapper/
└── package-info.java
```

For feature-dense bounded contexts such as `problem`, keep layer names stable
and split inside the layer by product workflow. Current production workflows are
`asset`, `preprocessing`, `recognition`, `parse`, `canonicalization`,
`classification`, and `session`. Tests mirror the same package below
`src/test/java`.

## Meaning of layers

### api
Transport-only concerns, request DTO validation, auth context mapping and response DTOs.

### application
Use-case orchestration and transaction boundaries.

### domain
Business concepts, value objects, invariants and ports. No provider/JPA DTO leakage.

### infrastructure
JPA, Redis, storage and external HTTP/provider adapters.

## Transactions

Never keep a DB transaction open during a slow AI call. Persist stage/job state, commit, perform external work, then persist result in a new transaction.

## Repositories

Domain interfaces in `domain.port`. JPA implementation in infrastructure. Controller never returns JPA entity.

## Shared kernel

Allowed: identifiers, DomainEvent, Clock, small generic value primitives.

Forbidden: giant Utils, business-rule dumping ground, provider clients.

## Configuration

Use typed configuration properties. Secrets are injected. Model routing and feature flags are externally configurable.

## Validation layers

1. DTO syntax.
2. Application authorization/context.
3. Domain invariants.

Bean validation is not the business-rule engine.

## Exception handling

Known errors map to stable API codes. Unexpected exceptions return safe generic message + traceId while detailed stack stays internal.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Hybrid AI/backend evolution rule

The Spring Boot API remains the orchestration and product authority. External or future proprietary inference implementations sit behind ports in the `ai` module. Do not introduce ML framework dependencies into learner-domain modules.

Future optional code roots are defined in `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`; they should not be materialized before Phase 13 unless required for evaluation tooling.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## V4 canonical hierarchy alignment

For exhaustive module-by-module package/file trees, migrations, test mirrors, and infrastructure placement, use `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`. Backend module boundaries and contracts remain governed by this document plus `backend/20_BACKEND_MODULE_CONTRACTS.md`; placement ambiguity is resolved by `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`.
