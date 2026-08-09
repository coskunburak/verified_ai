# Sprint 4.4 Execution Report - Vision/OCR Ingestion and Raw Evidence

## Executive Summary

Sprint 4.4 is complete locally. The platform now creates durable recognition jobs from the selected READY `OCR_OPTIMIZED` derivative, invokes a provider-neutral `VISION_PARSE` AI boundary, treats provider JSON as untrusted input, validates and normalizes recognition-level evidence, and persists raw/normalized evidence with coordinates, uncertainty, provenance, usage, cost, and latency.

The sprint deliberately stops before structured `ProblemParse`, canonical mathematics, subject/topic/skill classification, solving, tutoring, or verification.

## NotebookLM MCP Status

`NOTEBOOKLM_MCP_STATUS = UNAVAILABLE`

Tool discovery did not expose a NotebookLM MCP server in this execution environment. The local canonical documentation tree was available and read directly, including Phase 4 sprint docs, Sprint 4.1-4.3 implementation evidence, ADRs, AI governance, domain invariants, backend/API/data/security/ops docs, capability matrix, RTM, and technical debt register.

## Canonical Sources

Primary sources were `00_MASTER_INDEX.md`, `DOCUMENTATION_MANIFEST.md`, AI docs `25` through `29` and `57` through `61`, domain docs `06` through `10`, architecture docs `13`, `14`, and `51`, backend docs `20` and `21`, data docs `22` through `24`, security docs `35` and `36`, operations docs `37`, `39`, `48`, `70`, `71`, roadmap docs `43`, `67`, `68`, RTM `69`, and Sprint 4.1-4.4 Phase 4 docs.

## Repository Baseline

- Starting commit: `39c42b0 feat: complete sprint 4.3 image preprocessing and capture quality`
- Starting tree: clean.
- Pre-start `git diff --check`: pass.
- Sprint 4.3 execution report declared `SPRINT_4.3 = COMPLETE` and `SPRINT_4.4_READINESS = READY`.

## Sprint 4.3 Handoff

Sprint 4.4 consumes a backend-owned `ProblemSession`, AVAILABLE source `ProblemAsset`, selected READY `problem_asset_derivatives` row with `derivative_kind = OCR_OPTIMIZED` and `selected_for_recognition = true`, private derivative object key, checksum/dimensions, crop geometry, processor/config versions, and upstream quality evidence.

## Scope

Implemented recognition jobs, recognition evidence, forward-only migration, minimum `VISION_PARSE` AI capability boundary, local deterministic provider, configured gateway/fallback support, prompt/schema artifacts, schema and semantic validation, async worker, OpenAPI endpoints, iOS recognition states, privacy lifecycle integration, metrics, tests, and documentation synchronization.

## Out-of-Scope

No `ProblemParse`, canonical math representation, subject/topic/skill/difficulty classification, answer solving, verification status, parse correction UI, tutor behavior, training pipeline, self-hosted model, or full Sprint 5.1 model router was implemented.

## CAP Mapping

- `CAP-CAPTURE-003`: remains Complete and is consumed as the selected derivative/quality boundary.
- `CAP-PROBLEM-001`: Complete for raw OCR/vision ingestion evidence.
- `CAP-AI-001`: Partial; minimum `VISION_PARSE` provider-neutral boundary pulled forward.
- `CAP-AI-002`: Partial; recognition prompt/schema subset added.
- `CAP-AI-003`: Partial; recognition timeout/retry/fallback/usage/cost metadata added.
- `CAP-PRIV-001`: remains Partial overall, extended for recognition export/deletion.
- `CAP-OPS-001`: remains Partial overall, extended with recognition metrics/runbook foundations.

## REQ Mapping

- `REQ-CAPTURE-004`: Satisfied.
- `REQ-CAPTURE-003`: preserved and consumed.
- `REQ-AI-001`: Foundation for `VISION_PARSE`; full gateway remains Sprint 5.1.
- `REQ-PROBLEM-001`: preserved; evidence is not canonical Problem or parse.
- `REQ-PRIV-001`: maintained; raw recognized content is not log/analytics material.
- `REQ-PRIV-002`: extended for recognition job/evidence lifecycle.
- `REQ-DATA-001`, `REQ-AUTH-002`, and `REQ-BILL-001`: maintained.

## TD Mapping

- `TD-AI-001`: open for real external provider credentials, retention settings, region settings, and smoke validation.
- `TD-AI-002`: open for Sprint 4.10 representative recognition accuracy calibration.
- `TD-CAPTURE-002`: remains open for production object storage/IAM validation.
- `TD-CAPTURE-003`: remains open for PDF page/raster policy.
- `TD-CAPTURE-004`: remains open for staging preprocessing corpus/performance validation.
- `TD-PRIV-001`: narrowed for recognition stores; remains open for future stores.

## Recognition Domain

`RecognitionJob` is the durable async lifecycle record. `RecognitionEvidence` is the problem-owned raw evidence artifact. It may describe visible text/math-like blocks, coordinates, reading order, layout hints, recognition confidence, uncertainty, and provider provenance. It cannot assert canonical problem meaning.

