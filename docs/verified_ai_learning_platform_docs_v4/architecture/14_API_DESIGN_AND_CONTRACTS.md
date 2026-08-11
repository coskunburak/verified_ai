# API Design and Contracts

## Principles

- Versioned REST-style mobile API under `/api/v1`.
- JSON for structured APIs.
- Presigned direct binary upload.
- Stable machine-readable error codes.
- DTOs are not JPA entities.
- Commands are idempotent where mobile retry is expected.
- Backend owns authorization and entitlement.

## Phase 1 API Baseline Contract

### Required request context

| Concern | Contract |
|---|---|
| API version | Public mobile API paths start under `/api/v1`. |
| Authentication | Protected endpoints use backend-issued bearer access tokens. |
| Authorization | Resource ownership is derived from the authenticated principal, not a client-provided `userId`. |
| Correlation | Clients send or receive a correlation identifier such as `X-Request-Id`; responses include trace context where practical. |
| Idempotency | Retry-prone mutating commands require `Idempotency-Key`. |
| Content type | JSON APIs use `application/json`; binary uploads use backend-authorized presigned URLs. |

### Idempotency baseline

| Operation | Idempotency required? | Scope | Reuse behavior |
|---|---|---|---|
| Create problem session | Yes | User + command + key | Return existing logical session or compatible result. |
| Complete upload | Yes | User + upload intent + key | Do not double-register object state. |
| Request recognition | Yes | User + ProblemSession + selected recognition input + prompt/schema | Reuse the existing recognition job for the same input/provenance tuple. |
| Request problem parse | Yes | User + ProblemSession + RecognitionEvidence revision + `PROBLEM_NORMALIZE` prompt/schema/route | Reuse the existing parse job for the same input/provenance tuple. |
| Create user parse correction | Yes | User + ProblemSession + base parse revision + key | Return existing correction for matching fingerprint; reject key reuse for different payload. |
| Request solve | Yes | Problem session + selected parse + key | Reuse existing solve job if logical command matches. |
| Submit attempt | Yes | User + problem + key | Avoid duplicate mastery/mistake evidence. |
| Complete study session/item | Yes | Study session/item + key | Avoid double completion. |
| Billing Apple sync | Yes | User + external transaction/event identity | External event processing remains exactly-once at business level. |

Idempotency records must store enough command fingerprinting to reject accidental key reuse for different payloads.

### Asynchronous solve baseline

`POST /api/v1/problem-sessions/{id}/solve` returns `202 Accepted` when the solve cannot complete within a normal request budget.

Minimum response fields:

```json
{
  "solveJobId": "01J...",
  "problemSessionId": "01J...",
  "status": "QUEUED",
  "stage": "PARSE",
  "traceId": "01J..."
}
```

Allowed job statuses are `QUEUED`, `RUNNING`, `WAITING_EXTERNAL`, `RETRY_SCHEDULED`, `SUCCEEDED`, `FAILED`, and `CANCELLED`. User-facing progress maps to semantic stages, not fake percentages.

### Error payload baseline

Errors use a Problem Details-compatible shape with a stable product `code`:

```json
{
  "type": "https://errors.verified-ai-learning.example/problem-parse-failed",
  "title": "Problem could not be parsed",
  "status": 422,
  "code": "PROBLEM_PARSE_FAILED",
  "traceId": "01J...",
  "details": {
    "recoverable": true,
    "userAction": "EDIT_PARSE"
  }
}
```

`code`, `status`, `traceId`, `recoverable`, and `userAction` are contract fields. `title` is presentation/localization input and may evolve without changing semantics.

### Retry contract

- Client automatic retries are limited to network-safe and explicitly transient errors.
- Mutating retries require idempotency.
- Quota, entitlement, authentication, authorization, unsupported input, and semantic validation failures are not blindly retried.
- Provider-specific quota or model IDs are not exposed as normal public API semantics.

