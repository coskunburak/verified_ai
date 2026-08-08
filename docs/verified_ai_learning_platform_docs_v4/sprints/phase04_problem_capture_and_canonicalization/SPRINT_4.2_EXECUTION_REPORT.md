# Sprint 4.2 Execution Report - Presigned Asset Upload and Object Lifecycle

## Executive Summary

Sprint 4.2 is complete locally. A Sprint 4.1 `AcceptedCapturedAsset` can now become a durable, authenticated, user-owned backend `ProblemAsset` through backend reservation, private S3-compatible object upload, and backend SHA-256/size/content-type verification. The sprint stopped at AVAILABLE raw asset state and did not implement preprocessing, OCR, parsing, canonical mathematics, solving, or verification.

## NotebookLM MCP Status

`CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`

NotebookLM connected to notebook `00e25adc-dee0-4a9c-92d8-6c473d1ad2ac`; full source bodies were limited, so canonical repository Markdown supplied exact implementation traceability.

## Canonical Sources

Primary sources were the Phase 4 Sprint 4.2 spec, Sprint 4.1 implementation/report handoff, domain invariants, API/error contracts, backend module contracts, data model, storage lifecycle, privacy/security docs, observability/runbooks, RTM, capability matrix, and technical debt register.

## Repository Baseline

- Phase 3 baseline: `aa48259 feat: complete phase 3 identity account and commerce foundation`
- Phase 3 tag: `phase-3-complete`
- Sprint 4.1 checkpoint created before Sprint 4.2: `670140a feat: complete sprint 4.1 problem capture`

## Sprint 4.1 Handoff

Consumed `AcceptedCapturedAsset` as local iOS temporary capture/import state with source, kind, local file URL, bytes, crop metadata, dimensions/page metadata, and privacy-safe local lifecycle semantics. It was not treated as backend truth.

## Scope

Implemented presigned reservation, direct object upload, `ProblemSession`, `ProblemAsset`, lifecycle state, object-storage adapter, checksum/size/type validation, idempotency, iOS upload state/progress/retry, local cleanup after durable success, privacy lifecycle, rate limits, metrics, OpenAPI, tests, and docs.

## Out-of-Scope

No preprocessing, perspective correction, OCR, recognition evidence, parser, canonical `Problem`, classification, solving, verification, or derived assets were implemented.

## CAP Mapping

- `CAP-CAPTURE-002`: Complete.
- `CAP-CAPTURE-001`: Partial; `TD-CAPTURE-001` remains open.
- `CAP-PRIV-001`: Partial overall; problem asset export/deletion coverage added.
- `CAP-OPS-001`: Partial overall; upload operational metrics added.

## REQ Mapping

- `REQ-CAPTURE-002`: Satisfied.
- `REQ-PROBLEM-001`: Foundation; asset is distinct from canonical problem.
- `REQ-PRIV-001`: Foundation; no raw content logging/analytics.
- `REQ-PRIV-002`: Foundation; problem asset lifecycle contributor implemented.
- `REQ-AUTH-002`, `REQ-BILL-001`, and `REQ-DATA-001`: maintained.

## TD Mapping

- `TD-CAPTURE-001`: open real-device Sprint 4.1 validation.
- `TD-PRIV-001`: open for future stores; problem asset subcase closed.
- `TD-CAPTURE-002`: open production/staging object-storage validation debt.

## Domain Model

`problem` owns the durable upload lifecycle. PostgreSQL is the source of truth for session, asset, owner, metadata, and lifecycle state; object storage holds private raw bytes only.

## ProblemSession Model

Minimal Sprint 4.2 state is `CREATED -> ASSET_UPLOADED`. Later Phase 4 states are represented in the schema but not advanced by this sprint.

## ProblemAsset Model

`ProblemAsset` owns asset ID, owner, session relation, source type, asset kind, object key, content type, size, expected SHA-256, crop/dimension/page metadata, retention class, upload expiration, uploaded/available timestamps, and idempotency fingerprint.

## State Machines

Backend asset states covered by tests: `PENDING`, `AVAILABLE`, and `EXPIRED`; invalid non-pending completion returns stable upload state errors. iOS phases are `idle`, `reserving`, `uploading(progress)`, `confirming`, `available`, and `recoverableFailure`.

## Database Migration

Added `V007__create_problem_asset_upload_lifecycle.sql` with `problem_sessions` and `problem_assets`, composite owner FK, status/source/kind/content-type/checksum/crop/size constraints, unique object key, unique user reservation idempotency, and indexes for session lookup, owner history, status, and pending-expiry cleanup.

## Object Storage Architecture

Added provider-neutral `ProblemAssetStorage` and S3-compatible adapter with local MinIO/Testcontainers coverage. Default local bucket is `verified-ai-problem-assets-local`; production encryption/IAM/CORS/lifecycle evidence is tracked by `TD-CAPTURE-002`.

## Presigned Upload Architecture

Reservation validates the authenticated user and request, creates server-owned object identity, stores PENDING metadata, and returns a 15-minute presigned PUT URL plus required headers. The iOS client uploads directly to storage, then calls completion; backend verifies object metadata/checksum before AVAILABLE.

## Object Key Policy

Object keys are backend-generated as `problem-assets/{problemSessionId}/{problemAssetId}/original`. The client never supplies bucket, key, prefix, or authoritative storage identity.

## Checksum Policy

iOS computes SHA-256 over local bytes and sends lower-case hex in the reservation. Completion streams the private object from storage and compares the SHA-256 before marking AVAILABLE. Mismatch deletes the object and leaves the asset non-AVAILABLE.