## RecognitionJob Lifecycle

States are `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED_RETRYABLE`, and `FAILED_TERMINAL`. Jobs are idempotent by user, ProblemSession, selected input derivative, capability, prompt ID/version, and schema version. Stale RUNNING jobs are recoverable by worker policy.

## RecognitionEvidence Model

Raw provider JSON and normalized evidence are stored separately. Successful evidence records input derivative/source/session IDs, revision, schema version, provider/model, request/response IDs where safe, prompt and route identity, usage units, estimated cost, currency, pricing version, provider latency, total latency, and upstream quality evidence.

## Database Migration

`V009__create_problem_recognition_lifecycle.sql` adds:

- `recognition_jobs`
- `recognition_evidence`
- same-owner composite FKs to session/source/derivative
- idempotent job uniqueness
- JSONB object checks
- status/capability/prompt/schema constraints
- processing and owner/session indexes

Flyway/Testcontainers validated V001 through V009.

## Selected Recognition Input

The selected input is the explicit READY `OCR_OPTIMIZED` derivative marked `selected_for_recognition = true`. The recognition path does not query the latest derivative or send the original upload when a selected derivative exists.

## AI Capability Boundary

The dependency direction is:

```text
problem
 -> ai.application AiModelGateway
 -> ai.infrastructure provider adapter
```

Problem code has no OpenAI, Gemini, Apple Vision, or provider SDK dependency.

## Early Sprint 5.1 Subset Decision

Sprint 4.4 pulls forward only `AiCapability.VISION_PARSE`, route plan, provider-neutral request/result/provenance/usage records, configured gateway, provider adapter interface, unavailable adapter, and local fixture adapter. Full solver/tutor/mistake-classifier routing and model release governance remain Sprint 5.1+.

## Provider Routing

Configured route defaults to local fixture in local/test, with optional fallback provider configuration. Production startup rejects enabled recognition when `LOCAL_FIXTURE` is configured as primary.

## Providers Implemented

- `LOCAL_FIXTURE`: deterministic local/test provider.
- `UNAVAILABLE`: explicit disabled/unavailable adapter behavior.
- Real external provider: not configured in this workspace.

## Prompt Design

Prompt ID/version:

```text
vision-recognition / v001
```

The prompt asks only for visible recognition evidence, coordinates, reading order, layout hints, and uncertainty. It forbids solving, classification, correction, hidden-symbol inference, canonical math, or obeying visual prompt-injection text.

## Schema Design

Schema:

```text
recognition-evidence-v1
packages/schemas/recognition-evidence.schema.json
```

Validation enforces JSON object shape, schema version, block count, enum values, text length, confidence range, reading order, and normalized coordinate bounds.

## Source Coordinates

Coordinates are normalized `x`, `y`, `width`, `height` values in `[0,1]`, relative to the exact selected input derivative. Invalid boxes are rejected and do not create durable normalized evidence.

## Reading Order / Spatial Evidence

Each normalized block preserves explicit `readingOrder` and optional layout hints. Array order is not treated as the only source of reading order.

## Confidence

Provider raw and normalized recognition confidence are preserved where supplied and valid. Missing confidence is stored as `UNKNOWN`; the system does not invent `0.5` or treat recognition confidence as parse/verification confidence.

## Uncertainty

Uncertainty is explicit at block and document level. Review is required for low/unknown confidence, uncertainty flags, unreadable/unknown blocks, upstream quality warnings, or provider-declared review requirements.

## Quality Evidence Propagation

Sprint 4.3 quality warnings are copied into recognition evidence as upstream capture risk. Blur/glare/crop/resolution risk is not conflated with OCR confidence.

## Async Processing

`POST /api/v1/problem-sessions/{sessionId}/recognition` creates/reuses a job and returns `202`. The scheduled worker reads the selected private derivative, calls `VISION_PARSE`, validates output, persists evidence, and updates status. `GET /api/v1/problem-sessions/{sessionId}/recognition` returns safe status/evidence.

## Retry

Retryable provider/storage failures and schema invalid output remain `FAILED_RETRYABLE` until max attempts are exhausted. Terminal configuration, unsupported payload, or exhausted attempts become terminal.

## Timeout / Fallback

Route config includes timeout and max attempts. Gateway tests prove retryable primary timeout can use fallback and terminal invalid-auth failure does not. No real secondary provider is configured in this workspace.

## Idempotency / Concurrency

Database uniqueness prevents duplicate logical jobs for the same user/session/input/capability/prompt/schema. Successful evidence is tied immutably to the input derivative, so stale work from an older crop/derivative cannot become evidence for a newer input.

## Cost / Usage / Latency

Evidence records input/output tokens where available, image units, request units, estimated cost in micro-USD, pricing version, provider latency, and total job latency. Sprint 4.4 records ingestion cost only, not cost per verified solution.

## Privacy

Raw provider output may contain student content and is excluded from normal API responses and normal export payloads. Normalized evidence and recognition metadata are exported. Recognition rows are deleted with the owning problem session. No provider-side deletion guarantee is claimed without real provider configuration.

## Security

