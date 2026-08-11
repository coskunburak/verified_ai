# Sprint 4.9 Production Implementation Map
## Problem Session History, Retry, Resume, and Recovery Experience

**Project:** Verified AI Learning Platform  
**Phase:** Phase 4 — Problem Capture & Canonicalization  
**Sprint:** 4.9  
**Start condition:** Sprint 4.8 COMPLETE — LOCAL VALIDATION GREEN  
**Owning bounded context:** `problem`  
**Backend:** Java 21 / Spring Boot 4 / Spring Modulith / PostgreSQL / Flyway  
**iOS:** Swift / SwiftUI / Observation / async-await / SwiftData  
**Current highest migration:** `V014__create_user_correctable_parse_revision_lifecycle.sql`  
**Expected next migration:** `V015__harden_problem_session_history_and_recovery_queries.sql` if the query/index work requires it  
**New AI capability:** NONE  
**New prompt/model route:** NONE  

---

# 1. Executive Summary

Sprint 4.9 turns the Phase 4 ingestion pipeline into a coherent, durable, resumable **ProblemSession experience**.

Before Sprint 4.9, the repository already persists the major Phase 4 artifacts independently:

- ProblemSession;
- private ProblemAsset upload lifecycle;
- preprocessing derivatives and quality evidence;
- RecognitionJob and RecognitionEvidence;
- ProblemParse jobs and immutable revisions;
- user-corrected ProblemParse revisions;
- `problem_sessions.current_parse_id` selected-parse authority;
- CanonicalProblem revisions;
- ProblemClassification jobs/results;
- iOS ProblemCapture and ProblemReview flows.

Sprint 4.9 must make the platform able to answer, authoritatively:

```text
Where is this ProblemSession now?
Which durable work already exists?
Which job is already running?
What failed?
Is that failure retryable?
What exact action is safe next?
Can the user resume after app termination?
Can the user find and reopen the session later?
```

The target architecture is:

```text
Durable Phase 4 artifacts/jobs
        ↓
ProblemSession Projection
        ↓
ProblemSession Recovery Planner
        ↓
Typed stage + typed nextAction
        ↓
GET session detail/history
        ↓
iOS stale-while-revalidate cache
        ↓
Resume coordinator
        ↓
Existing stage-specific commands
```

The sprint must NOT introduce a generic “retry everything” workflow.

The backend determines **what is safe next**. Existing stage-specific endpoints perform that command. The iOS app determines **when the user explicitly invokes** retry/recovery.

This keeps retries idempotent, prevents unnecessary AI calls, preserves Sprint 4.8 selected-parse semantics, and creates a clean Phase 5 handoff.

---

# 2. Canonical Source-of-Truth Review

The implementation is governed by:

