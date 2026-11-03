# Verified AI API

Spring Boot modular monolith for the Verified AI Learning Platform.

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL for local runtime
- Docker for Testcontainers integration tests

## Local Commands

```sh
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The API is the product authority for identity, entitlement, learning state, verification policy, billing, and durable orchestration. It does not expose AI provider secrets or the internal math verifier to the mobile client.

## Sprint 6.6 delivery status

Bounded deterministic hints and the HTTP 422 repair are implemented. Owner targeted reconciliation
`20261102-214452-cv4eye9f` passed inputs/E/D; backend coverage spans 217 prior passes and the repaired
focused pass (218 cases, none unresolved). V029 fresh/isolated V028-forward cases passed in prior evidence.
Hint/reveal contracts, privacy, idempotency and rollback are in the
[Sprint 6.6 report](../../docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.6_EXECUTION_REPORT.md).

Full current-source acceptance remains PENDING_FULL_CURRENT_SOURCE_RUN; shared upgrades BLOCKED;
hosted CI NOT_VERIFIED; production NOT_AUTHORIZED. Both tutoring flags default false; TUTOR routing
remains disabled. Successful verified reveal is unavailable. No live inference or hint debit is added.

## Sprint 6.7 product integration

Owner-scoped attempt and learning execution is authored with active forward Flyway V030–V032, preserving old migration bytes and source evidence. Domain confirmation/capture provenance, immutable artifacts, evidence-idempotent mastery and privacy use short account/source transactions. Modularity and attempt test suites passed in owner runs; analysis/vision/production-authorized flags default false. Full current-source acceptance pending; shared upgrades NOT_AUTHORIZED.
