# Sprint 4.5 Execution Report - Structured Problem Parser and Versioned Schema

## Execution Status

`NOTEBOOKLM_MCP_STATUS = CONNECTED`

NotebookLM query `b55c51fdc9d5` completed during the original Sprint 4.5 implementation and confirmed the core boundary: `ProblemParse` is an intermediate structured interpretation, not canonical safe mathematics, a verifier AST, a solution, a skill classification, or a correction experience. Final closure query `9b05850af364` confirmed the same ownership: Sprint 4.5 owns schema-validated parser-level representation and immutable parse revision persistence; Sprint 4.6 owns canonical safe math; Sprint 4.10 owns formal ingestion/parser accuracy gates. No NotebookLM outage fallback was required; local canonical docs were still used for exact repository paths, domain invariants, and implementation details.

Sprint 4.5 is complete locally. Sprint 4.6 readiness is ready because the documented Sprint 4.5 local exit gates now pass and the remaining real-provider/calibration risks are explicitly tracked as non-blocking deferred validation debt for launch readiness and Sprint 4.10.

## Repository Baseline

- Original Sprint 4.5 implementation baseline: `8570441 feat: complete sprint 4.4 vision recognition evidence`
- Committed Sprint 4.5 implementation: `b04d678 feat: complete sprint 4.5 structured problem parsing`
- Final closure working tree: mixed. Sprint 4.5 semantic hardening is present alongside unrelated auth/iOS work and must be staged by exact files/hunks only.
- Sprint 4.4 handoff: durable `recognition_evidence` rows with normalized blocks, evidence revision, upstream quality evidence, provider/model/prompt/schema provenance, usage, cost, latency, uncertainty, and privacy lifecycle participation.

Sprint 4.5 consumes `RecognitionEvidence` by `(recognition_evidence_id, recognition_evidence_revision)` and never re-reads source object storage or treats OCR text as a canonical problem.

## Canonical Sources

Major sources used:

- `domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md`
- `domain/07_DOMAIN_MODEL_AND_AGGREGATES.md`
- `domain/09_DOMAIN_EVENTS_AND_STATE_MACHINES.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md`
- `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`
- `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md`
- `ai/26_PROMPT_SCHEMA_AND_VERSIONING.md`
- `ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md`
- `ai/29_AI_COST_LATENCY_AND_RELIABILITY.md`
- `architecture/13_ASYNC_PROCESSING_AND_JOB_ORCHESTRATION.md`
- `architecture/14_API_DESIGN_AND_CONTRACTS.md`
- `architecture/51_ERROR_TAXONOMY_AND_RECOVERY_CONTRACT.md`
- `backend/20_BACKEND_MODULE_CONTRACTS.md`
- `data/22_POSTGRESQL_DATA_MODEL.md`
- `data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md`
- `operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md`
- `operations/39_RUNBOOKS_AND_FAILURE_MODES.md`
- `operations/48_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- Sprint 4.1 through Sprint 4.4 implementation maps and execution reports.

## Capability Coverage

- `CAP-PROBLEM-002`: Partial overall because Sprint 4.6 still owns canonical safe math and verifier representation. Sprint 4.5's parser-level sub-scope is complete locally, including relation semantic hardening and explicit source-backed assumptions.
- `CAP-AI-001`: Partial. `AiModelGateway` now supports `VISION_PARSE` and `PROBLEM_NORMALIZE`; full model gateway governance remains Sprint 5.1.
- `CAP-AI-002`: Partial. `problem-parser/v001` and `problem-parse-v1` are added; full prompt registry/release workflow remains Sprint 5.2.
- `CAP-AI-003`: Partial. Parser timeout, bounded retry, fallback, cost, usage, latency, and route provenance are implemented; full optimization remains Sprint 5.3.
- `CAP-PRIV-001`: Partial overall. Parser rows are covered by export/deletion policy, but future learning/tutoring stores remain pending.

## Requirement Traceability

- `REQ-AI-001`: Foundation extended to `PROBLEM_NORMALIZE`; provider SDKs remain isolated in AI infrastructure adapters.
- `REQ-PRIV-001`: Maintained. Raw parser output and problem text are not log/analytics payloads.
- `REQ-PRIV-002`: Extended. Parser data participates in account export/deletion.
- `REQ-PROBLEM-001`: Maintained. `ProblemAsset`, `RecognitionEvidence`, `ProblemParse`, canonical problem, verifier representation, and solution remain separate.
- `REQ-PROBLEM-003`: Added and locally satisfied for the Sprint 4.5 foundation. Accepted parser output must pass JSON parsing, strict schema validation, parser semantic validation, relation semantic validation, source-backed assumption validation, provenance checks, and supported/review/unsupported handling.

## AI Architecture

The implemented dependency path is:

```text
problem application
 -> ai.application AiModelGateway
 -> PROBLEM_NORMALIZE
 -> ai.infrastructure ProblemNormalizeProviderAdapter