- `00_MASTER_INDEX.md`
- `sprints/phase04_problem_capture_and_canonicalization/PHASE_04_PROBLEM_CAPTURE_AND_CANONICALIZATION.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.8_EXECUTION_REPORT.md`
- `sprints/phase04_problem_capture_and_canonicalization/SPRINT_4.9_PROBLEM_SESSION_HISTORY_RETRY_AND_RECOVERY_EXPERIENCE.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`
- `product/03_END_TO_END_USER_JOURNEYS.md`
- `architecture/13_ASYNC_PROCESSING_AND_JOB_ORCHESTRATION.md`
- `backend/20_BACKEND_MODULE_CONTRACTS.md`
- `backend/21_AUTHORIZATION_RATE_LIMITING_AND_ABUSE.md`
- `data/22_POSTGRESQL_DATA_MODEL.md`
- `data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- `data/24_CACHING_STORAGE_AND_FILE_ASSETS.md`
- `ios/15_IOS_ARCHITECTURE_AND_FILE_HIERARCHY.md`
- `ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`
- `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md`
- `operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md`
- `quality/41_ENGINEERING_STANDARDS_FOR_CODEX.md`
- `quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`
- `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`
- `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`

---

# 3. Sprint 4.8 Baseline That Must Be Preserved

Sprint 4.8 already delivered:

- V014 user-correctable parse revision persistence;
- immutable AI/USER ProblemParse revisions;
- user correction parent lineage;
- correction request hashing/idempotency;
- stale-base conflict handling;
- correction rate limiting;
- parse-review/revision-history/correction APIs;
- selected-parse authority through `current_parse_id`;
- canonicalization from selected parse rather than implicit latest parse;
- privacy export/delete coverage for correction metadata;
- iOS ProblemReview flow.

Sprint 4.9 must not regress any of those guarantees.

The current `ProblemSessionStatus` vocabulary is already:

```text
CREATED
ASSET_UPLOADED
PARSING
PARSED
SOLVING
VERIFYING
COMPLETED
REVIEW_REQUIRED
FAILED
CANCELLED
```

Do not create recognition-specific and classifier-specific session statuses. Fine-grained state belongs in a derived projection.

---

# 4. Sprint Mission and Production Outcomes

The official mission is:

> Persist problem lifecycle state so uploads, parsing, corrections, retries, and later history survive app termination and transient failures.

Required production outcomes:

1. Backend-authoritative ProblemSession lifecycle.
2. Deterministic fine-grained stage projection.
3. Deterministic next safe action/recovery plan.
4. Owner-scoped paginated problem history.
5. Authoritative session detail endpoint.
6. App termination/background/relaunch resume.
7. Running-job polling resume without duplicate POST.
8. Exact-stage retry without repeating successful prior work.
9. Local pending-upload recovery where bytes are not yet backend durable.
10. Offline cached history without local-authority drift.
11. Privacy-safe telemetry.
12. No new hidden AI/model calls.
13. Full BOLA/concurrency/idempotency coverage.
14. Clean Phase 5 boundary at `READY_FOR_SOLVE`.

---

# 5. Non-Goals

Explicitly out of scope:

- Phase 5 solver implementation;
- verification planner or verdicts;
- solution generation;
- tutor flows;
- attempts/mistakes/mastery;
- generic server workflow engine;
- Kafka;
- new microservice;
- new AI model route or prompt;
- model training/fine-tuning;
- automatic AI retry on reconnect;
- “retry entire pipeline” endpoint;
- arbitrary old parse restore;
- taxonomy/classification editor;
- broad per-item deletion semantics beyond current account lifecycle;
- Phase 5 `SOLVING`/`VERIFYING` behavior.

---

# 6. Core Domain Invariants

## INV-4.9-01 — PostgreSQL is authoritative
SwiftData is a cache/recovery projection only.

## INV-4.9-02 — Session status is coarse
`problem_sessions.status` does not duplicate every child job state.

## INV-4.9-03 — Fine-grained stage is derived
`ProblemSessionStage` is computed from durable facts.

## INV-4.9-04 — nextAction is backend-derived
The client cannot invent or skip pipeline actions.

## INV-4.9-05 — successful work is reused
Retrying a later stage never repeats prior successful stages unnecessarily.

## INV-4.9-06 — running work is polled, not duplicated
QUEUED/RUNNING durable jobs map to WAIT actions.

## INV-4.9-07 — retryable != terminal
Operational transient failures and semantic terminal outcomes remain distinct.

## INV-4.9-08 — unsupported is explicit
Unsupported is not blindly retried.

## INV-4.9-09 — selected parse stays authoritative
All downstream decisions use `current_parse_id`.

## INV-4.9-10 — canonical authority is exact
Current canonical must correspond to selected parse id/revision.

## INV-4.9-11 — classification authority is exact
Current classification must correspond to current canonical.

## INV-4.9-12 — reconnect has no hidden AI side effects
Reconnect/foreground performs reads/polling only.

## INV-4.9-13 — retry is stage-specific
No global retry-all action.

## INV-4.9-14 — history is owner scoped
Every query derives owner from authenticated principal.

## INV-4.9-15 — history is privacy-minimized
List responses avoid raw problem content.

## INV-4.9-16 — local cache is account scoped
Logout/delete/account switch clears or isolates local records.

## INV-4.9-17 — app termination does not cancel server work
Only client polling is cancelled.

## INV-4.9-18 — client navigation cannot advance server state
Durable evidence must justify every server transition.

## INV-4.9-19 — history/recovery metadata is not training data
No training eligibility change.

## INV-4.9-20 — Phase 5 statuses remain reserved
Sprint 4.9 stops at `READY_FOR_SOLVE`.

---

# 7. Three-Layer Session State Model

## 7.1 Persisted `ProblemSessionStatus`

Use existing coarse vocabulary.

Current Phase 4 active subset:

```text
CREATED
ASSET_UPLOADED
PARSING
PARSED
REVIEW_REQUIRED
FAILED
```

Reserved:

```text
SOLVING
VERIFYING
COMPLETED
CANCELLED
```

## 7.2 Derived `ProblemSessionStage`

Recommended enum:

```text
AWAITING_UPLOAD
PREPROCESSING
RECOGNITION
PARSING
PARSE_REVIEW
CANONICALIZATION
CLASSIFICATION
READY_FOR_SOLVE
TERMINAL
```

## 7.3 Derived `ProblemSessionNextAction`

Recommended enum:

```text
NONE
RESUME_UPLOAD
START_PREPROCESSING
RETRY_PREPROCESSING
START_RECOGNITION
WAIT_RECOGNITION
RETRY_RECOGNITION
START_PARSE
WAIT_PARSE
RETRY_PARSE
REVIEW_PARSE
CANONICALIZE
START_CLASSIFICATION
WAIT_CLASSIFICATION
RETRY_CLASSIFICATION
READY_FOR_SOLVE
RECAPTURE_OR_REIMPORT
UNSUPPORTED
```

These enums are closed contracts, not arbitrary strings.

---

# 8. Recovery Planner Truth Table

## AWAITING_UPLOAD
Server sees no AVAILABLE source asset.

Server stage:

```text
AWAITING_UPLOAD
```

iOS combines this with local state.

If local source bytes exist:

```text
RESUME_UPLOAD
```

If local source bytes are missing:

```text
RECAPTURE_OR_REIMPORT
```

The backend cannot fabricate missing device bytes.

## PREPROCESSING
AVAILABLE asset, no completed preprocessing:

```text
START_PREPROCESSING
```

Transient preprocessing failure:

```text
RETRY_PREPROCESSING
```

Deterministic unusable input:

```text
RECAPTURE_OR_REIMPORT
```

## RECOGNITION
QUEUED/RUNNING recognition:

```text
WAIT_RECOGNITION
```

FAILED_RETRYABLE:

```text
RETRY_RECOGNITION
```

Terminal/unreadable:

```text
RECAPTURE_OR_REIMPORT
```

SUCCEEDED and no parse:

```text
START_PARSE
```

## PARSING
QUEUED/RUNNING parse:

```text
WAIT_PARSE
```

FAILED_RETRYABLE:

```text
RETRY_PARSE
```

semantic REVIEW_REQUIRED:

```text
REVIEW_PARSE
```

UNSUPPORTED:

```text
UNSUPPORTED
```

## SELECTED PARSE
Selected parse exists, canonical for exact selected parse does not:

```text
CANONICALIZE
```

If accepted parse history exists but `current_parse_id` is invalid/missing:

```text
PROBLEM_SESSION_LINEAGE_AMBIGUOUS
```

Never silently choose latest parse.

## CLASSIFICATION
Current canonical exists, no classification:

```text
START_CLASSIFICATION
```

classification QUEUED/RUNNING:

```text
WAIT_CLASSIFICATION
```

FAILED_RETRYABLE:

```text
RETRY_CLASSIFICATION
```

semantic UNSUPPORTED:

```text
UNSUPPORTED
```

semantic REVIEW_REQUIRED:
expose review state conservatively. Do not invent a taxonomy editor in this sprint.

Current successful classification:

```text
READY_FOR_SOLVE
```

---

# 9. Coarse Session Lifecycle Policy

Create `ProblemSessionLifecyclePolicy`.

Legal current-scope transitions should be explicit.

```text
CREATED
  → ASSET_UPLOADED
  → FAILED

ASSET_UPLOADED
  → PARSING
  → REVIEW_REQUIRED
  → FAILED

PARSING
  → PARSED
  → REVIEW_REQUIRED
  → FAILED

REVIEW_REQUIRED
  → PARSING
  → PARSED
  → FAILED

FAILED
  → ASSET_UPLOADED
  → PARSING
