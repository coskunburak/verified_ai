# Test Strategy

## Test dimensions

We require deterministic code tests, integration tests, mathematical property tests, AI evaluations, UI tests and production telemetry.

## Backend unit tests

Focus on domain invariants, state transitions, mastery updates, entitlement rules and planner logic. Pure domain tests should not boot Spring.

## Module tests

Verify module boundaries and module-level application behavior.

## Integration tests

Use Testcontainers PostgreSQL for repositories, migrations, transactions, event/outbox behavior and security. Do not substitute H2 for PostgreSQL semantics.

## Contract tests

External adapters:
- AI provider schema fixtures,
- Apple billing client,
- object storage,
- math verifier.

Internal/mobile:
- OpenAPI compatibility,
- DTO mapping.

## Math verifier tests

- known identity cases,
- malformed input,
- property-based generated expressions,
- historical regression cases,
- timeout/complexity attacks.

## iOS tests

ViewModel states, token refresh, job polling, entitlement gating, repository mapping, important UI flows.

Critical UI journeys:
- sign in,
- scan/import,
- parse correction,
- solve,
- verification details,
- tutor,
- purchase/restore,
- delete account.

## AI evaluation

Separate from ordinary unit suite. Golden dataset protects parser/solver/verifier/mistake/tutor quality.

Sprint 4.10 makes Phase 4 ingestion evaluation a dedicated deterministic gate. `make eval-ai` validates the ingestion-v1 dataset, runs deterministic local fixture scoring, generates a privacy-minimized report, and compares it with the approved deterministic baseline. This does not replace backend application-service tests or connected provider quality evaluation.

## Regression priority

1. Trust/verification safety.
2. Solution correctness.
3. Reliability.
4. Latency.
5. Cost.
6. Style.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI model-evolution testing

Add explicit test/evaluation layers for route replacement:

- offline golden dataset;
- protected holdout;
- cost benchmark;
- shadow comparison;
- canary monitoring;
- fallback/rollback tests;
- slice-level regression analysis.

Future training pipelines also require dataset-leakage and reproducibility tests.
<!-- HYBRID_AI_STRATEGY_V3:END -->

<!-- MODEL_DATA_LEAKAGE_V3:START -->
## Future dataset-pipeline tests

Phase 13 dataset tooling must test:

- eligibility filtering;
- lineage completeness;
- deletion/revocation lookup;
- stable split assignment;
- exact and near-duplicate leakage;
- protected holdout access controls;
- reproducibility from manifest/checksum.
<!-- MODEL_DATA_LEAKAGE_V3:END -->