```

`AiCapability` now includes `VISION_PARSE` and `PROBLEM_NORMALIZE`. The gateway exposes `executeProblemNormalize` with provider-neutral request/result/provenance/usage records. The local fixture provider is deterministic for tests and local development; production startup rejects enabled parser routing when `LOCAL_FIXTURE` is configured as primary.

## ProblemParse Domain

`ProblemParse` is a provider-independent, parser-level interpretation. It may contain subject/topic, task type, problem type, expression text/display notation, variables, explicit constraints, explicit assumptions, uncertainty, visual quality risks, and source evidence references.

It may not contain a safe executable AST, SymPy representation, verifier input, verified answer, solution steps, primary skill, difficulty, or user correction decision.

## Revision Model

`problem_parses` is append-only. Revisions are monotonic within a `problem_session_id` and are assigned while holding a pessimistic lock on the owning problem session. Re-running the same user/session/evidence revision/capability/prompt/schema/route input reuses the existing logical job. New evidence revisions or route/prompt/schema changes can create later immutable revisions. Sprint 4.5 writes only AI-sourced revisions.

Concurrency validation passed with unique session revisions and idempotent logical jobs.

## Database Changes

Migration `V010__create_problem_parse_lifecycle.sql` adds:

- Unique provenance constraint on `recognition_evidence(id, user_id, problem_session_id, revision)`.
- `problem_parse_jobs`.
- `problem_parses`.
- Same-owner composite FKs to `problem_sessions` and `recognition_evidence`.
- Logical idempotency uniqueness for parse jobs.
- Unique parse revision per `problem_session_id`.
- JSON object checks for raw and normalized output.
- Status, capability, source, unsupported-reason, usage, latency, and cost checks.
- Due/running/session/user/evidence indexes.

Flyway V001 through V010 validation passed.

## Parser Schema

- Schema ID: `https://schemas.verified-ai-learning.example/problem-parse.schema.json`
- Schema version: `problem-parse-v1`
- Location: `packages/schemas/problem-parse.schema.json`

The schema is provider-independent and disallows additional properties. It models support status, subject/topic, task/problem type, expressions, variables, constraints, assumptions, uncertainty, source evidence references, visual quality risks, and review requirement.

Final closure hardening fixes parser assumptions to source-backed explicit assumptions only: `assumptions[].explicit` is `const: true` in JSON Schema and `enum: [true]` in OpenAPI.

## Parser Prompt

- Prompt ID: `problem-parser`
- Prompt version: `v001`
- Location: `services/api/src/main/resources/prompts/problem-parser/v001.md`

The prompt forbids solving, answers, hints, verifier ASTs, SymPy expressions, skill/difficulty assignment, hidden constraint inference, fabricated source references, URL/tool/code execution, and instructions embedded in recognition text.

## Raw vs Normalized Output

`raw_output_jsonb` stores untrusted provider JSON and is not exposed by ordinary APIs or included in normal account export payloads. `normalized_problem_jsonb` stores only the normalized parser result after JSON, schema, semantic, domain invariant, provenance, and supported-scope validation.

## Supported Scope Matrix

Supported parser-level categories:

- Arithmetic evaluate/simplify.
- Algebraic expression simplify/evaluate.
- Single equation solve representation.
- Basic inequality solve representation.
- Function value representation.
- Basic limit representation.
- Basic derivative representation.
- Basic integral representation.

Unsupported or review-only examples include systems, piecewise, multi-part work, diagram-dependent geometry, text-heavy probability/physics, linear algebra, and structures that `problem-parse-v1` cannot faithfully represent.

## Subject, Topic, Task, and Problem Type

Subject validation currently allows `MATH`.

Topic validation currently allows:

- `MATH.ARITHMETIC`
- `MATH.ALGEBRA`
- `MATH.EQUATIONS`
- `MATH.FUNCTIONS`
- `MATH.CALCULUS.LIMITS`
- `MATH.CALCULUS.DIFFERENTIATION`
- `MATH.CALCULUS.INTEGRATION`

Task types:

- `EVALUATE`
- `SIMPLIFY`
- `SOLVE_EQUATION`
- `SOLVE_INEQUALITY`
- `FIND_FUNCTION_VALUE`
- `FIND_LIMIT`
- `DIFFERENTIATE`
- `INTEGRATE`

Problem types:

- `ARITHMETIC_EXPRESSION`
- `ALGEBRAIC_EXPRESSION`
- `EQUATION`
- `INEQUALITY`
- `FUNCTION`
- `LIMIT`
- `DERIVATIVE`
- `INTEGRAL`

