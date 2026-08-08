# Sprint 4.2 Implementation Map

## NotebookLM Evidence

`NOTEBOOKLM_MCP_STATUS = CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`

NotebookLM MCP connected to notebook `00e25adc-dee0-4a9c-92d8-6c473d1ad2ac`, titled `Verified AI Mathematics Learning Platform Technical Specification`; query conversation `3a773319-fb5c-4dce-bea2-a0dfc776e75e` was used before implementation. NotebookLM confirmed the Sprint 4.2 boundary but did not provide full source bodies, so checked-in canonical Markdown remained the complete source-of-truth read path.

Resolved semantic questions:

- `AcceptedCapturedAsset` is a local iOS handoff artifact from Sprint 4.1.
- `ProblemAsset` is durable raw asset metadata and private object-storage evidence, not canonical `Problem`.
- `ProblemSession` begins in Sprint 4.2 only as minimal ownership/lifecycle foundation.
- The backend owns asset identity, object key, owner, state, allowed content type, size, TTL, and checksum expectation.
- Sprint 4.2 supports original JPEG images and PDFs; preprocessing, OCR, parsing, canonical math, solving, verification, and derived assets remain later sprints.

## Canonical Sources Consulted

- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.2_PRESIGNED_ASSET_UPLOAD_AND_OBJECT_STORAGE_LIFECYCLE.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.1_IMPLEMENTATION_MAP.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.1_EXECUTION_REPORT.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `domain/07_DOMAIN_MODEL_AND_AGGREGATES.md`
- `architecture/14_API_DESIGN_AND_CONTRACTS.md`
- `architecture/51_ERROR_TAXONOMY_AND_RECOVERY_CONTRACT.md`
- `backend/20_BACKEND_MODULE_CONTRACTS.md`
- `backend/21_AUTHORIZATION_RATE_LIMITING_AND_ABUSE.md`
- `data/22_POSTGRESQL_DATA_MODEL.md`
- `data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- `data/24_CACHING_STORAGE_AND_FILE_ASSETS.md`
- `ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`
- `security/35_SECURITY_THREAT_MODEL.md`
- `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md`
- `operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md`
- `operations/39_RUNBOOKS_AND_FAILURE_MODES.md`
- `operations/48_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md`

## Repository Baseline

- Phase 3 baseline commit: `aa48259 feat: complete phase 3 identity account and commerce foundation`
- Phase 3 tag: `phase-3-complete`
- Sprint 4.1 checkpoint before new work: `670140a feat: complete sprint 4.1 problem capture`

## Ownership And Scope

Owning bounded context: `problem`.

In scope:
- authenticated upload reservation;
- minimal `ProblemSession` lifecycle `CREATED -> ASSET_UPLOADED`;
- `ProblemAsset` persistence, state, metadata, retention, idempotency, and owner enforcement;
- backend-generated private object keys;
- S3-compatible object-storage port/adapter;
- presigned PUT with 15-minute TTL;
- SHA-256, size, and content-type verification before AVAILABLE;
- iOS direct upload, progress, retry, offline recovery, and local cleanup after durable success;
- privacy export/deletion lifecycle;
- OpenAPI, metrics, rate limits, tests, and docs.

Out of scope:
- image preprocessing;
- perspective correction;
- OCR/Vision;
- recognition evidence;
- parser/canonical math;
- classification;
- solving;
- verification;
- derived assets.

## CAP Mapping

- `CAP-CAPTURE-002`: `Complete` after Sprint 4.2 implementation and validation.
- `CAP-CAPTURE-001`: remains `Partial`; real-device capture/import evidence remains `TD-CAPTURE-001`.
- `CAP-PRIV-001`: remains `Partial` overall, but problem asset lifecycle is covered.
- `CAP-OPS-001`: remains `Partial` overall, with upload metrics added.

## REQ Mapping

- `REQ-CAPTURE-002`: `Satisfied`.
- `REQ-PROBLEM-001`: `Foundation`; Sprint 4.2 keeps raw asset metadata separate from canonical problem state.
- `REQ-PRIV-001`: `Foundation`; upload logs/metrics avoid raw content.
- `REQ-PRIV-002`: `Foundation`; problem asset export/deletion contributor implemented.
- `REQ-AUTH-002`: continues to apply; owner is principal-derived.
- `REQ-BILL-001`: continues to apply; backend capability guard remains authoritative.
- `REQ-DATA-001`: continues to apply; PostgreSQL owns structured state.

## TD Mapping

- `TD-CAPTURE-001`: open, unchanged.
- `TD-PRIV-001`: open for future stores; problem asset subcase implemented.
- `TD-CAPTURE-002`: new open launch-readiness debt for production/staging bucket validation.

## Backend Implementation Plan

- Migration `V007__create_problem_asset_upload_lifecycle.sql`.
- Domain enums and ports under `services/api/src/main/java/com/verifiedai/problem/domain/**`.
- Persistence entities/repositories under `problem/infrastructure/persistence`.
- Storage configuration and S3-compatible adapter under `problem/infrastructure/storage`.
- Application service `ProblemAssetUploadApplicationService` for reservation, completion, verification, cleanup, and lifecycle rules.
- Thin controller and DTOs under `problem/api`.
- Public billing capability port `CapabilityAccessPolicy`.
- Rate-limit policies for upload reservation and completion.
- OpenAPI contract updates in `packages/contracts/openapi/public-api.yaml`.

## iOS Implementation Plan

- Add upload domain models and ports in `ProblemCapture/Domain`.
- Add backend API client and presigned object uploader in `ProblemCapture/Data`.
- Add `ProblemAssetUploadViewModel` in `ProblemCapture/Presentation`.
- Wire upload state into `RootView`, `AppDependencies`, and `ProblemCaptureView`.
- Preserve local asset on reservation, upload, completion, or offline failures.
- Delete local temporary bytes only after backend AVAILABLE response.

## State Machines

Backend `ProblemSession` Sprint 4.2 transition:

```text
CREATED -> ASSET_UPLOADED
```

Backend `ProblemAsset` Sprint 4.2 transitions:

```text
PENDING -> AVAILABLE
PENDING -> EXPIRED
PENDING -> FAILED
```

iOS upload phases:

```text
idle
reserving
uploading(progress)
confirming
available
recoverableFailure
```

## Test Evidence Required

- PostgreSQL/Flyway migration test from V001 to latest.
- Backend application tests for reservation, completion, idempotency, owner attack, checksum/size/content-type failures, expiry, cleanup, and privacy deletion.
- API tests for auth, idempotency header handling, and HTTP contracts.
- MinIO/S3-compatible integration test for presigned PUT, HEAD metadata, SHA-256 stream, and delete.
- iOS upload ViewModel tests for success, metadata/checksum, PDF metadata, offline failure, object PUT failure, and retry after completion failure.
- Full API and iOS suite regression.

## Rollout And Recovery

Local rollout uses Docker Compose MinIO configuration and Testcontainers evidence. Production rollout requires real bucket/IAM/encryption/CORS/lifecycle evidence before TestFlight upload enablement, tracked as `TD-CAPTURE-002`. Recovery path is to retry from retained local accepted bytes, expire abandoned pending reservations, and delete mismatched objects before they become AVAILABLE.

## Sprint 4.3 Handoff

Sprint 4.3 may consume an AVAILABLE `ProblemAsset` plus original raw object, crop metadata, asset kind, content type, dimensions/page count, checksum, and owning `ProblemSession`. It must create derived assets with provenance and must not assume OCR, parse, or canonical problem state exists.