```

Only when durable recovery evidence justifies it.

Forbidden examples:

```text
CREATED → PARSED
FAILED → COMPLETED
PARSED → CREATED
```

Reserved future:

```text
PARSED → SOLVING → VERIFYING → COMPLETED
```

---

# 10. Package A — Semantic Contract and Governance Reconciliation

## Goal
Freeze the lifecycle/recovery semantics before implementation.

## A1. Capability matrix reconciliation
Current governance docs must be checked for stale statuses.

Before closure:
- Sprint 4.7 classification capability should reflect completed evidence.
- Sprint 4.8 correction capability should reflect completed evidence.
- CAP-PROBLEM-005 remains Pending during implementation.
- CAP-PROBLEM-005 becomes Complete only after all 4.9 gates pass.

## A2. Add/confirm traceability requirement
If absent, create:

```text
REQ-PROBLEM-005
```

Recommended requirement:

> ProblemSession lifecycle, history, and recovery state are backend-authoritative and durable across client termination; retries resume/reuse the exact recoverable stage without duplicating durable or AI work.

Acceptance evidence:
- lifecycle-policy tests;
- recovery-planner tests;
- history/detail API;
- iOS relaunch;
- duplicate retry prevention;
- BOLA.

Operational evidence:
- transition metrics;
- recovery action distribution;
- history/detail latency;
- retry outcomes.

## A3. Document persisted vs derived state
Canonical docs must explicitly distinguish:
- `ProblemSessionStatus` persisted;
- `ProblemSessionStage` derived;
- `ProblemSessionNextAction` derived.

## A4. Confirm module
Everything stays under:

```text
com.verifiedai.problem
```

No new history/recovery microservice.

## A5. AI economics declaration

```text
new inference capability = 0
GET history AI calls = 0
GET detail AI calls = 0
reconnect AI calls = 0
```

Explicit stage retry may use the existing route/job policy.

## Package A gate
- [ ] semantics frozen
- [ ] REQ-PROBLEM-005 present
- [ ] stale capability statuses identified
- [ ] no new AI/model contract
- [ ] module ownership documented

---

# 11. Package B — Backend Lifecycle, Projection, History, and V015

## Goal
Build server-authoritative session projection/history.

## B1. V015 migration
Do not modify V014 or older migrations.

Recommended:

```text
V015__harden_problem_session_history_and_recovery_queries.sql
```

Current useful indexes already exist for user-created ordering and status-updated ordering, but activity history requires a stable tie-break.

Recommended:

```sql
CREATE INDEX ix_problem_sessions_user_updated_id
    ON problem_sessions(user_id, updated_at DESC, id DESC);
```

Optional only if real query path uses status filtering:

```sql
CREATE INDEX ix_problem_sessions_user_status_updated_id
    ON problem_sessions(user_id, status, updated_at DESC, id DESC);
```

Do not add unnecessary indexes.

## B2. No duplicate lifecycle columns
Do NOT add:

```text
current_recognition_job_id
current_parse_job_id
classification_status
recognition_status
preprocessing_status
```

to ProblemSession merely for convenience.

Use owning tables.

## B3. Extend ProblemSessionJpaEntity
Add explicit legal transition methods/policy integration:

```text
markParsing
markParsed
markReviewRequired
markFailed
```

Requirements:
- update `updated_at`;
- respect `@Version`;
- do not alter selected parse implicitly;
- do not use JPA callbacks for domain decisions.

## B4. ProblemSessionLifecyclePolicy
Create a deterministic policy with tests for every legal/illegal transition.

## B5. ProblemSessionProjection
Recommended internal record:

```text
sessionId
status
stage
inputMode
nextAction
retryable
reviewRequired
failureCode
currentParseId
currentParseRevision
currentParseSource
canonicalProblemId
canonicalProblemRevision
classificationId
classificationStatus
primarySkillId
difficulty
activeJob
createdAt
updatedAt
completedAt
version
```

## B6. ProblemSessionProjectionService
Read-only service.

It loads:
- owner-scoped session;
- source asset;
- preprocessing;
- recognition job/evidence;
- current parse job;
- selected parse;
- canonical matching selected parse;
- classification matching current canonical.

It then calls `ProblemSessionRecoveryPlanner`.

GET must remain side-effect free.

## B7. History query path
Do not call full detail projection N times.

Create a bounded history query.

Use:
- one projection query; or
- bounded batch fetch.

Never deserialize raw recognition/parser JSON for list rows.

## B8. Keyset pagination
Ordering:

```text
updated_at DESC, id DESC
```

Cursor contains:
- updatedAt;
- sessionId;
- optional cursor schema version.

Defaults:
- page size 20;
- max 50.

No OFFSET pagination.

## B9. History summary
Privacy-minimized:

```text
sessionId
status
stage
inputMode
nextAction
retryable
reviewRequired
currentParseRevision
currentParseSource
classificationStatus
primarySkillId
difficulty
createdAt
updatedAt
completedAt
```

No raw math needed.

## B10. Testcontainers tests
- V015 fresh migration;
- equal timestamp ordering;
- no duplicate pagination rows;
- owner filter;
- cursor boundary;
- max page size;
- optimistic locking;
- V014 selected-parse integrity remains green.

## Package B gate
- [ ] migration green
- [ ] lifecycle policy green
- [ ] history query bounded
- [ ] keyset deterministic
- [ ] no N+1
- [ ] no duplicate source of truth

---

# 12. Package C — Recovery Planner and Stage-Service Integration

## Goal
Derive the exact safe next action and update coarse session lifecycle only at accepted boundaries.

## C1. ProblemSessionRecoveryPlanner
Pure/deterministic where possible.

Input:
- session;
- asset state;
- preprocessing state;
- recognition state;
- parse state;
- selected parse;
- canonical state;
- classification state.

Output:

```text
stage
nextAction
retryable
reviewRequired
failureCode
```

## C2. Upload integration
After source asset becomes AVAILABLE:

```text
CREATED → ASSET_UPLOADED
```

Regression protect existing behavior.

## C3. Pipeline started
When durable post-upload recognition/parse lifecycle begins:

```text
ASSET_UPLOADED → PARSING
```

Coarse `PARSING` may intentionally cover recognition + parser.

## C4. Accepted selected parse
Supported selected parse:

```text
PARSING → PARSED
```

Review-required:

```text
PARSING → REVIEW_REQUIRED
```

USER correction may transition:

```text
REVIEW_REQUIRED → PARSED
```

only if backend semantic state supports it.

## C5. Failure policy
Do not mark coarse FAILED for every transient attempt.

FAILED means the active stage has no currently running work and requires explicit recovery or terminal handling.

## C6. Recognition
- QUEUED/RUNNING → WAIT_RECOGNITION
- FAILED_RETRYABLE → RETRY_RECOGNITION
- terminal → recovery/terminal
- success → next stage

## C7. Parse
- QUEUED/RUNNING → WAIT_PARSE
- FAILED_RETRYABLE → RETRY_PARSE
- REVIEW_REQUIRED → REVIEW_PARSE
- UNSUPPORTED → UNSUPPORTED
- success → selected parse path

## C8. Classification
- QUEUED/RUNNING → WAIT_CLASSIFICATION
- FAILED_RETRYABLE → RETRY_CLASSIFICATION
- semantic UNSUPPORTED → UNSUPPORTED
- success → READY_FOR_SOLVE

## C9. Never repeat successful stages
Regression assertions:

```text
recognition succeeded + parse retry
→ recognition call count unchanged

