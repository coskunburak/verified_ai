# Backend Module Contracts

## identity
Owns user account, external identity bindings, refresh token/session lifecycle.

Commands: authenticateWithApple, refreshSession, logout, requestDeletion.

## profile
Owns learning profile and preferences. Cannot mutate mastery.

## curriculum
Owns canonical skill graph and curricula. Primarily read/query at runtime; changes use controlled import/admin process.

## problem
Owns problem sessions, asset metadata, parse versions, selected parse and solve-request lifecycle.

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
Owns App Store server sync, product mapping and entitlement. Other modules query capabilities, not StoreKit internals.

## ai
Owns model routing, provider adapters, prompt registry access and AI usage accounting. It does not own learning/business meaning.

## admin
Read-heavy operational tooling. Any mutation goes through normal domain commands; no direct DB edit endpoint.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI and future ModelOps contract

`ai` may depend on external provider adapters and inference clients. Product modules depend only on capability ports/application services.

Future `modelops` tooling may read governed evaluation/training artifacts but cannot directly mutate learner mastery, verification, billing, or exam state. Promotion of a model changes routing configuration, not domain ownership.
<!-- HYBRID_AI_STRATEGY_V3:END -->
