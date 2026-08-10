# Sprint 4.6 Implementation Map

## NotebookLM / Source Evidence

`NOTEBOOKLM_MCP_STATUS = CONNECTED`.

NotebookLM query `feddccef9679` against the Verified AI Mathematics Learning Platform Technical Specification completed and confirmed that Sprint 4.6 owns Layer 4: a safe, parser-compatible verifier representation derived from selected `ProblemParse` data. The excerpts did not define exact numeric complexity limits or a fixed schema version string, so this sprint defines explicit v1 contracts and first-class limits in code and documentation.

Primary local sources reviewed:
- `domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`
- `ai/27_VERIFICATION_ENGINE.md`
- `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`
- `architecture/51_ERROR_TAXONOMY_AND_RECOVERY_CONTRACT.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.5_IMPLEMENTATION_MAP.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.5_EXECUTION_REPORT.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.6_CANONICAL_MATHEMATICAL_REPRESENTATION_AND_SAFE_PARSING.md`

## CAP / REQ / TD Mapping

- `CAP-PROBLEM-002`: Sprint 4.6 closes the canonical mathematical representation portion by converting accepted parser output into versioned canonical/verifier JSON.
- `REQ-CAPTURE-004`: OCR/AI parser text remains untrusted until deterministic canonical parsing succeeds.
- `REQ-DATA-001`: canonical problem records are durable user-derived data with FK ownership, export, deletion, and idempotency semantics.
- `REQ-VERIFY-001`: deterministic verifier input uses a bounded typed AST, never raw model strings.
- `TD-AI-001` and `TD-AI-002`: real provider validation and parser accuracy calibration remain open; Sprint 4.6 does not require new AI calls.

## Sprint 4.5 Handoff

Sprint 4.5 provides immutable `problem_parses` rows with `problem-parse-v1` normalized JSON, parser provenance, support status, review signals, variables, expressions, constraints, assumptions, and recognition evidence references. Sprint 4.6 consumes only selected/latest accepted parser output and never reads raw OCR/provider strings directly.

## Canonical Boundary

`ProblemParse` remains Layer 3: semantic interpretation and user-correctable parse fields.

`CanonicalProblem` is Layer 4: a backend-owned, immutable, versioned mathematical structure prepared for deterministic verifier services and later solver work.

Canonicalization must not:
- solve a problem,
- verify an answer,
- assign `VERIFIED`,
- classify primary skill or difficulty,
- silently simplify away mathematical restrictions,
- expose internal verifier endpoints or secrets to iOS.

## V1 Scope

Initial canonical support is intentionally narrow:
- arithmetic/algebra expressions,
- single equations,
- single inequalities,
- explicit parser constraints that can be represented as typed relations,
- declared variables from the accepted parse.

Unsupported or unsafe canonicalization fails explicitly with INPUT-category errors. Calculus, systems, solution sets, units, diagrams, probability, statistics, linear algebra, and multi-part structures remain represented at the parse layer only until later sprints define their canonical semantics.

## Typed AST Contract

Schema versions:
- `canonical-problem-v1`
- `verifier-input-v1`

AST node discriminators:
- `NUMBER`
- `VARIABLE`
- `UNARY`
- `BINARY`
- `FUNCTION`

Operators:
- `NEGATE`
- `ADD`
- `SUBTRACT`
- `MULTIPLY`
- `DIVIDE`
- `POWER`

Functions:
- `SQRT`
- `SIN`
- `COS`
- `TAN`
- `LOG`
- `EXP`

Numbers are exact strings, not binary floats. Supported literal kinds are integer and decimal. Rational values are represented structurally through `DIVIDE` or by explicit numerator/denominator fields where the schema uses a rational literal.

## Variable and Constraint Rules

Variables must be declared by `ProblemParse.variables[]` and match the safe identifier policy. The canonical layer rejects unknown identifiers instead of inventing variables.