parse succeeded + classification retry
→ parser call count unchanged

USER-selected parse + canonical missing
→ canonicalization uses USER parse
```

## C10. Running-job resume
If job is already QUEUED/RUNNING:
- detail returns WAIT;
- iOS performs GET polling;
- no duplicate POST.

## C11. Ambiguous lineage
Never guess.

Stable error:

```text
PROBLEM_SESSION_LINEAGE_AMBIGUOUS
```

Emit operational signal.

## C12. Integration tests
Fixture each stage and assert exact nextAction.

Include:
- recognition running;
- recognition retryable;
- parse running;
- parse retryable;
- review required;
- selected USER parse;
- canonical absent;
- classification running;
- classification retryable;
- unsupported;
- ready for solve;
- ambiguous lineage.

## Package C gate
- [ ] every Phase 4 stage mapped
- [ ] GET side-effect free
- [ ] WAIT never causes POST
- [ ] exact-stage retry
- [ ] ambiguous data fails explicitly
- [ ] zero new AI capability

---

# 13. Package D — Public API and OpenAPI

## Goal
Expose owner-scoped history and authoritative detail.

## D1. History endpoint

```http
GET /api/v1/problem-sessions?limit=20&cursor=<opaque>
```

Response:

```json
{
  "items": [],
  "nextCursor": null
}
```

## D2. Detail endpoint

```http
GET /api/v1/problem-sessions/{sessionId}
```

Example:

```json
{
  "problemSessionId": "uuid",
  "status": "PARSING",
  "stage": "RECOGNITION",
  "inputMode": "CAMERA",
  "nextAction": "WAIT_RECOGNITION",
  "retryable": false,
  "reviewRequired": false,
  "failureCode": null,
  "currentParse": null,
  "canonicalProblem": null,
  "classification": null,
  "activeJob": {
    "type": "RECOGNITION",
    "status": "RUNNING",
    "attemptCount": 1,
    "maxAttempts": 3
  },
  "createdAt": "...",
  "updatedAt": "...",
  "completedAt": null,
  "version": 3
}
```

## D3. Do not add generic recover mutation
Avoid:

```http
POST /problem-sessions/{id}/recover
```

Use typed nextAction to route into existing stage endpoint.

Mapping examples:

```text
START_PREPROCESSING → existing preprocess POST
RETRY_RECOGNITION → existing recognition POST
RETRY_PARSE → existing parse POST
REVIEW_PARSE → Sprint 4.8 review
CANONICALIZE → canonicalize POST
RETRY_CLASSIFICATION → classification POST
```

## D4. Active job
Public type:

```text
UPLOAD
PREPROCESSING
RECOGNITION
PARSE
CLASSIFICATION
```

No worker lock internals.

## D5. Stable errors
Recommended:

```text
PROBLEM_SESSION_NOT_FOUND
PROBLEM_SESSION_CURSOR_INVALID
PROBLEM_SESSION_STATE_CONFLICT
PROBLEM_SESSION_LINEAGE_AMBIGUOUS
PROBLEM_SESSION_RECOVERY_NOT_ALLOWED
PROBLEM_SESSION_TERMINAL
```

Only implement codes tied to real behavior.

## D6. BOLA
Both endpoints:
- bearer auth;
- principal-derived user;
- attacker gets concealed 404.

## D7. OpenAPI schemas
Recommended:

```text
ProblemSessionHistoryResponse
ProblemSessionSummaryResponse
ProblemSessionDetailResponse
ProblemSessionStage
ProblemSessionNextAction
ProblemSessionActiveJobResponse
ProblemSessionCurrentParseSummary
ProblemSessionCanonicalSummary
ProblemSessionClassificationSummary
```

## D8. Privacy
Never expose:
- object keys;
- signed URLs;
- raw recognition;
- raw parser output;
- prompts;
- provider secrets;
- request fingerprints;
- cost/token usage in regular history UI;
- local filenames.

## D9. Controller
Create:

```text
ProblemSessionController.java
```

Thin controller only.

## D10. API tests
- 401 history/detail;
- owner 200;
- attacker 404;
- history only owner;
- invalid cursor;
- pagination tie-break;
- no duplicates;
- no internal fields;
- active job mapping;
- ready/terminal mapping.

## Package D gate
- [ ] OpenAPI green
- [ ] BOLA green
- [ ] cursor deterministic
- [ ] public privacy boundary green
- [ ] existing endpoints backward compatible

---

# 14. Package E — iOS Persistence and App Lifecycle Resume

## Goal
Survive termination/background/offline without making local data authoritative.

## E1. SwiftData
SwiftData is cache/recovery context only.

## E2. CachedProblemSessionSummary
Recommended fields:

```text
ownerUserId
problemSessionId
status
stage
inputMode
nextAction
retryable
reviewRequired
createdAt
updatedAt
completedAt
cachedAt
```

## E3. ActiveProblemSessionRecord

```text
ownerUserId
problemSessionId
lastKnownStage
lastKnownNextAction
lastSeenServerVersion
updatedAt
```

## E4. PendingProblemUploadRecord
Needed because local source bytes may exist before durable AVAILABLE upload.

Recommended:

```text
ownerUserId
localAssetId
problemSessionId?
problemAssetId?
uploadId?
localFileReference
checksum
contentType
sizeBytes
reservationExpiresAt?
reservationIdempotencyKey
completionIdempotencyKey
createdAt
updatedAt
```

Persist minimum necessary data.

## E5. Account isolation
On logout:
- remove account-scoped cache/recovery rows.

On confirmed deletion:
- remove cache;
- remove pending local source files.

On account switch:
- never render previous account data.

## E6. Background
- cancel local polling;
- persist active session pointer;
- retain undurable local upload bytes;
- do NOT cancel server jobs.

## E7. Foreground
- restore user;
- GET authoritative detail;
- reconcile local cache;
- resume WAIT polling;
- present retry UI for failed stage.

## E8. Cold relaunch

```text
restore auth
→ profile/entitlement bootstrap
→ restore active session pointer
→ GET detail
→ reconcile
→ offer Continue
```

Do not force navigation automatically.

## E9. One poller per session
Structured concurrency only.
No detached polling tasks.

## E10. Poll restart
Only:

```text
WAIT_RECOGNITION
WAIT_PARSE
WAIT_CLASSIFICATION
```

restart GET polling.

No mutation POST is auto-restarted.

## E11. Reconnect
Reconnect:

```text
GET detail
```

not:

```text
POST recognition/parse/classification
```

## E12. Pending upload
Valid reservation + local bytes:
- resume upload.

Expired reservation + local bytes:
- obtain new safe reservation according to existing upload semantics.

Missing local bytes:
- retake/reimport.

## E13. History cache
Use stale-while-revalidate.

Offline:
- cached history visible;
- online actions disabled;
- explicit offline copy.

## E14. Persistence migration
Update the existing SwiftData migration plan if schema changes.

## E15. Tests
- background cancels polling;
- foreground reload;
- cold relaunch;
- running job resumes GET;
- retryable failure does not auto POST;
- reconnect no AI command;
- offline cache;
- refresh replaces cache;
- logout clears cache;
- user switch isolation;
- missing file;
- expired reservation;
- single poller.

## Package E gate
- [ ] relaunch green
- [ ] background/foreground green
- [ ] no auto mutation
- [ ] account isolation green
- [ ] pending upload recovery green
- [ ] SwiftData non-authoritative

---

# 15. Package F — Library / History and Recovery UX

## Goal
Expose prior/unfinished sessions and safe recovery to the learner.

## F1. Create `Features/Library`
The canonical hierarchy already reserves this feature.

Recommended actual structure:

```text
apps/ios/VerifiedAI/Features/Library/
  Domain/
  Data/
  Presentation/
