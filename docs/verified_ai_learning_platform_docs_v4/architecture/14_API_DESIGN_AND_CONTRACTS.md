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
| Patch/select parse revision | Conditional | Problem session + revision | Version conflict returns stable conflict error. |
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

`PresignProblemAssetUploadRequest` accepts `source`, `assetKind`, `contentType`, `sizeBytes`, `checksumSha256`, optional image dimensions, optional PDF page count, and optional normalized crop fields. The backend ignores any client attempt to choose object storage identity. `PresignProblemAssetUploadResponse` returns `uploadId`, `problemSessionId`, `problemAssetId`, `assetStatus`, `uploadUrl`, `expiresAt`, and `requiredHeaders`.

Upload completion is idempotent after success. Reusing a reservation idempotency key for a different payload returns `IDEMPOTENCY_KEY_REUSED`; completing an already AVAILABLE upload returns the stable durable asset reference.

### Problem sessions
- `POST /api/v1/problem-sessions`
- `GET /api/v1/problem-sessions/{id}`
- `PATCH /api/v1/problem-sessions/{id}/parse`
- `POST /api/v1/problem-sessions/{id}/solve`

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

## Contract lifecycle

Maintain OpenAPI contract. Generated Swift client code may be used selectively, but generated DTOs must not become the domain model.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI route abstraction in public APIs

The iOS/public API never accepts raw provider/model identifiers as normal product inputs. Clients request product capabilities (`solve`, `explain`, `tutor`, etc.); the backend owns route/model selection.

Public responses may expose product-safe provenance/verification evidence where useful, but must not couple clients to provider-specific fields. Internal route-policy changes should not require an iOS release.

Usage-limit responses use stable product-semantic errors rather than leaking provider quotas or raw token budgets.
<!-- HYBRID_AI_STRATEGY_V3:END -->
