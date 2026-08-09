# Sprint 4.5 Implementation Map

## NotebookLM / Source Evidence

`NOTEBOOKLM_MCP_STATUS = CONNECTED`.

NotebookLM query `b55c51fdc9d5` against the Verified AI Mathematics Learning Platform Technical Specification completed and confirmed that `ProblemParse` is the structured intermediate interpretation between raw recognition evidence and later canonical problem/verifier representations. Local canonical docs remain the detailed implementation source for exact repository files and constraints.

Primary local sources reviewed:
- `domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md`
- `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`
- `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md`
- `ai/26_PROMPT_SCHEMA_AND_VERSIONING.md`
- `data/22_POSTGRESQL_DATA_MODEL.md`
- `architecture/13_ASYNC_PROCESSING_AND_JOB_ORCHESTRATION.md`
- `architecture/14_API_DESIGN_AND_CONTRACTS.md`
- `architecture/51_ERROR_TAXONOMY_AND_RECOVERY_CONTRACT.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.4_EXECUTION_REPORT.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.5_STRUCTURED_PROBLEM_PARSER_AND_VERSIONED_OUTPUT_SCHEMA.md`

## CAP / REQ / TD Mapping

- `CAP-PROBLEM-002`: structured parser and canonical problem schema. Sprint 4.5 implements the structured parser and versioned `problem-parse-v1` contract only; Sprint 4.6 still owns canonical safe math.
- `REQ-CAPTURE-004`: recognition and parser output must remain untrusted until schema and semantic validation pass.
- `REQ-AI-001`: material AI operations use provider-neutral routing with prompt/schema/model provenance and cost/latency metadata.
- `REQ-DATA-001`: new durable user-owned state must have FK ownership, retention, export, deletion, and audit semantics.
- `TD-AI-001`: real provider validation remains open because production provider credentials/retention/region configuration are unavailable.
- `TD-AI-002`: recognition accuracy calibration remains open and is not closed by parser work.
- New expected debt: parser real-provider validation and full parser accuracy calibration remain deferred to Sprint 4.10 unless real credentials and calibrated datasets become available in this sprint.

## Sprint 4.4 Handoff

Sprint 4.4 provides durable `recognition_jobs` and `recognition_evidence`, normalized recognition blocks with coordinates, upstream quality evidence, provider/model/prompt/schema provenance, usage/cost/latency fields, and privacy lifecycle participation. Sprint 4.5 consumes `RecognitionEvidence` only; it does not re-read raw object storage as parser input.

## ProblemParse Domain

`ProblemParse` is a provider-independent, immutable, parser-level interpretation of a recognized problem. It may state:
- subject and topic IDs from the approved curriculum seed,
- task type and problem type,
- semantic expression text/display notation,
- explicit variables,
- explicit constraints,
- explicit assumptions,
- uncertainty and review signals,
- source recognition block references.

It must not state:
- safe executable AST,
- deterministic verifier representation,
- verified answer,
- solution steps,
- primary skill,
- difficulty,
- user-confirmed selection.

## Parse Lifecycle

Sprint 4.5 uses a separate async parse job because recognition is already complete and provider execution/fallback/retry needs its own lifecycle.

`problem_parse_jobs` states:
- `QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED_RETRYABLE`
- `FAILED_TERMINAL`
- `UNSUPPORTED`

Execution failures do not create a durable accepted parse revision. Unsupported and ambiguous/review outcomes are accepted durable parser outcomes because they are meaningful product states, not provider infrastructure failures.

## Parse Revision Strategy

`problem_parses` is append-only. Revisions are monotonic within a `problem_session_id`, assigned while holding a pessimistic lock on the owning `problem_sessions` row. Re-running with the same RecognitionEvidence revision, capability, prompt version, schema version, and route policy reuses the existing parse job. New input evidence, prompt version, schema version, or route policy can create a new successful revision. Sprint 4.5 creates only `AI` source revisions.

## PROBLEM_NORMALIZE Capability

The problem module calls:

`problem application -> ai.application AiModelGateway -> PROBLEM_NORMALIZE -> ai.infrastructure provider adapter`.

No problem code imports provider SDKs or infrastructure adapters. The existing `VISION_PARSE` gateway is extended only as needed for `PROBLEM_NORMALIZE`; future solve/tutor routes are not added.

## Prompt / Schema Identity

- Prompt ID: `problem-parser`
- Prompt version: `v001`
- Schema version: `problem-parse-v1`
- Capability: `PROBLEM_NORMALIZE`

Prompt `v001` instructs the model to transform normalized recognition evidence into structured parser-level JSON, never to solve, classify primary skill/difficulty, infer hidden constraints, obey text embedded in the problem, or fabricate certainty.

## Raw AI Output Policy