```

Use existing repo conventions; do not create unnecessary nesting solely for aesthetics.

## F2. Domain models
Potential:

```text
ProblemSessionSummary
ProblemSessionDetail
ProblemSessionStage
ProblemSessionNextAction
ProblemSessionHistoryRepository
```

If stage/action types are shared with Capture/Review/Home, place domain-facing versions in:

```text
SharedDomain/Problem/
```

Avoid duplicated enums.

## F3. Data
Recommended:

```text
ProblemSessionAPI.swift
ProblemSessionDTO.swift
ProblemSessionMapper.swift
ProblemSessionHistoryRepository.swift
ProblemSessionCache.swift
```

## F4. Presentation
Recommended:

```text
ProblemHistoryView.swift
ProblemHistoryViewModel.swift
ProblemSessionDetailView.swift
ProblemSessionDetailViewModel.swift
ProblemSessionRow.swift
ProblemSessionRecoveryCard.swift
ProblemSessionOfflineBanner.swift
```

## F5. History state

```swift
enum ProblemHistoryState {
    case idle
    case loading
    case loaded(items: [ProblemSessionSummary], hasMore: Bool)
    case loadingMore(items: [ProblemSessionSummary])
    case offlineCached(items: [ProblemSessionSummary])
    case empty
    case failed(ProblemHistoryError)
}
```

## F6. Detail state
Typed states for:
- loading;
- ready;
- waiting;
- recoverable failure;
- review required;
- unsupported;
- offline cached;
- failed.

## F7. Semantic copy
User-facing:

```text
Reading problem
Understanding problem
Needs review
Ready to continue
Retry available
Unsupported
```

Do not display raw internal enum language.

## F8. Privacy-minimized row
Do not require raw math text.

Example:

```text
Camera problem
Linear equation
Ready to continue
11 Aug • 13:42
```

If classification absent:

```text
Camera problem
Reading problem
11 Aug • 13:42
```

## F9. Home Continue card
Optionally show latest resumable session.

Do not auto-open on launch.

## F10. Resume router
Typed route mapping:

```text
RESUME_UPLOAD → ProblemCapture upload
WAIT_RECOGNITION → recognition progress
WAIT_PARSE → parse progress
REVIEW_PARSE → ProblemReview
CANONICALIZE → canonicalization
WAIT_CLASSIFICATION → classification progress
READY_FOR_SOLVE → Phase 5 boundary
```

## F11. Retry UX
Use action-specific copy:
- Try reading again
- Try understanding again
- Try classification again

Never “retry everything”.

## F12. Unsupported UX
Explicit limitation; no retry loop.

## F13. Offline
Cached list/detail can render.
Actions requiring backend say:

```text
Connect to continue
```

## F14. Accessibility
- Dynamic Type;
- VoiceOver row summary;
- status not color-only;
- explicit retry labels;
- offline announcement;
- accessible pagination;
- reduced motion.

## F15. Localization
All stage/action/failure/history strings localized.

## F16. UI tests
Happy path:
history → unfinished session → resume polling → continue.

Recovery:
retryable recognition → explicit Retry → recognition only.

Offline:
cached list → action disabled → reconnect → refresh.

## Package F gate
- [ ] history UI green
- [ ] unfinished session reopen green
- [ ] typed resume routing
- [ ] retry UX stage-specific
- [ ] offline green
- [ ] accessibility/localization green
- [ ] Phase 5 boundary respected

---

# 16. Package G — Security, Privacy, Observability, Economics, Rollout

## G1. BOLA
Test:
- another user session UUID;
- another user cursor;
- deep link to another user;
- local cache from previous account.

## G2. Cursor hardening
Validate:
- length;
- encoding;
- timestamp;
- UUID;
- optional version.

Invalid:

```text
400 PROBLEM_SESSION_CURSOR_INVALID
```

## G3. Expensive retry abuse
Do not create a recovery endpoint that bypasses existing stage-specific rate/entitlement policy.

## G4. Privacy
History is Student Personal Data.

Metrics labels allowed:

```text
stage
next_action
outcome
input_mode
failure_class
```

Never:
- userId/sessionId metric labels;
- problem text;
- expression;
- OCR;
- object key;
- filename.

## G5. Training
No new training eligibility.

## G6. Export/deletion
Derived stage/nextAction need not be persisted/exported.
Underlying durable facts remain export source.

Deletion regression:
- server history disappears;
- local cache removed;
- pending local files removed.

## G7. Backend metrics
Recommended:

```text
problem.session.lifecycle.transition.total
problem.session.history.load.total
problem.session.history.load.latency
problem.session.detail.load.total
problem.session.detail.load.latency
problem.session.recovery.plan.total
problem.session.recovery.ambiguous.total
problem.session.retry.requested.total
problem.session.retry.success.total
problem.session.retry.failure.total
```

Only add retry metrics where ownership is clear.

## G8. iOS events
Privacy-safe:

```text
problem_history_opened
problem_session_resumed
problem_session_retry_tapped
problem_session_offline_cache_shown
```

## G9. AI cost guard

```text
history GET inference = 0
detail GET inference = 0
foreground inference = 0
reconnect inference = 0
```

Explicit retry may invoke one existing stage according to policy.

Add an integration fake assertion that read APIs never call `AiModelGateway`.

## G10. Performance
Collect p50/p95:
- history;
- detail;
- DB query latency.

Do not invent production SLO thresholds before baseline.

## G11. Rollout
1. local;
2. Testcontainers;
3. simulator;
4. staging;
5. internal dogfood;
6. history UI enablement;
7. Home Continue card;
8. observe metrics;
9. progressive rollout.

## G12. Feature flags
Possible:

```text
problem_history_enabled
problem_session_resume_enabled
```

Flags must not hide unsafe server invariants.

## G13. Rollback
- V015 additive/index-only if possible;
- endpoints additive;
- older clients unaffected;
- iOS cache disposable but migration-safe;
- if resume UI faulty, disable UI flag and preserve server data;
- never fallback to “retry all”.

## Package G gate
- [ ] BOLA/cursor green
- [ ] no hidden AI calls
- [ ] privacy-safe telemetry
- [ ] deletion/local cleanup green
- [ ] rollout/rollback documented

---

# 17. Package H — Full Production Closure

## H1. Focused backend tests
Expected new tests:

```text
ProblemSessionLifecyclePolicyTest
ProblemSessionRecoveryPlannerTest
ProblemSessionProjectionServiceTest
ProblemSessionHistoryApplicationServiceTest
ProblemSessionControllerTest
FlywayMigrationTest
```

Regression:
- upload;
- recognition;
- parse;
- correction;
- canonicalization;
- classification.

## H2. API
Run:

```text
make test-api
```

## H3. Contracts

```text
make contracts-check
```

## H4. Docs

```text
make docs-check
```

## H5. iOS
Run simulator build and unit/UI tests according to current repository commands.

## H6. Full repository

```text
make check
```

## H7. Git hygiene

```text
git diff --check
git status --short
```

## H8. Required primary demo

```text
start problem
→ recognition RUNNING
→ terminate app
→ relaunch
→ history shows session
→ reopen
→ detail = WAIT_RECOGNITION
→ GET polling resumes
→ no duplicate POST
→ recognition succeeds
→ parse/review/canonical/classification
→ READY_FOR_SOLVE
```

## H9. Required degraded demo

```text
parse FAILED_RETRYABLE
→ relaunch
→ history/detail = RETRY_PARSE
→ user taps retry
→ recognition is NOT repeated
→ parser retries
→ pipeline proceeds
```

## H10. Selected parse demo

```text
AI R1
USER R2 selected
→ relaunch
→ R2 remains current
→ canonicalization uses R2
```

## H11. Execution report
Create:

```text
SPRINT_4.9_EXECUTION_REPORT.md
```

Initial:

```text
PENDING FINAL GATES
```

Do not mark complete until real green evidence.

## Package H gate
- [ ] all focused tests
- [ ] full backend
- [ ] full iOS
- [ ] contracts
- [ ] docs
- [ ] make check
- [ ] demos/evidence
- [ ] execution report
- [ ] matrices reconciled

---

# 18. Backend File-Level Implementation Map

Recommended new files, adjusted to existing naming conventions as necessary:

```text
services/api/src/main/java/com/verifiedai/problem/domain/model/
  ProblemSessionStage.java
  ProblemSessionNextAction.java
  ProblemSessionTransitionCause.java