Source-explicit constraints remain separate from derived restrictions:
- `sourceConstraints`: user/source-backed parser constraints.
- `derivedRestrictions`: deterministic safety restrictions discovered by canonical parsing.

Denominators produce explicit `DENOMINATOR_NON_ZERO` restrictions. Product denominators are decomposed into separate factor restrictions where possible, and no algebraic cancellation removes restrictions.

Default variable domain is `UNKNOWN` unless source-backed parser data states a stricter supported domain.

## Complexity Policy

Sprint 4.6 defines v1 limits because the canonical docs do not provide exact thresholds:
- max expression length: 512 characters,
- max AST nodes: 120,
- max AST depth: 32,
- max exponent magnitude for numeric exponents: 12,
- max numeric literal digits: 64,
- max function nesting depth: 8.

The same limits are enforced in the backend canonicalizer and in the Python verifier contract validator. Metrics record complexity rejections without logging problem text.

## Database Migration

Committed migrations currently stop at `V010__create_problem_parse_lifecycle.sql`. The worktree already contains an unrelated untracked auth migration named `V011__create_email_and_guest_identity_auth.sql`, so Sprint 4.6 uses:

`V012__create_canonical_problem_lifecycle.sql`

The migration creates `canonical_problems` with:
- owner/session/parse foreign keys,
- source parse id and revision,
- canonical revision,
- schema versions,
- problem type and task type columns,
- `canonical_problem_jsonb`,
- `verifier_input_jsonb`,
- `display_jsonb`,
- idempotency uniqueness on parse revision and schema,
- revision uniqueness per problem session,
- JSON object checks and access-pattern indexes.

## API / OpenAPI

Add:
- `POST /api/v1/problem-sessions/{sessionId}/canonicalize`
- `GET /api/v1/problem-sessions/{sessionId}/canonical-problem`

Public DTOs expose lifecycle-safe metadata and display fields only. They must not expose raw parser output, raw OCR, or internal verifier endpoints.

## Python Verifier Contract

The math verifier gains a typed `verifier-input-v1` validator that accepts only the shared AST contract and converts accepted nodes through explicit constructors. Raw OCR/model strings are not accepted by the canonical verifier path.

Existing internal string-equivalence code remains guarded compatibility behavior, but Sprint 4.6 tests exercise the typed AST path as the production handoff contract for later verifier work.

## Privacy / Security / Observability

Canonical math is user-derived problem data. Account export includes canonical/verifier JSON and display metadata; raw parser provider output remains excluded. Account deletion cascades canonical rows through `problem_sessions`.

Metrics include success, failure, unsupported parse, complexity rejection, and canonicalization latency. Labels are low-cardinality and do not contain student expressions.

## Tests

Focused Sprint 4.6 coverage:
- pure backend parser precedence, exact numbers, identifiers, limits, unsafe tokens, unknown functions,
- canonical application idempotency, revision behavior, denominator restrictions, source constraints, unsupported parse behavior, privacy export/deletion,
- API authentication/authorization and no raw AST exposure,
- shared JSON Schema validation for `canonical-problem-v1` and `verifier-input-v1`,
- Python verifier typed AST valid fixtures and adversarial invalid fixtures,
- Flyway migration presence and constraints.

## Dirty Worktree Isolation

Pre-existing auth/iOS edits are unrelated and must not be reverted or committed with Sprint 4.6. Shared files such as `packages/contracts/openapi/public-api.yaml`, `services/api/src/test/java/com/verifiedai/FlywayMigrationTest.java`, and `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java` require careful diff review and hunk-level staging if Sprint 4.6 touches them.

## Exit Gate

Before completion, run the focused Sprint 4.6 tests plus the repository gates requested by the execution prompt. The iOS gate must distinguish CoreSimulator sandbox permission failures from actual test hangs by using an elevated `xcrun simctl bootstatus <UDID> -b` check when necessary.