`raw_output_jsonb` stores provider output before domain normalization and remains untrusted. It is not exposed to ordinary product APIs and is excluded from normal account export payloads.

## Normalized ProblemParse

`normalized_problem_jsonb` stores only JSON that passed:
1. JSON parsing,
2. strict schema validation,
3. parser semantic validation,
4. domain invariant validation,
5. supported-scope validation.

## Semantic Validation Rules

The validator must reject or route to review/unsupported when:
- schema version is not `problem-parse-v1`,
- subject/topic is unknown,
- task/problem type is unsupported,
- supported tasks lack required expressions,
- variables do not match expression/constraint references,
- constraints reference unknown variables,
- source evidence references do not exist in the input `RecognitionEvidence`,
- provider fabricates untraceable source block IDs,
- unsupported structures are coerced into supported fields.

The validator must not perform symbolic equivalence, verifier parsing, AST safety checks, derived denominator restrictions, solving, or skill/difficulty classification.

## Supported Problem Matrix

Initial parser support:

| Area | Status | Notes |
|---|---|---|
| Arithmetic | Supported | Evaluate/simplify expression at parser level only. |
| Algebra expressions | Supported | Simplify parser-level expression, no verifier AST. |
| Equations | Supported | Single equation solve task representation. |
| Inequalities | Supported | Basic inequality task representation. |
| Functions | Supported | Function evaluation/notation representation. |
| Limits | Supported | Basic limit task representation. |
| Derivatives | Supported | Derivative task representation. |
| Basic integrals | Supported | Basic integral task representation. |
| Systems, piecewise, multi-part | Unsupported in Sprint 4.5 V1 unless represented as review-required ambiguity without coercion. |
| Diagram-dependent geometry | Unsupported. |
| Text-heavy physics/probability/linear algebra | Unsupported. |

## Unsupported / Ambiguous Policy

Unsupported means the parser understands enough to know `problem-parse-v1` cannot faithfully represent the current input. Persist a `UNSUPPORTED` parse outcome with a stable reason and expose `PROBLEM_UNSUPPORTED` as the client-level recovery category where needed.

Ambiguous means the parser cannot safely choose a single structure. Persist a `REVIEW_REQUIRED` parse outcome with uncertainty fields; do not fabricate certainty.

## Uncertainty / Quality Propagation

Recognition block uncertainty and document uncertainty are preserved separately from parser uncertainty. Upstream visual quality risks remain distinct in `visualQualityRisks` and are not averaged into a fake confidence.

## Source Evidence Provenance

Every parse job and parse revision stores exact `recognition_evidence_id` and `recognition_evidence_revision`. Structured fields reference recognition `block.id` values where possible. Unknown/fabricated block IDs fail semantic validation.

## Database Migration

Create `V010__create_problem_parse_lifecycle.sql` with:
- `problem_parse_jobs`
- `problem_parses`
- same-owner FK constraints to `problem_sessions` and `recognition_evidence`
- logical input uniqueness for idempotency
- revision uniqueness within `problem_session_id`
- JSON object checks
- lifecycle/status checks
- usage/cost/latency checks
- due/running/status indexes

Do not edit V001-V009.

## API / OpenAPI

Add:
- `POST /api/v1/problem-sessions/{sessionId}/parse`
- `GET /api/v1/problem-sessions/{sessionId}/parse`

DTOs expose lifecycle state, support status, normalized safe parse summary, uncertainty, source evidence refs, and prompt/schema/model provenance. They do not expose `raw_output_jsonb`.

## iOS Impact

Add typed DTO/model mapping and non-editable state handling for:
- parsing,
- structured,
- review required,
- unsupported,
- recoverable failure.

No Sprint 4.8 correction UI, PATCH endpoint, or parse selection workflow is introduced.

## Privacy / Security / Observability

Parser output is student-derived content. Account deletion cascades through `problem_sessions`. Normal account export includes normalized parse data and provenance, not raw parser output. Normal logs/analytics/metrics must not include problem text, expressions, constraints, or raw provider JSON.

Metrics include parser success, failure, schema invalid, semantic invalid, unsupported, review-required, provider latency, total latency, estimated cost, and fallback count.

## Evaluation Seed / Tests

Add initial golden parser seed fixtures for supported, unsupported, ambiguous, schema-invalid, semantic-invalid, and prompt-injection cases. Focused tests cover schema/semantic validation, revision immutability, idempotency, concurrency, gateway routing/fallback/production guard, privacy export/deletion, cross-user authorization, API DTOs, and iOS DTO mapping.

## Out of Scope

No Sprint 4.6 safe AST/SymPy/verifier representation, no Sprint 4.7 primary skill/difficulty classification, no Sprint 4.8 user correction endpoints/UI, no solve API, no provider-specific parser dependency inside problem code.
