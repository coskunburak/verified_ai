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

## Sprint 5.11 verification projection

GET `/api/v1/problem-sessions/{sessionId}/solves/{jobId}/verification` returns an explicit
nullable `decision`. It does no computation outside bounded policy/source reads and creates
no jobs, evidence, decisions or usage. POST
`/api/v1/problem-sessions/{sessionId}/solutions/{solutionId}/verification/{runKey}` evaluates
an existing current owned run with a UUID Idempotency-Key. Neither endpoint accepts a
client verdict. `verification-decision-v1` is an additive public allowlist with strict
status/scope/reason/recovery codes, checked/unchecked methods, contradiction references,
lineage, correlation and timestamp. Generation responses are unchanged. iOS renders this
separate result and clears it on refresh, offline failure, backgrounding or stale responses.

### Sprint 5.11 calculus decision scope — 2026-10-31

The unreleased verification-decision-v1 projection adds OPEN_INTERVAL,
ANTIDERIVATIVE_REPRESENTATIVE and PUNCTURED_NEIGHBORHOOD. Each VERIFIED calculus projection
contains exactly its corresponding checked method. Generic FINAL_ANSWER and mixed calculus/
generic obligations are invalid. Scope refers to the selected source and immutable evidence;
raw expressions, secondary payloads and internal owner identifiers remain excluded.
The GET remains read-only; POST evaluates existing evidence without executing mathematics.
Implementation and contract tests await owner validation; production flags remain disabled.

## Sprint 6.4 tutor API

The additive `/api/v1/problem-sessions/{sessionId}/tutor-sessions` family supports keyed POST creation,
GET list/detail, sequence-paginated GET messages, keyed POST learner content and idempotent POST close.
Only backend source/ownership/policy authority is accepted. JSON fields are exact; client role/user/source
assertions reject. History is accessible after close/source change; new stale activity rejects. Foreign
and absent objects share errors. See public-api.yaml and the Sprint 6.4 implementation map for schemas,
resource ceilings, retry scope, stable Problem Details and migration restrictions.

## Sprint 6.5 Socratic turn contract

Add POST/GET `/api/v1/problem-sessions/{sessionId}/tutor-sessions/{tutorSessionId}/turns`.
POST accepts a bounded content string and optional enumerated intent (default UNKNOWN), with required
Idempotency-Key. Unknown/duplicate fields, scalar coercion and authority fields are rejected. It returns
`tutor-turn-v1` with parent learner identity/sequence, TUTOR reply, phase/action/outcome, version and
explicit uncertainty. HTTP 200 means atomic local persistence, not mathematical assessment or AI success.
No async server job exists. Uncertain transport acknowledgement must replay the same key/content/intent.
Committed replay survives close/staleness/disable. Cross-operation key reuse conflicts.

GET is owned historical paging by exclusive learner sequence (gaps are expected for legacy messages),
ascending order, limit 1–50. Existing six operations/DTOs are preserved, and `/messages` stays LEARNER-only.
`messageCount` still counts learner records; new total-capacity checks count both message and reply rows.
The 120/account/minute fail-closed limiter and correlation/error infrastructure are shared. New server
transactions have a five-second timeout; iOS turn requests use a fifteen-second network timeout and
persist uncertain retries. Detailed payloads and failure statuses are in public-api.yaml.

## Sprint 6.6 hint operations

GET/POST `/{tutorSessionId}/hints` and POST `/{tutorSessionId}/reveal` extend the existing Tutor hierarchy.
`tutor-hint-v1` is an exact public allowlist; `tutor-hint-history-v1` returns all zero-to-three artifacts,
current/next level, current Socratic progress and availability. Next level is null whenever unavailable.
POST hints accepts only an enumerated level and required Idempotency-Key; replay requires the same level.
Changed payload or reuse across learner messages, turns and hints yields IDEMPOTENCY_KEY_REUSED.
Two distinct concurrent H1 keys cannot advance to H2. Committed replay survives close/stale/disable.

Confirmed reveal accepts exactly boolean confirmed=true, but returns TUTOR_REVEAL_UNAVAILABLE until an
authorized verified-reference contract exists. No success response or hidden answer field is advertised.
The existing authentication, ownership, 16 KB body bound, strict duplicate-field parsing, 120/account/minute
limiter, Problem Details/trace correlation and safe storage failures apply. No inference or billing call.
Existing endpoints and schemas remain backward compatible; the updated iOS feature requires these new APIs.

## Sprint 6.7 implemented attempt API boundary

Owner-scoped /api/v1/problem-sessions/{id}/attempts provides submission/history/context, extraction review,
/{attemptId}/counterfactual-twin creation/read/answer, /{attemptId}/alternative-methods, /drills and /mistake-passport.
Internal /internal/v1/verify/attempt remains v1; /attempt-v2 and /attempt-learning share bounded authenticated workers.
Submission v2 adds optional real-domain confirmation/capture review; clients never supply verdicts or correct answers.
Context v2 exposes actual enabled capabilities; student-attempt-v1 envelope admits versioned analysis v1/v2 through
student-attempt-response. Stable idempotency, ownership/freshness and first-answer transaction guards apply.
New contracts are listed in the Sprint 6.7 implementation map. Feature flags default off; owner acceptance pending.


### Sprint 6.8 versioned method comparison

The existing attempt alternative-method POST retains the empty-body v1 contract. A body with
`schemaVersion=method-comparison-v2` selects `method-comparison-public-v2`. The additional
`POST /api/v1/problem-sessions/{sessionId}/alternative-methods` accepts the version and an exact
`AttemptSourceIdentity` obtained from attempts/context; it never creates an attempt.