## Content-Type Policy

Supported Sprint 4.2 content types are `image/jpeg` and `application/pdf`. Content type is checked during reservation and again against object metadata during completion.

## Size Policy

Maximum original upload size is 20 MB. Reservation rejects invalid/oversized size; completion rejects and deletes uploaded objects whose actual size differs from the reservation.

## Idempotency

Reservation idempotency is scoped by user plus `Idempotency-Key` and guarded by a request hash. Completion is idempotent after success and returns the existing AVAILABLE asset reference on repeat calls.

## Ownership

The backend derives user ownership from the authenticated JWT principal. Completion queries by `(uploadId, userId)` under pessimistic lock, and the database enforces `(problem_session_id, user_id)` ownership consistency.

## iOS Upload Architecture

Added `ProblemAssetUploadModels`, `ProblemAssetUploadPorts`, `ProblemAssetUploadAPI`, `PresignedObjectUploader`, and `ProblemAssetUploadViewModel`. `AppDependencies`, `RootView`, and `ProblemCaptureView` now orchestrate reservation, direct PUT, completion, success handoff, retry, and cleanup.

## Upload State Machine

Accepted local asset flow:

```text
idle -> reserving -> uploading(progress) -> confirming -> available
```

Recoverable failures keep the accepted local asset for retry.

## Retry Strategy

Offline, reservation, object PUT, and completion failures enter recoverable failure state. Retry reuses stable idempotency keys and local bytes; success deletes local temporary files.

## Offline Behavior

New durable uploads are unavailable offline. The local accepted asset is retained until connectivity returns and backend completion succeeds.

## Temporary File Cleanup

Local raw temp asset cleanup occurs only after backend confirms AVAILABLE. Failure, cancellation before durable success, and offline state preserve local bytes for explicit retry or existing capture-flow cleanup.

## Privacy Lifecycle

Problem asset metadata participates in account export and deletion. Raw binaries are excluded from export and deleted from object storage during confirmed account deletion.

## Account Export

Export category `problemAssets` includes session/asset metadata and `rawBinaryIncluded=false`.

## Account Deletion

Confirmed deletion deletes matching private object keys through `ProblemAssetStorage`, then deletes problem session rows with asset cascade.

## Security Controls

Controls include active-account checks, server capability guard, principal-derived ownership, no client-controlled object key, content-type/size/checksum validation, reservation TTL, cleanup of mismatched objects, dedicated upload rate limits, and stable problem detail errors.

## Rate Limits

- `POST /api/v1/uploads/presign`: 30 requests per 10 minutes, fail-open.
- `POST /api/v1/uploads/{uploadId}/complete`: 60 requests per 10 minutes, fail-open.

## Observability

Added privacy-safe Micrometer metrics: `problem.asset.reservation.success.total`, `problem.asset.reservation.failure.total`, `problem.asset.upload.complete.success.total`, `problem.asset.upload.complete.failure.total`, `problem.asset.checksum_mismatch.total`, `problem.asset.size_mismatch.total`, `problem.asset.pending_expired.total`, `problem.asset.presign.latency`, and `problem.asset.completion.verification.latency`.

## Runbooks

Updated runbooks for problem asset upload failure, storage outage/mismatch handling, and account deletion inspection of problem asset rows/object keys.

## Tests

- `make test-api`: PASS, 57 tests.
- `make test-ios`: PASS, 66 tests.
- Backend coverage includes application, API, privacy lifecycle, rate-limit, Flyway, and S3-compatible MinIO integration tests.
- iOS coverage includes six upload ViewModel tests plus full existing iOS scheme regression.

## Local MinIO Validation

PASS. The S3-compatible adapter test creates a MinIO bucket, generates a presigned PUT URL, uploads bytes through Java `HttpClient`, verifies HEAD metadata, streams SHA-256, deletes the object, and confirms not-found behavior.

## Production Storage Validation

NOT_RUN. No production/staging bucket, IAM credentials, encryption/CORS/lifecycle policy, or regional settings were available in this workspace. Tracked as `TD-CAPTURE-002`.

## Integration Demo

Automated local evidence covers iOS fixture metadata/checksum -> API reservation -> presigned storage PUT -> backend completion -> AVAILABLE `ProblemAsset` with `ProblemSession` `ASSET_UPLOADED`. The MinIO adapter test validates the direct object-storage leg.

## Known Limitations

Real-device camera validation remains open from Sprint 4.1. Production/staging object-storage validation remains open for launch readiness. Background upload continuation across process death is not implemented; retry is explicit and local bytes are preserved during recoverable failures.

## Documentation Changes

Updated canonical API, data model, storage, privacy, security, observability, runbook, analytics, capability, RTM, debt, master index, and documentation tree files. Added this report and `SPRINT_4.2_IMPLEMENTATION_MAP.md`.

## Git Status

Sprint 4.1 checkpoint commit exists. Sprint 4.2 changes are intended for a separate checkpoint commit after validation.

## Sprint 4.2 Exit Decision

`SPRINT_4.2 = COMPLETE`

All minimum exit gates are met locally, with production object-storage validation recorded as launch-readiness debt rather than implementation blocker.

## Sprint 4.3 Readiness

`SPRINT_4.3_READINESS = READY`

Sprint 4.3 can consume AVAILABLE raw assets, crop metadata, capture metadata, checksum, and object references to create provenance-linked derived assets. It must not assume OCR, parse, canonical problem, solution, or verification exists.