services/api/src/main/java/com/verifiedai/problem/application/
  ProblemSessionLifecyclePolicy.java
  ProblemSessionRecoveryPlanner.java
  ProblemSessionRecoveryPlan.java
  ProblemSessionProjection.java
  ProblemSessionProjectionService.java
  ProblemSessionHistoryApplicationService.java
  ProblemSessionHistoryPage.java
  ProblemSessionSummary.java
  ProblemSessionCursorCodec.java
  ProblemSessionMetrics.java

services/api/src/main/java/com/verifiedai/problem/api/
  ProblemSessionController.java
  ProblemSessionHistoryResponse.java
  ProblemSessionSummaryResponse.java
  ProblemSessionDetailResponse.java
  ProblemSessionActiveJobResponse.java

services/api/src/main/resources/db/migration/platform/
  V015__harden_problem_session_history_and_recovery_queries.sql
```

Likely existing files to modify:

```text
ProblemSessionJpaEntity.java
ProblemSessionJpaRepository.java
ProblemAssetUploadApplicationService.java
ProblemRecognitionApplicationService.java
ProblemParseApplicationService.java
ProblemParseCorrectionApplicationService.java
CanonicalProblemApplicationService.java
ProblemClassificationApplicationService.java
OpenAPI public-api.yaml
```

Do not modify AI provider adapters without a real defect.

---

# 19. iOS File-Level Implementation Map

Recommended:

```text
apps/ios/VerifiedAI/SharedDomain/Problem/
  ProblemSessionStage.swift
  ProblemSessionNextAction.swift
  ProblemSessionSummary.swift
  ProblemSessionDetail.swift

apps/ios/VerifiedAI/Features/Library/Domain/
  ProblemSessionHistoryModels.swift
  ProblemSessionHistoryPorts.swift

apps/ios/VerifiedAI/Features/Library/Data/
  ProblemSessionAPI.swift
  ProblemSessionDTO.swift
  ProblemSessionMapper.swift
  ProblemSessionHistoryRepository.swift
  ProblemSessionCache.swift

apps/ios/VerifiedAI/Features/Library/Presentation/
  ProblemHistoryView.swift
  ProblemHistoryViewModel.swift
  ProblemSessionDetailView.swift
  ProblemSessionDetailViewModel.swift
  ProblemSessionRow.swift
  ProblemSessionRecoveryCard.swift

apps/ios/VerifiedAI/Core/Persistence/Models/
  CachedProblemSessionSummary.swift
  ActiveProblemSessionRecord.swift
  PendingProblemUploadRecord.swift
