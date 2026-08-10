# Sprint 4.6 Execution Report

## Status

`SPRINT_4.6 = COMPLETE`

Sprint 4.6 implements canonical mathematical representation and safe parsing only. It does not implement solving, answer verification, verification verdict policy, primary skill classification, difficulty assignment, parse correction UI, or Phase 5 workflows.

## Source Evidence

NotebookLM MCP query `feddccef9679` completed successfully. It confirmed:
- Layer 4 is the safe verifier representation derived from Layer 3 `ProblemParse`.
- Raw OCR/AI strings must not be executed or passed through unrestricted symbolic parsing.
- The verifier must enforce allowlisted operations/functions, node/depth/exponent/symbolic complexity limits, time budgets, and internal auth.
- Exact numeric thresholds were not specified in the excerpts, so Sprint 4.6 defines explicit v1 limits.

Local source documents reviewed and updated:
- `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.6_IMPLEMENTATION_MAP.md`

## Implemented Scope

Backend:
- Added deterministic canonical math parser with typed AST nodes.
- Added `canonical-problem-v1` and `verifier-input-v1` document models.
- Added immutable `canonical_problems` persistence via `V012__create_canonical_problem_lifecycle.sql`.
- Added `POST /api/v1/problem-sessions/{sessionId}/canonicalize`.
- Added `GET /api/v1/problem-sessions/{sessionId}/canonical-problem`.
- Added privacy export/deletion coverage for canonical problem data.
- Added low-cardinality canonicalization metrics.

Verifier:
- Added typed `VerifierInputRequest` Pydantic schema.
- Added explicit AST-to-SymPy constructor validator.
- Added `/internal/v1/verify/validate-input`.
- Added structured schema error handling for invalid verifier payloads.

Contracts and fixtures:
- Added `packages/schemas/canonical-problem.schema.json`.
- Added `packages/schemas/verifier-input.schema.json`.
- Updated public and internal OpenAPI contracts.
- Added synthetic verifier input fixtures under `packages/test-fixtures/canonical/verifier-input-v1/`.

## Canonical V1 Policy

Supported:
- arithmetic expressions,
- algebraic expressions,
- single equations,
- single inequalities,
- relational source constraints.

Rejected or deferred:
- calculus,
- systems,
- solution sets,
- units,
- diagrams,
- probability/statistics/linear algebra,
- multi-part structures,
- raw OCR/AI string execution.

Complexity limits:
- expression length: 512 characters,
- AST nodes: 120,
- AST depth: 32,
- numeric exponent magnitude: 12,
- numeric literal digits: 64,
- function nesting depth: 8.

Derived restrictions:
- denominator non-zero restrictions are preserved,
- product denominators are decomposed where possible,
- `sqrt`, `log`, and `tan` domain restrictions are represented,
- no algebraic cancellation removes restrictions.

## Focused Evidence

| Gate | Result |
|---|---|
| Java production compile with Java 21 | PASS |
| `CanonicalMathParserTest` | PASS |
| `CanonicalProblemApplicationServiceTest` with Testcontainers | PASS |
| `ProblemParseControllerTest` canonical API coverage | PASS |
| Focused Sprint 4.6 Java suite including Flyway | PASS |
| Python verifier typed AST unit/contract tests | PASS, 12 tests |
| Python ruff check | PASS |
| OpenAPI/JSON schema syntax parse | PASS |

Known warnings:
- Python verifier tests still emit the existing Starlette/httpx deprecation warning already tracked as `TD-DEP-001`.
- Java Testcontainers tests require Docker socket access outside the Codex sandbox.

## Full Exit Gate

| Gate | Result |
|---|---|
| `make doctor` | PASS |
| `make lint` | PASS |
| `make contracts-check` | PASS |
| `make docs-check` | PASS |
| `make secret-scan` | PASS |
| `make test-api` | PASS, 115 tests |
| `make test-verifier` | PASS, 20 tests, 1 existing Starlette/httpx warning |
| `make test-ios` | PASS |
| Docker Compose config validation | PASS |
| Git diff/staging isolation check | PASS, Sprint 4.6 hunks isolated from pre-existing auth/iOS work |

iOS simulator note: the first sandboxed `make test-ios` attempt could not reach CoreSimulator and reported no matching simulator. Elevated `xcrun simctl list devices available` showed `iPhone 16 Pro (56E87D46-F64B-4AC7-AB87-1D94F5C9F3D0)`, and elevated `xcrun simctl bootstatus 56E87D46-F64B-4AC7-AB87-1D94F5C9F3D0 -b` returned immediately with the device already booted before the successful elevated iOS test run.

## Dirty Worktree Isolation

The workspace contained unrelated auth/iOS work before Sprint 4.6. Sprint 4.6 changes must be staged selectively. Shared files requiring hunk-level review include:
- `packages/contracts/openapi/public-api.yaml`
- `services/api/src/test/java/com/verifiedai/platform/FlywayMigrationTest.java`

The unrelated untracked auth migration `V011__create_email_and_guest_identity_auth.sql` remains user-owned. Sprint 4.6 uses `V012__create_canonical_problem_lifecycle.sql`.

## Remaining Follow-up

- `TD-MATH-001`: later Phase 5 verification work must avoid routing canonical OCR/LLM text through the legacy guarded string equivalence endpoint and should converge deterministic verification on typed AST input.
