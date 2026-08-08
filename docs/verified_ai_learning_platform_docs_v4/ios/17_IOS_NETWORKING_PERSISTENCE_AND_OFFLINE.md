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
- CachedSolution,
- CachedMistake,
- CachedMasterySnapshot,
- CachedStudyPlan,
- PendingSyncAction.

## Cache policies

- History: stale-while-revalidate.
- Mastery: display cache instantly; refresh background.
- Today plan: cache current snapshot; server owns completion.
- Entitlement: cache for UI responsiveness but expensive server operations re-check authority.
- Learning profile: onboarding draft lives in view state until saved; durable resume state comes from `/api/v1/me/learning-profile`.
- Current entitlement: `EntitlementDisplayCache` may render last known tier/capabilities while offline, but a server refresh overwrites it and privileged backend actions must still pass server-side capability policy.
- Account privacy: export and deletion are online-only because the backend must derive current-user ownership, apply retention policy, revoke sessions, and record privacy audit events.

## Offline availability

Available:
- saved history,
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
<!-- HYBRID_AI_STRATEGY_V3:END -->
