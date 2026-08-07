# Domain Events and State Machines

## Event design

Events are immutable completed facts, not method-call logs.

Good: `VerificationCompleted`
Bad: `VerificationServiceMethodCalled`

## Core events

Identity:
- UserRegistered
- UserDeletionRequested
- UserDeleted

Problem:
- ProblemSessionCreated
- ProblemAssetRegistered
- ProblemParsingStarted
- ProblemParsed
- ProblemParseFailed
- ProblemSolveRequested

Solving:
- PrimarySolutionGenerated
- SecondarySolutionGenerated
- SolverDisagreementDetected
- SolutionGenerated

Verification:
- VerificationStarted
- VerificationCompleted
- VerificationRequiresReview

Attempt/Mistake/Mastery:
- AttemptSubmitted
- AttemptEvaluated
- MistakeDetected
- MistakeReviewed
- MasteryUpdated

Study/Exam:
- StudyPlanCreated
- StudyPlanRecalculationRequested
- StudyPlanRebalanced
- StudySessionCompleted
- UserExamCreated
- MockExamCompleted
- ReadinessUpdated

Billing:
- EntitlementActivated
- EntitlementChanged
- EntitlementExpired

## ProblemSession state machine

States:
CREATED → ASSET_UPLOADED → PARSING → PARSED → SOLVING → VERIFYING → COMPLETED

Alternative terminal/intermediate states:
REVIEW_REQUIRED, FAILED, CANCELLED.

Illegal transitions such as CREATED → COMPLETED are rejected.

## Solve job state machine

QUEUED
RUNNING
WAITING_EXTERNAL
RETRY_SCHEDULED
SUCCEEDED
FAILED
CANCELLED

Retries are operational and must remain idempotent at business-result level.

## Entitlement state machine

NONE
ACTIVE
GRACE_PERIOD
BILLING_RETRY
EXPIRED
REVOKED

External billing events are applied idempotently using stable external event/transaction identifiers.

## Event publication guarantee

For durable cross-module reactions, write business state and event publication/outbox record atomically in one transaction.

Consumers are idempotent.

## Event payload rule

Events carry identifiers and facts, not giant entity snapshots.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Additional operational events

Future governance/operations may emit internal events such as:

- `AiRouteSelected`
- `AiRouteEscalated`
- `ModelEvaluationCompleted`
- `ModelReleasePromoted`
- `ModelReleaseRolledBack`
- `TrainingEligibilityRevoked`

These are operational/governance events and must not be confused with learner evidence events.
<!-- HYBRID_AI_STRATEGY_V3:END -->
