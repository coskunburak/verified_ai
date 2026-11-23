# Feature Flags, Remote Configuration, and Experimentation

## Purpose

AI and learning behavior should be deployable gradually without requiring an App Store release for every backend/model change.

## Flag categories

### Release flags
Enable incomplete/new capability safely.

Examples:
- secondary_solver_enabled
- advanced_verification_enabled
- tutor_v2_enabled
- adaptive_plan_v2_enabled

### Experiment flags
A/B test product behavior.

### Operational kill switches
Disable expensive or unsafe functionality immediately.

### Configuration values
Daily solve quota, route thresholds, timeout budgets and UI-safe server configuration.

## Ownership

Backend remains authority for any flag affecting security, billing, cost or learning semantics.

Client remote config may change presentation but cannot bypass server policy.

## Rollout

Typical progressive rollout:
1%
5%
25%
50%
100%

Use user-stable assignment to avoid variant flicker.

## AI experiments

Every model/prompt experiment records variant in AI usage/evaluation telemetry. A/B rollout does not replace golden evaluation.

## Flag lifecycle

Every flag has:
- owner,
- purpose,
- created date,
- planned removal date,
- default state.

Remove stale flags after rollout to avoid permanent branching complexity.

## Emergency behavior

Kill switch examples:
- disable a verification method associated with false positives,
- disable secondary solver during severe provider cost/outage,
- disable PDF uploads during a security incident.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Model-routing flags

Support audited remote configuration/flags for:

- provider/model route selection;
- secondary-solver escalation policy;
- strong-model percentage;
- proprietary-model shadow/canary percentage;
- emergency provider/model kill switches.

Flags cannot override domain invariants or verification truthfulness.

## Sprint 10.7 implemented feature-flag and experimentation contract

- **Server-authoritative storage (`V071`):** `feature_flag_definitions`, `experiment_definitions`, `experiment_assignments`, and immutable `feature_flag_audit_logs`, all enforcing `training_eligible = FALSE` (`CHECK (NOT training_eligible)`) and zero incremental AI spend (`llmTokensUsed = 0`).
- **Narrow SPI (`com.verifiedai.featureflags.application`):** Downstream modules consume only `FeatureFlagEvaluationPort` across the Spring Modulith `@NamedInterface("application")` boundary (`client_safe = FALSE` flags such as `secondary_solver_enabled`, `strong_model_pct`, `proprietary_canary_pct`, and `daily_solve_quota_override` are evaluated strictly server-side and never serialized to `/config/remote`).
- **Kill-switch supremacy (`INV-FF-002`):** Active kill switches override enabled state, rollout percentages, and linked experiments (`EXP-LOS-08` falls back to `control_quota_wall` while `client_mistake_moment_paywall_ui` is killed), enforced both server-side and in iOS `RemoteFeatureFlagStore` (`ProblemCaptureView` PDF upload guard).
- **Client delivery & governance:** `GET /api/v1/config/remote` and `/v1/config/remote` emit `Cache-Control: no-store, private` with strong content `ETag` (`304 Not Modified` on `If-None-Match`). `ROLE_ADMIN` governance endpoints under `/api/v1/admin/feature-flags*` and `/v1/admin/feature-flags*` require `Idempotency-Key`, optimistic `expectedVersion`, and legal `ExperimentStateMachine` transitions (`KILLED -> RUNNING` blocked with `409 FEATURE_FLAG_INVALID_TRANSITION`; `CONCLUDED` terminal).
<!-- HYBRID_AI_STRATEGY_V3:END -->