## Endpoint map

### Auth
- `POST /api/v1/auth/apple` — permit all; exchanges a backend-verified Apple identity token plus raw nonce for a platform session.
- `POST /api/v1/auth/refresh` — permit all; rotates a refresh token or revokes the session on reuse detection.
- `GET /api/v1/auth/session` — authenticated; returns the current backend user/session identifiers from the access token.
- `POST /api/v1/auth/logout` — authenticated; revokes the current backend session and active refresh tokens.

### Account and privacy
- `GET /api/v1/me/account` — authenticated; returns the current account state derived from the principal.
- `POST /api/v1/me/data-exports` — authenticated; prepares a current-user export without token secrets or raw payment material.
- `GET /api/v1/me/data-exports/{exportId}` — authenticated; returns export status scoped to current user.
- `GET /api/v1/me/data-exports/{exportId}/content` — authenticated; downloads a ready, unexpired export document.
- `POST /api/v1/me/deletion-request` — authenticated; marks the current account deletion-requested.
- `GET /api/v1/me/deletion-request` — authenticated; returns deletion lifecycle state.
- `POST /api/v1/me/deletion-request/confirm` — authenticated; requires confirmation text and executes deletion/revocation.

### Profile
- `GET /api/v1/me/learning-profile` — authenticated; returns the current user's learning profile or `NOT_STARTED` when no durable profile row exists.
- `PATCH /api/v1/me/learning-profile` — authenticated; validates and upserts the current user's learning profile with optimistic `expectedVersion` conflict handling.

### Uploads
- `POST /api/v1/uploads/presign` - authenticated; requires `Idempotency-Key`; creates a `ProblemSession` and PENDING `ProblemAsset`, validates source/kind/content type/size/SHA-256/crop metadata, and returns a short-lived presigned PUT URL plus required headers. Normal response is `201 Created`.
- `POST /api/v1/uploads/{id}/complete` - authenticated; requires `Idempotency-Key`; derives owner from the bearer principal, verifies the private object with HEAD and streamed SHA-256, then transitions `ProblemAsset` to `AVAILABLE` and `ProblemSession` to `ASSET_UPLOADED`. Normal response is `200 OK`.
- `POST /api/v1/problem-assets/{id}/preprocess` - authenticated; derives owner from the bearer principal, reads the private AVAILABLE source object through the backend, creates deterministic preprocessing derivatives and quality evidence, and returns the durable preprocessing lifecycle result. Normal response is `200 OK`; unsupported PDF preprocessing is returned as recoverable `FAILED` lifecycle state in Sprint 4.3.
- `GET /api/v1/problem-assets/{id}/preprocessing` - authenticated; returns the current preprocessing lifecycle state, derivative metadata, quality evidence, selected recognition derivative id, and user recovery actions without exposing storage object keys or signed download URLs.

`PresignProblemAssetUploadRequest` accepts `source`, `assetKind`, `contentType`, `sizeBytes`, `checksumSha256`, optional image dimensions, optional PDF page count, and optional normalized crop fields. The backend ignores any client attempt to choose object storage identity. `PresignProblemAssetUploadResponse` returns `uploadId`, `problemSessionId`, `problemAssetId`, `assetStatus`, `uploadUrl`, `expiresAt`, and `requiredHeaders`.

Upload completion is idempotent after success. Reusing a reservation idempotency key for a different payload returns `IDEMPOTENCY_KEY_REUSED`; completing an already AVAILABLE upload returns the stable durable asset reference.

Sprint 4.3 preprocessing responses include `preprocessingStatus`, `qualityOutcome`, optional `failureCode`, `preferredRecognitionDerivativeId`, `derivatives`, `qualitySignals`, and `userRecoveryActions`. The selected `OCR_OPTIMIZED` derivative is a future Sprint 4.4 recognition input only; Sprint 4.3 does not perform OCR, vision recognition, parsing, solving, or verification.

