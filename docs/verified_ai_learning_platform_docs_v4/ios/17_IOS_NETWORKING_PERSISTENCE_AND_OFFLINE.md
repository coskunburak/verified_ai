# iOS Networking, Persistence, and Offline Strategy

## Networking

Use URLSession + async/await.

`APIClient` owns:
- base URL,
- encoding/decoding,
- auth headers,
- token refresh coordination,
- idempotency header,
- trace IDs,
- standardized errors.

## Token refresh

Only one refresh task runs at a time. Requests encountering expiration await the shared refresh. A failed refresh transitions auth state once rather than causing retry storms.

## Retries

Retry selected transient failures. Commands require idempotency. Never blindly replay an unsafe request.

Sprint 4.2 upload retries keep the accepted local asset until the backend returns an AVAILABLE durable reference. Reservation and completion use stable `Idempotency-Key` values derived from the local accepted asset; presigned object PUT is retried only as part of a bounded user-visible retry path. Offline state is recoverable because the local bytes are retained.

## Solve polling

Repository starts solve and polls job status with bounded cadence/backoff. Persist job ID so backgrounding/relaunch can resume.

## SwiftData role

SwiftData is local cache/offline projection, not backend authority.

Candidate cache models:
- CachedProblemSummary,
- CachedProblemSession,
- CachedSolution,
- CachedMistake,
- CachedMasterySnapshot,
- CachedStudyPlan,
- PendingSyncAction.

## Cache policies

- History: stale-while-revalidate.
- Problem session recovery: render cached session summaries while offline, then replace them with backend history/detail projections on refresh. Cached `stage`, `nextAction`, parse, canonical, and classification summaries are display state only; the server decides every recoverable action.
- Mastery: display cache instantly; refresh background.
- Today plan: cache current snapshot; server owns completion.
- Entitlement: cache for UI responsiveness but expensive server operations re-check authority.
- Learning profile: onboarding draft lives in view state until saved; durable resume state comes from `/api/v1/me/learning-profile`.
- Current entitlement: `EntitlementDisplayCache` may render last known tier/capabilities while offline, but a server refresh overwrites it and privileged backend actions must still pass server-side capability policy.
- Account privacy: export and deletion are online-only because the backend must derive current-user ownership, apply retention policy, revoke sessions, and record privacy audit events.

## Offline availability

Available:
- saved history,
- cached problem-session recovery summaries,
- solutions already downloaded,
- mistake book,
- mastery snapshot,
- downloaded lessons.

Not available:
- new AI solve,
- new server verification,
- authoritative billing changes.
- account data export or account deletion confirmation.
- new durable problem-asset upload while offline; the capture can remain local and retry after connectivity returns.
- foreground/reconnect auto-recovery that would start recognition, parsing, canonicalization, classification, solving, verification, or any AI provider call without an explicit backend command and user action.

## Conflict semantics

- Profile edits use versioning/optimistic conflict.
- Study completion is append/idempotent.
- Mastery is server-only and never client-merged.
- Entitlement conflicts are not client-resolved in Sprint 3.4 because normal clients cannot mutate entitlement.

## Local security

Tokens: Keychain.
Cached data: app sandbox and appropriate file protection.
Never persist provider secrets or log full raw problem content casually.

Sprint 4.2 direct object upload uses presigned URLs from the backend and must not attach bearer Authorization headers to the object-storage PUT request. Local temporary capture bytes are deleted only after upload completion is confirmed by the backend and the durable `ProblemAsset` is AVAILABLE.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI request/retry economics on mobile

The client must not implement aggressive automatic retries for expensive solve/tutor operations. Retry semantics come from backend job/error contracts and idempotency keys. Network reconnect must not duplicate solves or trigger multiple paid inference pipelines.

Persist user-visible results/history, not provider-specific routing configuration.

Sprint 4.9 problem-session history/resume uses `GET /api/v1/problem-sessions` and `GET /api/v1/problem-sessions/{sessionId}` as read-only recovery projections. Running jobs are resumed by polling/detail refresh. Exact-stage retry uses the existing stage command named by the backend-derived `nextAction`; there is no client-side recover-all command.

Sprint 4.7 mobile classification uses only backend commands and reads:
`POST /api/v1/problem-sessions/{sessionId}/canonicalize`,
`GET /api/v1/problem-sessions/{sessionId}/canonical-problem`,
`POST /api/v1/problem-sessions/{sessionId}/classification`, and
`GET /api/v1/problem-sessions/{sessionId}/classification`. The capture
view-model polls classification with bounded backoff and treats
`CLASSIFIED`, `REVIEW_REQUIRED`, `UNKNOWN`, and `UNSUPPORTED` as
backend-owned semantic outcomes. The client never invents skill IDs,
difficulty, confidence, or taxonomy labels while offline or during retry.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## Sprint 6.4 tutor recovery

Tutor APIClient models reload server-authoritative sessions and ordered messages. An atomic protected,
backup-excluded pending operation file stores only the unresolved create/send key, target and bounded
original content; it is not a transcript store. Retry preserves key/payload across relaunch and removes
the marker only after acknowledgement or definitive rejection. Account/logout cleanup removes pending
data. Active owner, authentication session, task cancellation and presentation generation guard results.
Offline presentation cannot create confirmed messages. Tutor replies/hints are explicitly unavailable.

## Sprint 6.6 hint recovery

