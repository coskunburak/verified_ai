# Async Processing and Job Orchestration

## Why solving is asynchronous

A solve may involve upload processing, vision parsing, two AI solver calls, arbitration, deterministic verification and explanation generation. Latency is variable and providers may retry or fail. A single long synchronous HTTP request is fragile.

## Public pattern

`POST /api/v1/problem-sessions/{id}/solve`

Returns `202 Accepted` with a `solveJobId` and semantic status.

Client polls initially; SSE can be added later.

Sprint 4.4 adds the same durable async pattern for recognition:

`POST /api/v1/problem-sessions/{id}/recognition`

Returns `202 Accepted` with recognition job status. The worker reads the selected OCR-optimized derivative, invokes provider-neutral `VISION_PARSE`, validates untrusted provider JSON, persists RecognitionEvidence, and exposes status through `GET /api/v1/problem-sessions/{id}/recognition`.

## Durable job schema

Suggested fields:
- id,
- job_type,
- aggregate_id,
- status,
- stage,
- attempt_count,
- max_attempts,
- next_attempt_at,
- locked_by,
- locked_until,
- created_at,
- started_at,
- finished_at,
- error_code,
- trace_id.

## Pipeline stages

1. PARSE
2. CLASSIFY
3. PRIMARY_SOLVE
4. SECONDARY_SOLVE
5. ARBITRATE
6. VERIFY
7. EXPLAIN
8. FINALIZE

Each stage persists its result before moving forward so retries do not recreate prior expensive work unnecessarily.

Recognition is a pre-parse ingestion stage. It records raw visual evidence only and must not advance directly to `PARSED`, `SOLVING`, `VERIFYING`, or `COMPLETED`.

## Idempotency

Repeated command with the same idempotency key returns/reuses the same logical operation. AI provider calls themselves may not be idempotent; our orchestration layer must make business output idempotent.

## Retry policy

Retry transient failures:
- timeouts,
- 429,
- selected provider 5xx,
- temporary verifier unavailability.
- temporary object-storage or recognition-provider unavailability.

Do not blindly retry:
- invalid user input,
- unsupported problem,
- schema-invalid after bounded regeneration attempts,
- auth/entitlement failures.
- malformed recognition geometry or schema-invalid recognition output after bounded attempts.

Use exponential backoff + jitter.

## Claiming jobs

V1 can use PostgreSQL-backed workers with row claiming (`FOR UPDATE SKIP LOCKED`) or an appropriate durable job library.

Kafka is intentionally not required in V1.

## Failure states

After retry exhaustion:
- FAILED for operational failure,
- REVIEW_REQUIRED for ambiguous/quality condition where user intervention may help.

Preserve trace, partial artifacts and safe error code.

## Progress UX

Expose stages rather than fake percentages:
- Reading problem,
- Understanding,
- Solving,
- Verifying,
- Preparing explanation.

## Cancellation

User cancellation marks job state and prevents downstream work. Already in-flight third-party calls may complete, but late results are ignored safely.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Future batch ML/evaluation workloads

Model evaluation, dataset materialization and training are not user-facing solve jobs. They must use isolated worker/compute paths, bounded credentials and explicit job provenance. Production solve queue capacity must not be consumed by offline training/evaluation work.
<!-- HYBRID_AI_STRATEGY_V3:END -->