### Problem sessions
- `GET /api/v1/problem-sessions?limit=20&cursor=...` - authenticated; derives owner from the bearer principal and returns `{items,nextCursor}` using keyset pagination over `updated_at DESC, id DESC`. `limit` defaults to 20 and is capped at 50. List items include coarse session status, derived `stage`, derived `nextAction`, retry/review flags, selected parse summary fields, current classification summary fields, timestamps, and no raw student content.
- `GET /api/v1/problem-sessions/{sessionId}` - authenticated; derives owner from the bearer principal and returns the authoritative recovery projection for one session: coarse status, derived `stage`, derived `nextAction`, retry/review/failure fields, selected current parse summary, current canonical summary only when it matches the selected parse, current classification summary only when it matches the current canonical, active durable job, timestamps, and version.
- `POST /api/v1/problem-sessions/{id}/recognition` - authenticated; creates or reuses a durable recognition job for the selected READY `OCR_OPTIMIZED` derivative and returns `202 Accepted`. The request does not hold the HTTP connection open for provider execution.
- `GET /api/v1/problem-sessions/{id}/recognition` - authenticated; returns the current recognition job/evidence status, selected input derivative/source asset IDs, review-required flag, safe normalized recognition blocks, and no raw provider output or object-storage keys.
- `POST /api/v1/problem-sessions/{id}/parse` - authenticated; creates or reuses a durable parser job for the exact accepted RecognitionEvidence revision and returns `202 Accepted`. The worker invokes `PROBLEM_NORMALIZE`, validates `problem-parse-v1`, and may return supported, review-required, unsupported, or failed lifecycle state without solving.
- `GET /api/v1/problem-sessions/{id}/parse` - authenticated; returns current parse job/revision status, support status, normalized parser-level structure, source evidence references, review-required flag, prompt/schema/model provenance, and no raw parser output.
- `GET /api/v1/problem-sessions/{id}/parse-review` - authenticated; returns the selected parse, editable normalized problem document, correction eligibility, and current revision metadata.
- `POST /api/v1/problem-sessions/{id}/parse-revisions` - authenticated; requires `Idempotency-Key`; creates a user-corrected parse revision from a selected base parse when semantic validation succeeds.
- `GET /api/v1/problem-sessions/{id}/parse-revisions` - authenticated; returns immutable parse revision history with source, parent, corrected fields, support status, and selection state.
- `POST /api/v1/problem-sessions/{id}/solve`

Sprint 4.9 history/detail/reconnect reads are recovery projections only. They must not reserve uploads, start preprocessing, create recognition jobs, create parser jobs, canonicalize, classify, solve, verify, or call an AI provider. Recovery commands remain exact stage-specific POSTs; there is no generic recover-all endpoint. If durable lineage is ambiguous, for example an accepted parse exists but `problem_sessions.current_parse_id` is missing, detail fails closed rather than guessing a downstream authority.

### Jobs
- `GET /api/v1/solve-jobs/{id}`

### Solutions
- `GET /api/v1/solutions/{id}`
- `GET /api/v1/solutions/{id}/verification`

### Attempts
- `POST /api/v1/attempts`
- `GET /api/v1/attempts/{id}`

### Learning
- `GET /api/v1/mastery`
- `GET /api/v1/mistakes`
- `GET /api/v1/study-plan/today`
- `POST /api/v1/study-sessions/{id}/complete`

### Billing
- `GET /api/v1/me/entitlements` — authenticated; returns the backend-authoritative current entitlement tier/status/source and currently granted capability list.
- `GET /api/v1/me/billing/apple/configuration` — authenticated; returns backend-owned StoreKit configuration and app account token.
- `POST /api/v1/me/billing/apple/transactions` — authenticated; submits signed StoreKit transaction evidence for backend verification.
- `POST /api/v1/webhooks/apple/app-store` — public Apple webhook endpoint for signed Server Notifications V2 payloads.