## Expression Representation

Expressions store parser-level `sourceText`, `normalizedText`, optional display LaTeX, relation, role, and source block IDs. This is Layer 3 style semantic text only. It is not a Sprint 4.6 safe AST, not an executable expression, and not deterministic verifier input.

Variables must be declared explicitly and match expression/constraint references. Constraints are accepted only when explicit and source-linked. Assumptions preserve explicit-source semantics and are not treated as hidden mathematical facts.

Final closure hardening validates task/relation semantics: `SOLVE_EQUATION` requires at least one expression with `relation = EQUALS`; `SOLVE_INEQUALITY` requires at least one expression with an inequality relation.

## Provenance and Uncertainty

Every parse job and parse revision stores exact `recognition_evidence_id` and `recognition_evidence_revision`. Structured source references must point at real recognition block IDs. Recognition uncertainty, parser uncertainty, and visual quality risks remain separate fields and are not collapsed into a fake confidence score.

## Validation Behavior

Schema-invalid output is rejected and retried when retry budget remains. Semantic-invalid output is terminal and creates no durable accepted parse revision. Unsupported content is a durable accepted parser outcome with a stable unsupported reason. Ambiguous content is a durable `REVIEW_REQUIRED` outcome with parser/recognition uncertainty preserved.

Semantic validation rejects unknown subject/topic values, unsupported task/problem combinations, missing expressions for supported output, equation/inequality task relation mismatches, mismatched variables, constraints referencing unknown variables, fabricated source block IDs, unsupported output without a reason, and unsupported reasons on supported parses. Schema validation rejects parser assumptions unless they are explicitly source-backed.

## Error Taxonomy

Stable API error codes added:

- `PROBLEM_PARSE_FAILED`
- `PROBLEM_UNSUPPORTED`

Parse job failure classes include `SCHEMA_INVALID`, `SEMANTIC_INVALID`, provider failure classes such as timeout/rate-limit/unavailable, and retry exhaustion through the job state machine.

## Provider Routing, Cost, and Latency

Default local route:

- Capability: `PROBLEM_NORMALIZE`
- Primary provider: `LOCAL_FIXTURE`
- Route policy: `problem-parser-route-v1`
- Schema: `problem-parse-v1`
- Pricing version: `problem-parser-local-v1`
- Max attempts: `2`
- Timeout: `PT20S`
- Max response bytes: `65536`

Each durable parse stores provider, model, route, prompt, schema, fallback flag, request units, token/image units when available, provider latency, total latency, estimated cost in micros, currency, and pricing version.

## OpenAPI

New public contracts:

- `POST /api/v1/problem-sessions/{sessionId}/parse`
- `GET /api/v1/problem-sessions/{sessionId}/parse`
- `ProblemParseResponse`
- `NormalizedProblemParse`
- `ProblemParseExpression`
- `ProblemParseVariable`
- `ProblemParseConstraint`
- `ProblemParseAssumption`
- `ProblemParseUncertainty`
- `ProblemParseSourceEvidenceRef`
- `ProblemParseVisualQualityRisk`

Responses expose safe lifecycle/provenance/normalized parse state and do not expose raw parser output.

## iOS Changes

iOS now has parser DTOs, API methods, upload port methods, parse status states, `startParse`, `retryParse`, `observeParse`, parse result application, non-editable "Understand Problem" UI, and review/unsupported/failure rendering. The UI deliberately does not implement Sprint 4.8 correction or user-selected semantics.

## Privacy, Export, and Deletion

Parser rows are user-owned student-derived content. Account export includes normalized parse data and safe provenance, with `rawProblemParserOutputIncluded = false`. Account deletion cascades parser jobs and parses through the owning problem session. Logs and metrics exclude problem text, expressions, raw JSON, object keys, and user/session IDs.

## Security

Model output is treated as untrusted data. Prompt-injection text in recognition evidence cannot override the parser prompt, call tools, fetch URLs, execute code, request secrets, mutate state, solve the problem, or change route configuration. Output size, schema fields, enum values, source references, and bounded arrays are validated before persistence.

## Observability

Parser metrics:

- `ai.problem.parse.started.total`
- `ai.problem.parse.success.total`
- `ai.problem.parse.unsupported.total`
- `ai.problem.parse.failure.total`
- `ai.problem.parse.schema_invalid.total`
- `ai.problem.parse.semantic_invalid.total`
- `ai.problem.parse.fallback.total`
- `ai.problem.parse.provider.duration`
- `ai.problem.parse.total.duration`
- `ai.problem.parse.estimated_cost_micros`

Analytics/runbook docs were updated for parser started/completed/review/unsupported/failure semantics without high-cardinality or raw content labels.

## Golden Parser Evaluation Seed

Location: `evaluations/parser/golden/problem-parse-v1-seed.json`