Both routes reuse AttemptLearningService and the internal token-authenticated attempt-learning route.
The internal union adds method-comparison-v2 / method-comparison-result-v2. Strict bundled schemas
validate nested typed math and metadata. Public v2 includes exact source lineage and
CURRENT_AT_PUBLICATION freshness; historical v1 semantics are unchanged. No v2 durable cache/table:
replays recompute deterministically, under 30 learning calls/account/minute and existing process admission.
Unknown authority fields reject. Source change returns 409; worker/disabled failures return 503;
UNSUPPORTED and NO_ALTERNATIVE return typed 200 outcomes with zero verified cards. No global solver
or learner verdict changes. See Sprint 6.8 implementation map for proof bounds and owner gates.

## Sprint 6.9 visualization boundary

GET /api/v1/problem-sessions/{problemId}/visualizations/context and POST /api/v1/problem-sessions/{problemId}/visualizations provide an ephemeral source projection. Independent context/source/request/result/public v1 schemas bind source revisions, execution ID, viewport and kind. Request cap 16 KiB; worker response cap remains 16 KiB. HTTP 409 requires source reload, 429 rate recovery, 503 unavailable; unsupported mathematical scope returns an empty UNSUPPORTED result. No-store responses, JWT ownership and post-worker reauthorization apply.

## Sprint 7.1 versioned answer capture and historical reads

Existing POST /api/v1/problem-sessions/{sessionId}/attempts accepts unversioned submission v1/v2 unchanged,
or explicit schemaVersion=attempt-submission-v3 with source, steps (0–11) and finalAnswer. The new typed decoder
rejects unknown/nested authority fields, coercion, invalid UUIDs, duplicate keys, malformed UTF-8 and invalid text.
The same 16 KiB body, 256 UTF-16 units / 1024 UTF-8 bytes per text, 30/account and 60 requests/minute limits apply.
V3 returns student-attempt-v2 with separate finalAnswer, null analysis and SUBMITTED (saved, not awaiting an automatic job).
No paid inference, mathematical normalization or verifier execution is necessary to save an answer.

GET list/detail accepts representationVersion=2 for native versioned v1/v2 records. Default 1 preserves the older
student-attempt-response contract, projecting the final answer as a last unassessed display line. Projection does
not alter durable evidence. Updated clients request representation 2; deploy the backend/migration before clients.
Existing context remains v2. Old clients can display new work without interpreting its final-answer role.

Same owner/session/key and identical semantic payload replay after timeout, feature disable or source revision change;
changed payload returns IDEMPOTENCY_KEY_REUSED/409. New stale submissions reject with ATTEMPT_STALE_SOURCE/409.
Capacity is ATTEMPT_CAPACITY_EXCEEDED/409, rate limit 429, rollout/persistence unavailability 503. Body/key validation
returns REQUEST_VALIDATION_FAILED/422; malformed path/query values return 400. Foreign objects remain 404 and inactive
accounts 403. Only exact retries can recover a possibly committed request; GET never starts assessment.

## Sprint 7.2 structured-work API

POST /api/v1/problem-sessions/{sessionId}/attempts/structured-work accepts attempt-structured-work-request-v1 and returns
attempt-structured-work-v1. GET /api/v1/problem-sessions/{sessionId}/attempts/{attemptId}/structured-work reads immutable
owned history even when rollout is off. Historical attempts without an artifact return ATTEMPT_NOT_FOUND (404); no backfill.
The context-v2 response adds optional structuredWorkEnabled; absent means unsupported by that server. Legacy routes and
submission hashes are unchanged. A legacy history projection contains unassessed reviewed text and explicit final answer.

All request keys are required; nullable capture/asset/job/predecessor fields must be explicit null. Responses retain explicit
nulls for absent provenance/normalization/original OCR step text. Step roles, normalization/review status and provenance are
closed enums. Native observations are client attestations; gateway provider/model/prompt come only from stored recognition.
Observation IDs may be shared after splitting; every observation must remain linked and step/observation IDs are unique.
One final answer maximum, 12 total steps, 256 UTF-16 units/1024 UTF-8 bytes per text, 16 KiB wire request, 1800-character AST
per step, 64 KiB stored artifact. ASCII explicit multiplication is supported by the existing parser; implicit multiplication,
Unicode superscripts/bars and unsupported calculus are retained verbatim as UNSUPPORTED, without guessed replacement.

Authentication, active account, problem/attempt/asset ownership, source freshness, 30-attempt capacity and 30/minute
structured operation rate limit apply. Same-key exact typed-command replay is checked before rollout/source freshness;
changed payload returns IDEMPOTENCY_KEY_REUSED (409). Revisions use supersedesAttemptId and conflict with OPTIMISTIC_CONFLICT
if already superseded; original source and observations cannot change. Errors use existing Problem Details/traceId:
401 authentication, 403 ACCOUNT_NOT_ACTIVE, 404 RESOURCE_FORBIDDEN/ATTEMPT_NOT_FOUND, 409 ATTEMPT_STALE_SOURCE or capacity,
413 REQUEST_TOO_LARGE, 422 REQUEST_VALIDATION_FAILED, 429 RATE_LIMIT_EXCEEDED, 503 ATTEMPT_UNAVAILABLE. Retry an uncertain
failure with the same key and complete body. The request can safely save unresolved review; no mathematical assessment runs.