## Error contract

```json
{
  "type": "https://errors.example.com/problem-parse-failed",
  "title": "Problem could not be parsed",
  "status": 422,
  "code": "PROBLEM_PARSE_FAILED",
  "traceId": "01J...",
  "details": {"recoverable": true}
}
```

`code` is stable for the client. `title` is localizable/presentation-friendly.

## Pagination

Use cursor pagination for history, attempts and mistakes. Avoid large offset scans on mutable timelines.

## Idempotency

Require `Idempotency-Key` for retry-prone commands such as create problem, solve, attempt submission and billing sync.

Sprint 4.2 upload idempotency is scoped to authenticated user plus key. Reservation stores a request hash and rejects key reuse for different payloads. Completion is safe to retry: if the asset is already AVAILABLE, the backend returns the existing durable reference without double-registering object state.

Sprint 4.4 recognition idempotency is scoped to authenticated user, ProblemSession, selected recognition input derivative, `VISION_PARSE`, prompt ID/version, and schema version. Retrying the command returns the existing logical job rather than creating unlimited provider calls.

Sprint 4.5 parse idempotency is scoped to authenticated user, ProblemSession, exact RecognitionEvidence id/revision, `PROBLEM_NORMALIZE`, prompt ID/version, schema version, and route policy version. Retrying the command returns the existing logical parse job rather than creating unlimited parser calls or duplicate revisions.

Sprint 4.8 correction idempotency is scoped to authenticated user, ProblemSession, base parse id/revision, correction reason, correction schema version, and canonical corrected problem JSON. Matching retries return the existing corrected revision. Reusing a key for different correction content returns `PARSE_CORRECTION_IDEMPOTENCY_CONFLICT`.

## Concurrency

User-editable parse revisions and learning profile updates use explicit version/revision and optimistic conflict response.

## Current Phase 3 DTOs

`LearningProfileResponse` returns `exists`, `id`, `userId`, V1 profile fields, `onboardingStatus`, `version`, `createdAt`, and `updatedAt`. `NOT_STARTED` is represented by no durable row plus `exists=false`.

`EntitlementResponse` returns `id`, `userId`, `tier`, `source`, `status`, `effectiveAt`, nullable `expiresAt`, `capabilities`, and `version`. Normal clients have no public entitlement mutation endpoint; unsupported public write attempts return a stable Problem Details 405.

`AccountStateResponse`, `DataExportResponse`, and `DeletionRequestResponse` are current-user DTOs. They never accept `userId` as an input path/query/body parameter from mobile clients.

## DTO safety

Never expose:
- private chain-of-thought,
- system prompts,
- provider secrets,
- internal security/audit details.

Verification API exposes only high-level evidence.

Recognition APIs expose only normalized raw evidence needed for the next product step. They never expose raw provider payloads, provider secrets, signed URLs, object keys, system prompts, canonical parse claims, solutions, or verification status.

Problem parse APIs expose only normalized parser-level structure needed for review and later canonicalization. They never expose raw parser payloads, provider secrets, system prompts, safe verifier AST, primary skill/difficulty classification, solutions, answers, or verification status. User-correction APIs expose correction reason, corrected field names, source, parent, and selected status, but not correction request hashes or idempotency keys.

## Contract lifecycle

Maintain OpenAPI contract. Generated Swift client code may be used selectively, but generated DTOs must not become the domain model.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI route abstraction in public APIs

The iOS/public API never accepts raw provider/model identifiers as normal product inputs. Clients request product capabilities (`solve`, `explain`, `tutor`, etc.); the backend owns route/model selection.

Public responses may expose product-safe provenance/verification evidence where useful, but must not couple clients to provider-specific fields. Internal route-policy changes should not require an iOS release.

Usage-limit responses use stable product-semantic errors rather than leaking provider quotas or raw token budgets.
<!-- HYBRID_AI_STRATEGY_V3:END -->
