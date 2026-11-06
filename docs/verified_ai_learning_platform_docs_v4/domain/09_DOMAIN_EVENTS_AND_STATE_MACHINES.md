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

## Sprint 7.1 saved evidence state

Legacy attempts transition SUBMITTED → ANALYZED or STALE_SOURCE once; retries of transient verifier failures
reuse SUBMITTED and the immutable request. Final-answer capture uses SUBMITTED as saved/unassessed, with no
background assessment job; a replay returns that same acknowledgement. Its schema prohibits ANALYZED in this
sprint. The existing conceptual AttemptSubmitted event is not a correctness or mastery event; no new event bus
or learning consumer is introduced. Reads never transition state.

## Sprint 7.2 review and syntax states

Structured capture leaves StudentAttempt SUBMITTED and analysis null. CONFIRMED is a learner transcription attestation;
REQUIRED is explicit unresolved review. NORMALIZED, UNSUPPORTED and REVIEW_REQUIRED describe representation only.
A changed text, role or segmentation revokes local confirmation. A saved revision is immutable; a correction creates
another attempt/child under the same source, with revision+1 and a unique predecessor link. No assessment, mistake or
mastery event is published by this route. Existing Sprint 6.7 evaluation transitions are not redefined.