Synthetic fixture count: `33`

- Supported: `9`
- Unsupported: `6`
- Ambiguous/review-required: `5`
- Schema-invalid: `7`
- Semantic-invalid: `5`
- Security/prompt-injection: `1`

The seed is synthetic only and is not production student content, a protected holdout, or training-eligible data.

## Validation Results

Focused Sprint 4.5 backend suite:

- `ConfiguredAiModelGatewayTest`: 5 passed.
- `ProblemParseApplicationServiceTest`: 11 passed.
- `ProblemParseControllerTest`: 2 passed.
- `FlywayMigrationTest`: 4 passed.
- `ModularityTest`: 1 passed.
- Total: 23 passed, 0 failed, 0 errors, 0 skipped.

Full validation:

- `make test-api`: 103 passed, 0 failed, 0 errors, 0 skipped.
- `make test-ios`: 74 passed, 0 failed, result `** TEST SUCCEEDED **`.
- `make test-verifier`: 13 passed, 1 dependency deprecation warning.
- `make contracts-check`: pass.
- `docker compose config --quiet`: pass.
- `make secret-scan`: pass.
- `make lint`: pass.
- `make doctor`: pass with existing local `gh` CLI warning.
- `git diff --check`: pass.

iOS final gate clarification:

- Sandboxed `xcrun simctl bootstatus` can fail because the sandbox cannot access CoreSimulator. That is a local permission boundary, not a product simulator boot failure and not an XCTest hang.
- Elevated `xcrun simctl bootstatus 56E87D46-F64B-4AC7-AB87-1D94F5C9F3D0 -b`: pass, terminal boot readiness reached in 9 seconds during final closure.
- `xcodebuild test-without-building -only-testing:VerifiedAITests`: pass during final closure investigation.
- Full `make test-ios`: pass, 74 tests, no remaining `xcodebuild`, XCTest runner, or hanging iOS test process after completion.
- The intermediate 11:03 build failure was caused by accidental summary prose inserted into `KeychainStore.swift`; that accidental source insertion was removed, `KeychainStore.swift` no longer carries that incident diff, and the current iOS gate is pass.

Local provider validation passed through deterministic fixture provider tests and end-to-end parse application tests. Real provider validation is blocked by `TD-AI-001` and `TD-AI-003`.

## Specific Acceptance Evidence

- Schema-invalid validation: retryable parser failure, no parse revision.
- Semantic-invalid validation: terminal parser failure, no parse revision.
- Equation task without equation relation validation: terminal semantic parser failure, no parse revision.
- Inequality task without inequality relation validation: terminal semantic parser failure, no parse revision.
- Non-explicit parser assumption validation: retryable schema parser failure, no parse revision.
- Unsupported validation: durable `UNSUPPORTED` parse with stable reason.
- Ambiguous validation: durable `REVIEW_REQUIRED` parse with uncertainty.
- Revision validation: immutable monotonic revisions and concurrency uniqueness.
- Ownership validation: cross-user parse access rejected.
- Privacy validation: export includes normalized parse only; deletion removes parser rows.
- Spring Modulith validation: pass after exposing curriculum application interface.
- Architecture drift: no provider SDK dependency introduced in problem module.

## Technical Debt

Existing open debt:

- `TD-AI-001`: real vision provider validation remains open.
- `TD-AI-002`: recognition accuracy calibration remains open.

New open debt:

- `TD-AI-003`: real parser provider validation.
- `TD-AI-004`: parser accuracy calibration.

Closed debt: none.

Release blockers: none for local Sprint 4.5 exit. Real provider/parser calibration debts block production parser enablement, not Sprint 4.6 local readiness. There is no open iOS simulator hang debt; current evidence distinguishes sandbox CoreSimulator permission failure from simulator readiness and XCTest execution.

## Documentation Changes

Updated canonical documentation spans domain docs, AI orchestration/prompt/evaluation/cost docs, architecture async/API/error docs, backend module contracts, data model/lifecycle docs, privacy/security docs, observability/runbook/analytics docs, capability matrix, requirements traceability, technical debt register, evaluations README, schemas README, implementation map, execution report, master index, and manifest.

## Git Finalization

Follow-up Sprint 4.5 semantic-hardening commit is permitted only with exact staging. The working tree contains unrelated auth/iOS work, including mixed changes in `packages/contracts/openapi/public-api.yaml`; the OpenAPI assumption contract must be staged by hunk, not by whole-file `git add`.

## Sprint 4.6 Readiness

`SPRINT_4.6_READINESS = READY`

Sprint 4.6 can consume immutable, schema-valid, semantically coherent, provenance-preserving `ProblemParse` revisions and proceed to canonical safe mathematical representation without treating parser text as executable verifier input.
