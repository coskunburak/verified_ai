# Domain Model and Aggregates

## Aggregate philosophy

Aggregates define consistency boundaries. Do not create one giant Student aggregate.

## Identity Aggregate

User:
- UserId,
- status,
- createdAt,
- deletedAt.

Rules:
- deleted/disabled user cannot create new learning actions,
- identity changes must not silently destroy billing state.

## LearningProfile Aggregate

Fields:
- UserId,
- educationLevel,
- preferredLanguage,
- explanationDepth,
- dailyStudyMinutes,
- timezone,
- optional goal context.

Profile edits do not directly change mastery.

## Curriculum Model

Entities:
- Subject,
- Topic,
- Skill,
- SkillPrerequisite,
- Curriculum,
- CurriculumSkill.

Skill IDs are stable semantic codes, e.g. `MATH.CALCULUS.DERIVATIVE.CHAIN_RULE`.

## ProblemSession Aggregate

Represents lifecycle initiated by one user problem submission.

Fields:
- ProblemSessionId,
- UserId,
- status,
- input mode,
- selected parse reference,
- canonical Problem reference,
- solve job reference,
- timestamps.

Problem assets are metadata references to object storage.

States:
CREATED, ASSET_UPLOADED, PARSING, PARSED, SOLVING, VERIFYING, COMPLETED, REVIEW_REQUIRED, FAILED, CANCELLED.

## Problem

Canonical semantic fields:
- subject,
- topic,
- primary/secondary skills,
- difficulty estimate,
- problem type,
- normalized representation,
- language,
- provenance.

## Solution Aggregate

Solution:
- SolutionId,
- ProblemId,
- method,
- final answer representation,
- generation metadata.

SolutionStep:
- order,
- mathematical expression,
- public explanation,
- rule/concept references.

SolverRun is immutable evidence and stores provider/model/prompt/cost/latency metadata.

## Verification Aggregate

VerificationRun:
- id,
- solution,
- policy version,
- overall status,
- timestamps.

VerificationSignal:
- method,
- status,
- verifier version,
- evidence metadata.

Historical verification runs are immutable. Reverification creates a new run.

## Attempt Aggregate

Attempt:
- user,
- problem,
- source,
- submitted work,
- final answer,
- evaluation status.

AttemptStep stores structured work. Student work remains distinct from reference Solution.

## Mastery Aggregate

Key: UserId + SkillId.

Fields:
- score [0,1],
- confidence [0,1],
- evidenceCount,
- lastUpdated,
- algorithmVersion.

MasteryHistory stores previous/new values and cause.

## StudyPlan Aggregate

StudyPlan:
- user,
- horizon,
- version,
- status.

StudyPlanItem:
- date/window,
- skill,
- activity type,
- target duration/count,
- priority,
- reason.

## UserExam Aggregate

- examDefinition,
- targetDate,
- optional targetScore,
- status,
- readiness history.

## Entitlement Aggregate

- user,
- tier,
- status,
- effective/expiry,
- transaction identifiers.

Only billing domain mutates authoritative entitlement.

## Read models

UI should use projections such as:
- HomeDashboard,
- ProblemResult,
- MistakeBookSummary,
- SkillMasteryTree,
- TodayPlan,
- ExamReadiness.

Read models may denormalize but never become write authority.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Model-governance supporting concepts

AI usage provenance, route releases, dataset eligibility and future model artifacts are operational/governance records, not learner-domain aggregates. They must not pollute core `Problem`, `Attempt`, `Mastery`, or `Exam` semantics.

Potential supporting aggregates/modules:

- `AiUsageRecord` / usage ledger;
- `ModelRouteRelease`;
- `EvaluationRun`;
- future `DatasetVersion` and `ModelArtifact` in a dedicated ModelOps/AI governance boundary.

Learner state must remain valid if the implementation route changes from external API to proprietary model.
<!-- HYBRID_AI_STRATEGY_V3:END -->