Recognition output is data only. It cannot execute SQL/code, fetch URLs, choose storage keys, mutate entitlement, call tools, or override prompt policy. Response size, block count, text length, confidence, and coordinates are bounded. Provider secrets remain backend-only.

## Prompt Injection Defense

Prompt-injection fixture text is recognized as visible evidence only. It does not alter schema, trigger solving, return secrets, or execute any action.

## Observability

Added low-cardinality metrics:

- `ai.vision.recognition.started.total`
- `ai.vision.recognition.success.total`
- `ai.vision.recognition.failure.total`
- `ai.vision.recognition.timeout.total`
- `ai.vision.recognition.schema_invalid.total`
- `ai.vision.recognition.fallback.total`
- `ai.vision.recognition.provider.latency`
- `ai.vision.recognition.total.latency`
- `ai.vision.recognition.estimated_cost_micros`

Labels exclude user IDs, session IDs, asset IDs, object keys, raw text, prompt text, and provider payloads.

## API Contracts

OpenAPI now includes:

- `POST /api/v1/problem-sessions/{sessionId}/recognition`
- `GET /api/v1/problem-sessions/{sessionId}/recognition`

Responses expose job status, input IDs, review-required flag, blocks, provenance metadata, and no raw provider internals.

## iOS Recognition State

iOS upload state now supports starting recognition, recognizing, recognized, review required, and recognition failure states. Polling is bounded. User copy says "Read Problem" / "Reading the problem" and does not say "Solving".

## Tests

- Focused Sprint 4.4 backend/gateway tests: PASS, `10` tests, `0` failures.
- Full backend/API suite: PASS, `80` tests, `0` failures.
- Spring Modulith boundary: PASS inside API suite, `1` test.
- Flyway migration chain: PASS inside API suite, `2` tests, V001 through V009.
- Focused iOS recognition/upload tests: PASS, `10` tests, `0` failures.
- Full iOS suite: PASS, `70` tests, `0` failures on iPhone 16 Pro simulator.
- Math verifier regression: PASS, `13` tests, `1` existing Starlette/httpx deprecation warning.
- `doctor`: PASS, with non-blocking `gh` not installed warning.
- `lint`: PASS.
- `docs-check`: PASS, `256` Markdown files.
- `contracts-check`: PASS.
- `secret-scan`: PASS.
- `docker compose config --quiet`: PASS.
- `git diff --check`: PASS.

## Fixtures

Fixtures cover valid evidence, invalid coordinates, missing confidence, prompt injection text, upstream quality warning propagation, fallback on retryable timeout, terminal invalid auth, production fake-provider rejection, account export, and deletion cascade.

## Local Provider Validation

`LOCAL_FAKE_PROVIDER_TEST = PASS`

Deterministic local fixture recognition path produces schema-valid raw evidence, normalized blocks, provenance, usage, cost, latency, and success status.

## Real Provider Validation

`REAL_PROVIDER_TEST = BLOCKED`

No real external provider credentials, retention/region settings, or provider-route configuration are available in this workspace. Tracked as `TD-AI-001`.

## Failure Injection

- Schema/coordinate invalid output: rejected, job becomes `FAILED_RETRYABLE`, no evidence row.
- Missing confidence: accepted as `UNKNOWN`, review required.
- Retryable primary timeout: fallback succeeds and provenance marks fallback used.
- Terminal invalid auth: no fallback, exception remains terminal.

## Integration Demo

`ProblemRecognitionApplicationServiceTest.requestRecognitionCreatesIdempotentJobAndWorkerPersistsEvidence` demonstrates the local end-to-end flow: ready selected derivative, recognition request, idempotent job reuse, worker execution, provider-neutral result, raw/normalized evidence separation, normalized coordinates, confidence, provenance, usage/cost, and final `SUCCEEDED` status.

## Known Limitations

Real provider integration is not configured. Recognition accuracy is not calibrated against a representative golden dataset. PDF recognition remains blocked by PDF preprocessing/raster policy. The unified future AI usage ledger remains Sprint 5.1+.

## Documentation Changes

Updated domain model/events/invariants, ADR-003, AI routing/prompt/cost docs, async/API/error contracts, backend module/rate-limit docs, PostgreSQL data model, data lifecycle/storage docs, privacy/threat/observability/runbook/analytics docs, iOS architecture, capability matrix, RTM, TD register, master index, OpenAPI, schema/prompt READMEs, implementation map, and this report.

## Git Status

Sprint 4.4 changes are intended for checkpoint commit:

```text
feat: complete sprint 4.4 vision recognition evidence
```

## Sprint Exit Decision

`SPRINT_4.4 = COMPLETE`

All local code, schema, migration, architecture, privacy, and documentation gates are satisfied. Real provider validation and recognition accuracy calibration are tracked as non-blocking launch-readiness debt.

## Sprint 4.5 Readiness

`SPRINT_4.5_READINESS = READY`

Sprint 4.5 can consume durable RecognitionEvidence with normalized blocks, source coordinates, reading order/layout hints, uncertainty, quality evidence, provider/model provenance, prompt/schema identity, and usage/cost/latency metadata without reading raw storage objects directly.
