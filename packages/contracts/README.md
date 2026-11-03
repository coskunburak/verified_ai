# Contracts

Versioned API contracts live here. Runtime modules may generate adapters from these files, but generated code must not become the source of truth.

- `openapi/public-api.yaml` documents client-facing API routes. It exposes canonical problem display metadata only, not internal verifier AST payloads.
- `openapi/internal-math-verifier.yaml` documents internal verifier routes. Sprint 4.6 adds typed `verifier-input-v1` validation; raw expression equivalence remains a guarded compatibility endpoint.

## Sprint 6.6 Tutor operations

`openapi/public-api.yaml` adds owned GET/POST hints and confirmed POST reveal under the existing
Tutor session hierarchy. Level progression and idempotency are server-authoritative. Exact-field/type
semantic rejection uses 422; same-key payload conflicts use 409. Duplicate fields and oversized bodies
are bounded. REVEAL returns TUTOR_REVEAL_UNAVAILABLE until a trusted authorized reference reader exists.
Existing Tutor schemas/operations remain compatible.

Owner evidence reconciliation 20261102-214452-cv4eye9f passed inputs/E/D with no unresolved cases;
the HTTP status regression passed its focused owner retest. This is TARGETED_RECONCILIATION_PASSED,
not single-current-source full acceptance or production authorization. See the
[Sprint 6.6 report](../../docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.6_EXECUTION_REPORT.md).

## Sprint 6.7 product integration

Public attempt routes now include reviewed extraction, durable twins/answers/history, verified methods and mistake passport. Internal attempt-v2 and attempt-learning share the authenticated bounded worker. Versioned schema references, strict ownership and idempotency apply; contract checks and targeted test repairs passed in owner runs.