TutorServicing/API/ViewModel use server-returned nextLevel/availability. Existing protected pending markers
add hint/reveal kinds and an expected hint level, preserving legacy append/turn decoding. Lost acknowledgement
retains the exact operation key and level; duplicate taps, offline creation and account-invalid responses
are blocked. A restart reloads server history and any unresolved marker before permitting new work.
New EN/TR controls distinguish H1/H2/H3 from a confirmed reveal request, display exhaustion/unavailability,
and retain composer drafts during hints. Accessibility focus/announcements and 44-point controls introduce
no animation. Real-device/VoiceOver review remains owner-operated. No new Swift file needs registration.

## Sprint 6.7 pending attempts and learning recovery

AttemptAnalysisPendingStore saves exact source/text/key before POST and separate reviewed drafts, atomically with
complete file protection/backup exclusion and account/problem isolation. Both writes and reads have encoded size
bounds. Lost acknowledgement/cancellation preserves pending identity; definitive rejection preserves draft text.
Generation/current-account checks fence every async result. Native capture previews remain memory-only. Server
learning history recovers committed drills on reopen; first-answer replay cannot duplicate mastery. Logout clears
local pending/drafts and learning state. Offline correctness is never inferred; verified artifacts come from server.

## Sprint 7.1 final answers and draft revision recovery

Analyze My Work retains manual/photographic step analysis and adds final-answer mode with optional intermediate work.
An explicit review sheet precedes capture-only submission v3. APIClient remains the only transport; native history uses
representationVersion=2. A returned student-attempt-v2/SUBMITTED clears the pending marker as a saved acknowledgement,
and is labeled Saved / Not assessed in EN/TR. Legacy SUBMITTED remains a retryable assessment state. No green/verified
or mastery claim is inferred. Historical earlier-source work is explicitly labeled.

Draft files now persist source identity and final answer. Older/changed-source drafts retain exact text but require
review against the current context before a new operation is allowed. Existing unresolved operations always retry
the original source and key. Account/generation/cancellation fences apply after network suspensions. Acknowledgement
removes draft and pending files; selecting history cannot recreate an acknowledged draft. Store failures retain the
pending identity for retry. Native/photo provenance and existing protected backup-excluded storage remain intact.

## Sprint 7.2 structured review and recovery

AnalyzeMyWorkViewModel explicitly opts into the context-v2 structuredWorkEnabled capability. Existing entry and answer-only
submission paths remain available. Native/gateway review creates immutable observations and independently editable steps;
movement preserves IDs, splitting at a learner-supplied newline shares observation links, merging retains all links and
requires renewed review. The OCR sheet can continue into the structured editor without attestation, so ambiguous/multiline
observations can be split and reviewed without first claiming confirmation. Edits/role changes revoke confirmation. Normalized preview uses existing native AttemptMathView
only after a server artifact is saved; UI labels separately identify saved, structured, unsupported and unassessed states.

Native source previews are owner/problem/capture-bound protected local files. Vision boxes use its known bottom-left origin;
gateway metadata does not declare an origin in the existing contract, so the UI shows the full source and an explicit warning
instead of guessing a region overlay. All boxes/raw transcription remain in provenance. Geometry never proves 2D mathematics.

Structured pending requests retain exact body/key in protected account-scoped storage (20 KB envelope; 16 KiB wire input).
Structured drafts allow a bounded 20 KB envelope; legacy bounds remain 16 KiB. Source changes require explicit draft review;
new source clears predecessor intent. Canceled/late/account-switched responses cannot publish. Retry after a lost acknowledgement
uses the same key even when rollout is disabled. Definitive 400/409/413/422 preserves a review draft before releasing the key.
Artifact reads remain enabled during rollback, and old servers without the capability are not queried for the new route.
EN/TR labels, VoiceOver control names, Dynamic Type layouts and unassessed copy are covered by authored XCTest/XCUITest sources.

## Sprint 6.10 tutor recovery hardening

Authenticated refresh restores the existing protected pending operation's content and intent into the composer. It never replays a mutation on reconnect/resume; explicit retry retains the original key. Unsent drafts remain in-memory and survive screen background/foreground within the same view model; no additional durable draft storage is introduced. History loading is capped at the existing 100-message boundary, refuses overrun pages, and checks cancellation/current-account authority after each page before continuing. Existing account invalidation clears visible content; source and Phase 7 acknowledgement/binding contracts remain unchanged.

## Sprint 7.3 evaluation recovery

Analyze My Work reads evaluation after saved-work/history reads. Only the explicit evaluation action sends POST.
A protected AttemptEvaluationPending retains attempt, exact source/revision, real-domain scope claim and idempotency key.
Reopening/reconnect does not POST; GET recovers a lost completed acknowledgement and clears pending state. IN_PROGRESS
requires refresh; RETRYABLE permits explicit same-key retry. Account/generation fences reject late responses. New work
revisions clear visible correctness; historical evaluations show a badge. Source-unavailable context does not hide saved
history. Final correctness is shown separately from step status and includes the reviewed-real-scope notice where applicable.

## Sprint 7.4 classification recovery

Analyze My Work requests classification explicitly after accepted evaluation. The stable key
classification-v1-<lowercase evaluation UUID> binds retries across relaunch; it contains no label authority.
Resume performs GET only. Account/generation/evaluation identity fences prevent late publication; revision
changes clear old presentation. Transient errors preserve accepted evaluation and expose existing retry UI.
Strict typed validation checks source/owner/execution/causal step and category/strength/skill compatibility.
No new local learner-content cache or provider access is added. EN/TR and non-color evidence/review labels
are covered by owner-only unit/UI accessibility sources.
