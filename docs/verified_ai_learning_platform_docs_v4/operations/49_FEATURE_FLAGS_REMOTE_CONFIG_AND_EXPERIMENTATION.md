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
<!-- HYBRID_AI_STRATEGY_V3:END -->
