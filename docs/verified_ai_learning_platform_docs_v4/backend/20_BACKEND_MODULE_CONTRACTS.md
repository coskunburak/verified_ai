# Backend Module Contracts

## identity
Owns user account, external identity bindings, refresh token/session lifecycle.

Commands: authenticateWithApple, refreshSession, logout, requestDeletion.

## profile
Owns learning profile and preferences. Cannot mutate identity, entitlement, or mastery. Current API scope is authenticated `/api/v1/me/learning-profile` read/upsert with one profile per user and optimistic update conflicts.

## curriculum
Owns canonical skill graph and curricula. Primarily read/query at runtime; changes use controlled import/admin process.

## problem
Owns problem sessions, asset metadata, parse versions, selected parse and solve-request lifecycle.

Sprint 4.2 scope within `problem`:
- owns `ProblemSession` minimal lifecycle `CREATED -> ASSET_UPLOADED`;
- owns `ProblemAsset` identity, user ownership, object key, upload status, metadata, checksum expectation, and retention class;
- exposes thin upload controllers under `/api/v1/uploads`;
- depends on object storage through `ProblemAssetStorage`, not directly from domain/application semantics;
- depends on billing only through the public `CapabilityAccessPolicy` application interface and never on StoreKit/App Store internals.

## solving
Receives canonical Problem, creates SolverRuns and Solution. Does not decide VERIFIED.

## verification
Owns verification planner, policy, runs and signals. Sole authority for overall verification status.

## tutoring
Owns tutor session state, hint policy and tutor-specific progression.

## attempt
Owns student-submitted work and attempt evaluation. Publishes evaluation facts.

## mistake
Owns taxonomy and structured diagnoses. Consumes attempt evidence.

## mastery
Owns User × Skill state/history. Consumes approved evidence events.

## studyplan
Reads mastery, exam context and review due state. Creates structured plans. Cannot mutate mastery directly.

## exam
Owns exam definitions, user targets, readiness and mock assessment orchestration.

## billing
Owns entitlement state, App Store product mapping, Apple transaction verification, subscription lifecycle projection, notification ingestion and reconciliation. Current Sprint 3.5/3.6 scope includes:
- `GET /api/v1/me/entitlements` for backend-authoritative access state.
- `GET /api/v1/me/billing/apple/configuration` for purchase availability, backend product catalog and stable `appAccountToken`.
- `POST /api/v1/me/billing/apple/transactions` for authenticated StoreKit verified transaction JWS submission with `Idempotency-Key`.
- `POST /api/v1/webhooks/apple/app-store` for unauthenticated App Store Server Notifications V2 signed payload ingestion.

Other modules query capabilities and entitlement state. They do not depend on StoreKit internals, App Store product IDs, client-provided tier flags, or local purchase UI state.

## ai
Owns model routing, provider adapters, prompt registry access and AI usage accounting. It does not own learning/business meaning.

## admin
Read-heavy operational tooling. Any mutation goes through normal domain commands; no direct DB edit endpoint.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI and future ModelOps contract

`ai` may depend on external provider adapters and inference clients. Product modules depend only on capability ports/application services.

Future `modelops` tooling may read governed evaluation/training artifacts but cannot directly mutate learner mastery, verification, billing, or exam state. Promotion of a model changes routing configuration, not domain ownership.
<!-- HYBRID_AI_STRATEGY_V3:END -->