```

Potential modifications:

```text
AppDependencies.swift
AppLifecycleHandler.swift
Home feature
ProblemAssetUploadViewModel.swift
ProblemReviewViewModel.swift
Persistence migration plan
Localizable.xcstrings
```

Do not keep expanding the already-large ProblemCapture View/ViewModel into a global history coordinator.

---

# 20. API Contract Reference

## History

```http
GET /api/v1/problem-sessions?limit=20&cursor=<opaque>
```

Suggested response:

```json
{
  "items": [
    {
      "problemSessionId": "uuid",
      "status": "PARSING",
      "stage": "RECOGNITION",
      "inputMode": "CAMERA",
      "nextAction": "WAIT_RECOGNITION",
      "retryable": false,
      "reviewRequired": false,
      "currentParseRevision": null,
      "currentParseSource": null,
      "classificationStatus": null,
      "primarySkillId": null,
      "difficulty": null,
      "createdAt": "...",
      "updatedAt": "...",
      "completedAt": null
    }
  ],
  "nextCursor": "..."
}
```

## Detail

```http
GET /api/v1/problem-sessions/{sessionId}
```

Suggested response:

```json
{
  "problemSessionId": "uuid",
  "status": "PARSING",
  "stage": "PARSING",
  "inputMode": "PHOTO_LIBRARY",
  "nextAction": "WAIT_PARSE",
  "retryable": false,
  "reviewRequired": false,
  "failureCode": null,
  "currentParse": null,
  "canonicalProblem": null,
  "classification": null,
  "activeJob": {
    "type": "PARSE",
    "status": "RUNNING",
    "attemptCount": 1,
    "maxAttempts": 3
  },
  "createdAt": "...",
  "updatedAt": "...",
  "completedAt": null,
  "version": 4
}
```

---

# 21. Failure/Recovery Matrix

| State | Stage | nextAction | Retry command? | Meaning |
|---|---|---|---:|---|
| no AVAILABLE source | AWAITING_UPLOAD | RESUME_UPLOAD | local-dependent | continue upload if bytes exist |
| preprocessing absent | PREPROCESSING | START_PREPROCESSING | yes | prepare asset |
| preprocessing transient fail | PREPROCESSING | RETRY_PREPROCESSING | yes | retry same stage |
| unusable input | TERMINAL | RECAPTURE_OR_REIMPORT | no | new input needed |
| recognition queued/running | RECOGNITION | WAIT_RECOGNITION | no | poll |
| recognition retryable fail | RECOGNITION | RETRY_RECOGNITION | yes | retry recognition only |
| recognition terminal | TERMINAL | RECAPTURE_OR_REIMPORT | no | new input |
| parse absent | PARSING | START_PARSE | yes | start parser |
| parse queued/running | PARSING | WAIT_PARSE | no | poll |
| parse retryable fail | PARSING | RETRY_PARSE | yes | retry parser only |
| parse review required | PARSE_REVIEW | REVIEW_PARSE | user edit | Sprint 4.8 |
| selected parse no canonical | CANONICALIZATION | CANONICALIZE | deterministic command | create safe canonical |
| canonical no classification | CLASSIFICATION | START_CLASSIFICATION | yes | classify |
| classification queued/running | CLASSIFICATION | WAIT_CLASSIFICATION | no | poll |
| classification retryable | CLASSIFICATION | RETRY_CLASSIFICATION | yes | retry classification only |
| classification unsupported | TERMINAL | UNSUPPORTED | no | explicit limitation |
| classification ready | READY_FOR_SOLVE | READY_FOR_SOLVE | no | Phase 5 handoff |

---

# 22. Concurrency Requirements

## Optimistic locking
Keep `@Version` on ProblemSession.

Competing writes must conflict rather than silently overwrite.

## Duplicate retry
Two retry taps must collapse according to existing stage idempotency.

## Read during completion
Projection must be conservative if child state changes while detail is built.

Never produce an impossible “ready” state from mixed lineage.

## History pagination during updates
Use keyset feed semantics:
- refresh restarts page 1;
- no frozen snapshot guarantee across pages;
- deterministic cursor tuple;
- no duplicate row in one forward pagination sequence.

---

# 23. Performance Requirements

Backend history:
- max 50;
- owner+updated index;
- no N+1;
- no raw JSON deserialization;
- no full revision-history scan.

Detail:
- bounded lookup of current artifacts/jobs;
- no provider network call.

iOS:
- cache-first history;
- lazy pagination;
- no detail fetch per list row.

---

# 24. Security Test Matrix

Required abuse cases:

- guessed session UUID;
- another user's cursor;
- malformed cursor;
- oversized cursor;
- page-size abuse;
- duplicate Retry tap;
- Retry while job is RUNNING;
- crafted client nextAction;
- stale local cache after logout;
- deep link to foreign session;
- inactive/deleted account retry;
- account deletion while active session cached.

---

# 25. Privacy Contract

History/list/detail is personal learning data.

Server history should not require raw content.

Logs:
- trace/correlation;
- stage;
- result;
- stable error.

No:
- OCR text;
- normalized expression;
- image bytes;
- object keys;
- unrestricted user identifiers.

Local cache:
- account-scoped;
- sandbox protected;
- removable.

Derived stage/nextAction is not a new training signal.

---

# 26. AI Economics Contract

Normal Sprint 4.9 reads:

```text
history → 0 inference
detail → 0 inference
foreground → 0 inference
reconnect → 0 inference
offline rendering → 0 inference
```

An explicit user retry may cause one attempt in the existing stage route.

Recovery cannot select provider/model/premium fallback.

---

# 27. Observability Contract

Recommended backend metrics:

```text
problem.session.lifecycle.transition.total
problem.session.history.load.total
problem.session.history.load.latency
problem.session.detail.load.total
problem.session.detail.load.latency
problem.session.recovery.plan.total
problem.session.recovery.ambiguous.total
```

Retry metrics only where ownership is clear.

No high-cardinality labels.

Formal SLO thresholds should wait for measured baseline unless an existing project SLO already governs the endpoint.

---

# 28. Full Test Matrix

## Domain
- transition matrix;
- stage resolver;
- nextAction planner;
- illegal transition;
- terminal/retryable distinction.

## Persistence
- V015;
- keyset pagination;
- tie-break;
- owner filter;
- optimistic concurrency;
- existing FK regression.

## API
- auth;
- BOLA;
- cursor;
- schema;
- stable errors;
- privacy field exclusion.

## Integration
- app-termination-style fresh load of durable RUNNING job;
- recognition retry;
- parse retry;
- selected USER parse;
- canonical gap;
- classification retry;
- unsupported;
- ready-for-solve.

## iOS
- cache;
- lifecycle;
- poll cancellation/restart;
- no auto POST;
- pending upload recovery;
- user isolation;
- history/detail ViewModels;
- accessibility;
- UI retry path.

## Privacy
- deletion;
- local cleanup;
- export regression.

## Economics
- fake AiModelGateway call count remains zero for GET/reconnect.

---

# 29. Required Documentation Updates

Create:

```text
SPRINT_4.9_IMPLEMENTATION_MAP.md
SPRINT_4.9_EXECUTION_REPORT.md
```

Review/update:

```text
00_MASTER_INDEX.md
DOCUMENTATION_MANIFEST.md
PHASE_04_PROBLEM_CAPTURE_AND_CANONICALIZATION.md
product/03_END_TO_END_USER_JOURNEYS.md
domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md
backend/20_BACKEND_MODULE_CONTRACTS.md
backend/21_AUTHORIZATION_RATE_LIMITING_AND_ABUSE.md
data/22_POSTGRESQL_DATA_MODEL.md
data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md
ios/15_IOS_ARCHITECTURE_AND_FILE_HIERARCHY.md
ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md
security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md
operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md
roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md
quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md
```

At closure:
- reconcile Sprint 4.7/4.8 capability statuses;
- CAP-PROBLEM-005 → Complete only after evidence;
- REQ-PROBLEM-005 → Satisfied only after evidence.

---

# 30. Final Definition of Done

## Domain
- [ ] lifecycle policy explicit
- [ ] persisted status vs derived stage separated
- [ ] backend-derived nextAction
- [ ] client cannot bypass lifecycle
- [ ] selected parse preserved
- [ ] current canonical exact
- [ ] current classification exact

## History
- [ ] owner-scoped history endpoint
- [ ] keyset pagination
- [ ] deterministic tie-break
- [ ] no N+1
- [ ] privacy-minimized list
- [ ] bounded page size

## Resume
- [ ] detail endpoint
- [ ] running recognition resumes polling
- [ ] running parse resumes polling
- [ ] running classification resumes polling
- [ ] relaunch no duplicate POST
- [ ] reconnect no hidden AI work

## Retry
- [ ] recognition retries recognition only
- [ ] parse retries parse only
- [ ] classification retries classification only
- [ ] successful prior work reused
- [ ] terminal semantic states not auto retried
- [ ] duplicate retry idempotent

## Upload
- [ ] pending local source survives termination until durable
- [ ] expired reservation recoverable
- [ ] missing file requires retake/reimport
- [ ] local cleanup obeys lifecycle

## iOS
- [ ] Library/history implemented
- [ ] cache stale-while-revalidate
- [ ] offline history
- [ ] unfinished session reopen
- [ ] one poller/session
- [ ] background cancels poll only
- [ ] foreground reloads server
- [ ] account-isolated cache
- [ ] logout/delete cleanup

## UX
- [ ] semantic stage copy
- [ ] stage-specific Retry
- [ ] unsupported UI
- [ ] offline UI
- [ ] optional Home Continue
- [ ] accessibility
- [ ] localization
- [ ] no fake progress percentage

## Security/privacy
- [ ] BOLA
- [ ] cursor tenant-safe
- [ ] no raw content logs
- [ ] no raw content analytics
- [ ] no training eligibility change
- [ ] delete regression
- [ ] expensive retry policy not bypassed

## Observability/economics
- [ ] transition metrics
- [ ] history/detail latency
- [ ] recovery-plan metrics
- [ ] ambiguity metric
- [ ] GET history zero AI calls
- [ ] GET detail zero AI calls
- [ ] reconnect zero automatic AI calls

## Persistence/contracts
- [ ] V015 green if needed
- [ ] index/query tested
- [ ] optimistic locking green
- [ ] no duplicate source-of-truth table
- [ ] OpenAPI synced
- [ ] iOS DTO fixtures synced
- [ ] stable errors

## Final gates
- [ ] Maven clean compile
- [ ] focused unit tests
- [ ] Testcontainers
- [ ] ApplicationContextTest
- [ ] FlywayMigrationTest
- [ ] full API tests
- [ ] iOS simulator build
- [ ] iOS unit tests
- [ ] critical UI tests
- [ ] make contracts-check
- [ ] make docs-check
- [ ] make test-api
- [ ] make check
- [ ] git diff --check
- [ ] execution report
- [ ] capability/RTM reconciliation

---

# 31. Required Production Demos

## Demo 1 — Termination/relaunch

```text
recognition RUNNING
→ kill app
→ relaunch
→ history contains session
→ detail = WAIT_RECOGNITION
→ polling resumes
→ no recognition POST
→ durable job finishes
→ pipeline continues
```

## Demo 2 — Exact-stage retry

```text
parse FAILED_RETRYABLE
→ relaunch
→ RETRY_PARSE
→ user taps
→ recognition count unchanged
→ parser retries
→ continues
```

## Demo 3 — Sprint 4.8 regression

```text
AI R1
USER R2 selected
→ relaunch
→ R2 current
→ canonicalization uses R2
```

Each demo must be backed by automated test evidence and privacy-safe trace/metric evidence.

---

# 32. Phase 4.10 Handoff

Sprint 4.10 may assume only after Sprint 4.9 COMPLETE:

- every session can be deterministically projected;
- every recovery path is explicit;
- retryability is measurable;
- app termination does not destroy ingestion progress;
- prior successful AI work is reusable;
- history/resume is production-like;
- difficult-input/golden tests can exercise stable lifecycle/recovery semantics.

Do not enter Sprint 4.10 while session recovery still depends on undocumented screen state or manual DB intervention.

---

# 33. Recommended First Coding Slice

Start with **Package A + early Package B**:

1. add/reconcile `REQ-PROBLEM-005`;
2. freeze `ProblemSessionStage`;
3. freeze `ProblemSessionNextAction`;
4. implement lifecycle transition policy + unit tests;
5. implement RecoveryPlanner + full truth-table tests;
6. add V015 history index;
7. implement owner-scoped history/detail projections;
8. run compile + Flyway + ApplicationContext + focused tests.

Do NOT start with the Swift history screen.

The server must first answer correctly:

```text
What is this session doing?
What is safe next?
```

---

# 34. Completion Statement Template

Use only after actual green gates:

```text
Sprint 4.9 COMPLETE

Delivered:
- durable ProblemSession lifecycle projection;
- owner-scoped keyset-paginated history;
- authoritative detail/recovery planning;
- exact-stage retry/resume;
- app termination/background/relaunch continuity;
- SwiftData cache and pending-upload recovery;
- iOS Library/history/recovery UX;
- privacy-safe observability;
- zero hidden AI work on reads/reconnect;
- full regression and documentation synchronization.

Phase 4:
4.1 COMPLETE
4.2 COMPLETE
4.3 COMPLETE
4.4 COMPLETE
4.5 COMPLETE
4.6 COMPLETE
4.7 COMPLETE
4.8 COMPLETE
4.9 COMPLETE
4.10 NEXT
```
