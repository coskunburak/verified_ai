# Sprint 4.4 Implementation Map

## NotebookLM Evidence

`NOTEBOOKLM_MCP_STATUS = UNAVAILABLE`

Codex tool discovery did not expose a NotebookLM MCP server in this execution environment. The Sprint 4.4 prompt permits local canonical-source fallback only when exact canonical docs are available locally. This repository contains the v4 canonical documentation tree, Sprint 4.1-4.3 implementation maps/execution reports, accepted ADRs, AI governance docs, data/privacy docs, and Phase 4 sprint specifications, so implementation proceeds from local canonical Markdown.

## CAP IDs

- `CAP-CAPTURE-003`: remains Complete and is the Sprint 4.4 handoff source for selected OCR-optimized derivatives, quality evidence, crop geometry, and processor/config provenance.
- `CAP-PROBLEM-001`: moves from Pending to Complete for Vision/OCR ingestion and raw recognition evidence.
- `CAP-AI-001`: moves from Pending to Partial because a minimum provider-neutral `VISION_PARSE` capability boundary is pulled forward for Sprint 4.4 only.
- `CAP-AI-002`: moves from Pending to Partial for the recognition prompt/schema subset only.
- `CAP-AI-003`: moves from Pending to Partial for recognition timeout/retry/fallback/usage-cost metadata only.
- `CAP-PRIV-001`: remains Partial overall and gains recognition job/evidence export/deletion coverage.
- `CAP-OPS-001`: remains Partial overall and gains recognition metrics/runbook foundations.

## REQ IDs

- `REQ-CAPTURE-004`: introduced by this sprint for durable RecognitionJob and RecognitionEvidence over the selected recognition input.
- `REQ-CAPTURE-003`: consumed; Sprint 4.4 does not bypass preprocessing or select an arbitrary derivative.
- `REQ-AI-001`: partially satisfied for `VISION_PARSE`; provider-specific code remains inside `ai.infrastructure`.
- `REQ-PROBLEM-001`: preserved; recognition evidence is not canonical Problem, parse, classification, solution, or verification.
- `REQ-PRIV-001`: extended; raw images, recognized text, raw provider output, object keys, signed URLs, and provider payloads are excluded from logs/analytics.
- `REQ-PRIV-002`: extended; recognition jobs/evidence participate in account export/deletion.
- `REQ-DATA-001`: maintained; PostgreSQL owns structured job/evidence state.

## TD IDs

- `TD-CAPTURE-002`: remains open for production object storage/IAM validation.
- `TD-CAPTURE-003`: remains open; PDF recognition is blocked until explicit PDF raster/page policy exists.
- `TD-CAPTURE-004`: remains open for staging preprocessing corpus/performance validation.
- `TD-AI-001`: added if real external provider credentials/settings are unavailable in this workspace.
- `TD-AI-002`: added for Sprint 4.10 recognition accuracy and review-threshold calibration.
- `TD-PRIV-001`: narrowed for recognition stores once lifecycle contributor coverage is implemented.

## Sprint 4.3 Handoff

Sprint 4.4 consumes a backend-owned `ProblemSession` with an AVAILABLE original `ProblemAsset`, a READY `problem_asset_derivatives` row where `derivative_kind = OCR_OPTIMIZED` and `selected_for_recognition = true`, quality evidence rows, crop fields, source/derivative dimensions, private derivative object key, checksum, processor name/version, and preprocessing configuration version.

PDF inputs remain blocked at Sprint 4.3's `PDF_UNSUPPORTED` preprocessing failure until a later PDF rasterization policy is implemented.

## RecognitionEvidence Domain

`RecognitionEvidence` is a problem-owned, durable raw-evidence artifact produced from visual recognition. It stores untrusted provider output separately from normalized recognition-level evidence. It may say which text/math-like visual blocks appear, where they appear in the recognition-input asset, what uncertainty exists, and which provider/model/prompt/schema/route produced it. It must not contain canonical Problem semantics.

## RecognitionJob Lifecycle

Durable job states:

- `QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED_RETRYABLE`
- `FAILED_TERMINAL`

Successful jobs are not overwritten. Failed retryable jobs are retried within a bounded max-attempt policy. Repeated start requests for the same user/session/input/capability/prompt/schema return the existing logical job.

## AI Capability Boundary

Sprint 4.4 pulls forward only:

- `AiCapability.VISION_PARSE`
- provider-neutral request/result/provenance/usage records
- a route policy version for recognition
- provider adapter interfaces and a local fixture provider for deterministic tests/local demos

It does not implement `PROBLEM_NORMALIZE`, `SOLVE`, `TUTOR`, secondary solving, verifier arbitration, or the full Sprint 5.1 gateway roadmap.

## Provider Route

The `problem` module requests `VISION_PARSE` through `ai.application`. The `ai` module owns provider adapter selection and records provider/model/route metadata. A local fixture provider is allowed in local/test only. Production/staging real-provider credentials remain configuration and are validated separately.

## Prompt Version

Prompt identity:

