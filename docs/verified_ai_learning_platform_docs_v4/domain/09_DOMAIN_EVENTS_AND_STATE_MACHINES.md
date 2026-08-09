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
- ProblemRecognitionRequested
- ProblemRecognitionStarted
- RecognitionEvidenceRecorded
- ProblemRecognitionFailed
- ProblemParsingStarted
- ProblemParsed
- ProblemParseFailed
- ProblemParseUnsupported
- ProblemParseReviewRequired
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

## Recognition job state machine

QUEUED
RUNNING
SUCCEEDED
FAILED_RETRYABLE
FAILED_TERMINAL

Sprint 4.4 recognition jobs are idempotent by user, ProblemSession, selected recognition input derivative, capability, prompt version, and schema version. Successful evidence is immutable for that input/provenance tuple. Retryable failures use bounded attempts and stale RUNNING jobs are recoverable by worker policy.

## Problem parse job state machine

QUEUED
RUNNING
SUCCEEDED
FAILED_RETRYABLE
FAILED_TERMINAL
UNSUPPORTED

Sprint 4.5 parse jobs are idempotent by user, ProblemSession, exact RecognitionEvidence revision, `PROBLEM_NORMALIZE`, prompt version, schema version, and route policy version. Successful, review-required, and unsupported parser outcomes create immutable `problem_parses` revisions. Schema-invalid provider output can retry within the bounded policy and creates no revision until accepted; semantic-invalid output terminally fails the job without durable parse revision.

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