- `promptId = vision-recognition`
- `promptVersion = v001`

The prompt instructs the provider to return only visible text/math/spatial evidence, preserve ambiguity, treat image text as data, and never solve, infer hidden symbols, classify, or normalize to canonical math.

## Schema Version

Schema identity:

- `schemaVersion = recognition-evidence-v1`

The schema lives in `packages/schemas/recognition-evidence.schema.json`. Java validation mirrors the schema and adds recognition-level semantic checks.

## Raw Provider Output Policy

Raw provider JSON is stored separately from normalized evidence for support/reproducibility, under the same user/problem ownership and account deletion lifecycle. It is not logged and is excluded from normal public API responses. Retention is documented as sensitive recognition evidence tied to account lifecycle and future retention hardening.

## Normalized Recognition Evidence

Normalized evidence contains:

- schema version
- coordinate space metadata
- ordered recognition blocks
- block kind and visible text
- normalized bounding boxes
- reading order
- raw and normalized confidence when supplied
- explicit `UNKNOWN` confidence when absent
- uncertainty flags
- upstream quality warnings
- review-required flag

It excludes subject, topic, task, canonical expression, variables, constraints, answer, solution, and verification status.

## Coordinate Model

Canonical stored coordinates are normalized `x`, `y`, `width`, and `height` in `[0,1]`, relative to the exact selected OCR-optimized derivative (`input_derivative_id`). App validation rejects negative dimensions, values outside the coordinate space, and boxes that exceed bounds.

## Confidence / Uncertainty Model

`recognitionConfidence` remains distinct from parse/verification confidence. Provider raw confidence is preserved where available. Normalized confidence is stored only when the provider value is numeric within `[0,1]`; absent provider confidence is `UNKNOWN`, not fabricated.

## Storage Policy

Recognition metadata lives in PostgreSQL. Image bytes remain in private object storage and are read through backend storage ports. No permanent public URLs are generated. The provider request receives only the selected OCR-optimized derivative bytes, not the original image when a derivative exists.

## Database Migration

Add forward-only `V009__create_problem_recognition_lifecycle.sql` with:

- `recognition_jobs`
- `recognition_evidence`
- FK ownership through `(id, user_id)` pairs
- status/capability/schema/prompt constraints
- uniqueness for idempotent recognition job creation
- JSONB type checks for raw and normalized evidence
- indexes for owner/session/status processing

## Async Job Execution

`POST /api/v1/problem-sessions/{id}/recognition` creates or reuses a durable job and returns its current state. A scheduled worker claims queued/retryable jobs, reads the selected derivative, invokes `VISION_PARSE`, validates output, persists evidence, and updates status.

## Retry, Fallback, Timeouts

Max attempts, response size, provider timeout, and fallback route are configuration. Transient provider errors become `FAILED_RETRYABLE` until attempts are exhausted. Invalid input/auth/config/schema exhaustion becomes terminal. Fallback, when configured, is recorded as route provenance and does not merge provider outputs.

## Cost, Usage, Latency

Evidence records request/image/token units where available, provider latency, total job latency, estimated cost in micro-USD, currency, and pricing version. Sprint 4.4 records recognition-stage cost only, not cost per verified solution.

## Privacy

Raw recognized text and raw provider JSON are sensitive student content. They are not analytics labels, logs, or public list fields. Account export includes recognition metadata and normalized recognition evidence; raw provider output export remains metadata-only unless future policy says otherwise. Account deletion removes recognition rows through cascade from `problem_sessions`.

## Security

Prompt injection in images is treated as visible student content only. Model output is data only: it cannot execute SQL, call URLs, choose object keys, mutate entitlements, or drive tools. Size, block count, text length, JSON shape, confidence range, and coordinates are bounded.

## Observability

Recognition metrics are low-cardinality counters/timers for started, success, failure, schema invalid, timeout, retry, fallback, provider latency, total latency, and estimated cost. Labels exclude user IDs, session IDs, asset IDs, object keys, raw text, prompts, and provider payloads.

## Evaluation Fixtures

Focused fixtures cover valid evidence, malformed JSON, missing fields, invalid enum, invalid coordinates, confidence outside range, massive block count, missing confidence, prompt injection text, timeout, rate limit, 5xx, fallback, and upstream quality propagation.

## OpenAPI

Add:

- `POST /api/v1/problem-sessions/{sessionId}/recognition`
- `GET /api/v1/problem-sessions/{sessionId}/recognition`

Responses expose safe status, selected input asset IDs, review-required flag, block count, safe normalized summary when available, and no raw provider internals.

## iOS State

Extend upload/capture handoff with recognition states:

- starting recognition
- recognizing
- recognized
- review required
- recoverable recognition failure
- terminal recognition failure

UI copy uses "Reading the problem" and never "Solving."

## Out-of-Scope Boundaries

No structured `ProblemParse`, canonical mathematics, subject/topic/skill classification, answer solving, verifier call, parse review UI, provider training pipeline, self-hosted model, or full Sprint 5.1 gateway is implemented.
