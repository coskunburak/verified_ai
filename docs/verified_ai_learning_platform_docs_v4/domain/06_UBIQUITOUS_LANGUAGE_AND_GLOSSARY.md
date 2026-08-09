# Ubiquitous Language and Glossary

This vocabulary is canonical across code, API, database, analytics, documentation and AI-agent instructions.

## Identity

### User
Authenticated account. Not equivalent to learner profile.

### UserIdentity
External identity binding such as Sign in with Apple.

### LearningProfile
Academic preferences and learning settings.

## Curriculum

### Subject
High-level discipline; initially MATHEMATICS.

### Topic
Hierarchical grouping such as CALCULUS.

### Skill
Smallest durable mastery unit, e.g. CHAIN_RULE.

### Curriculum
Versioned mapping of supported skills to a program/exam.

## Problem

### ProblemAsset
Raw file/input artifact such as image or PDF page.

### ProblemParse
Provider-independent structured interpretation of accepted RecognitionEvidence. In Sprint 4.5 it is an immutable AI parse revision containing subject/topic/task/problem-type hints, parser-level expression text, variables, explicit constraints/assumptions, uncertainty, visual-quality risk, and source evidence references. It is not a canonical safe AST, solution, verified answer, primary skill classification, difficulty label, or user-confirmed correction.

### Problem
Canonical semantic learning problem consumed by solving/learning systems.

### ProblemSession
User workflow around one submitted problem, including parse revisions and solve job.

## Solution

### Solution
Reference solution associated with a Problem.

### SolutionStep
Structured visible step.

### SolverRun
One model/strategy attempt to solve the problem.

### Arbitration
Process comparing independent solver outputs.

## Verification

### VerificationRun
Execution of one verification policy/version.

### VerificationSignal
Individual evidence such as symbolic equivalence or numeric back-check.

### VerificationStatus
VERIFIED, PARTIALLY_VERIFIED, UNVERIFIED.

### Verified
A policy-derived property. Never a raw model claim.

## Learning

### Attempt
Student's submitted answer or solution work.

### AttemptStep
Structured segment of the student's work.

### Mistake
Structured diagnosed error tied to an attempt/step/skill.

### Mastery
Estimated competence for one canonical skill.

### MasteryConfidence
Evidence strength behind mastery estimate.

### KnowledgeGraph
User-specific representation of skill relationships and mastery state.

### StudySession
One bounded learning session.

### StudyPlan
Structured set of future learning actions.

### PracticeItem
Question selected/generated specifically for learning.

## Tutor

### Hint
Progressively stronger guidance.

### TutorTurn
One learner/tutor exchange.

### ExplanationDepth
Quick, Standard, Deep, Beginner.

## Exam

### ExamDefinition
Canonical exam/curriculum definition.

### UserExam
User's target exam instance with date/goal.

### Readiness
Estimate of current preparation relative to exam requirements.

### MockExam
Assessment approximating exam distribution and constraints.

## AI

### AI Provider
External vendor.

### Model Route
Selected provider/model/capability and policy for one operation.

### Prompt Version
Immutable prompt/template identifier.

### Structured Output
Schema-conforming model output that remains untrusted until semantic validation.

### Golden Dataset
Curated evaluation set for regression testing AI changes.

## Infrastructure

### Source of Truth
Canonical authoritative data store; PostgreSQL for application state.

### Cache
Disposable derived data.

### Object Storage
Binary asset storage.

### Durable Job
Persisted async work that survives restart.

## Forbidden ambiguous terms

Avoid:
- "question" when ProblemAsset vs Problem matters,
- "answer" when Solution vs Attempt matters,
- "confidence" without qualification,
- "AI verified",
- "premium user" without tier/status,
- "score" without mastery/readiness/exam context.

## Phase 1 Term Authority Matrix

This matrix is the implementation-ready companion to the glossary. "Durable" means the concept is persisted in canonical product storage or has a durable record. "Authoritative" identifies where product truth is established.

| Term | Owner | Lifecycle | Durable | User-visible | Authoritative source | Forbidden interpretation |
|---|---|---|---|---|---|---|
| User | identity | registered -> active/disabled/deleted | Yes | Indirectly | Backend/PostgreSQL | Not the same as LearningProfile or device install. |
| Identity | identity | bound -> refreshed/revoked/deleted | Yes | No | Backend plus external identity provider validation | Not a learner preference record. |
| LearningProfile | profile | created -> edited -> deleted/anonymized | Yes | Yes | Backend/PostgreSQL | Does not directly edit mastery. |
| Subject | curriculum | versioned active/deprecated | Yes | Yes | Curriculum module | Not free-form AI text. |
| Topic | curriculum | versioned active/deprecated | Yes | Yes | Curriculum module | Not a localized display label. |
| Skill | curriculum | versioned active/deprecated/split/merged | Yes | Yes | Curriculum module | Not a model-generated tag or broad topic. |
| Curriculum | curriculum | drafted -> active -> superseded | Yes | Sometimes | Curriculum module | Not a user's personal plan. |
| ProblemAsset | problem | registered -> uploaded -> processed -> retained/deleted | Yes | Sometimes | Backend metadata plus object storage | Not the canonical Problem. |
| ProblemParse | problem | AI proposed -> accepted/review-required/unsupported -> later corrected/selected in Sprint 4.8 | Yes | Yes in review UX | Problem module | Not trusted merely because schema-valid; not a safe verifier representation or user-confirmed parse in Sprint 4.5. |
| Problem | problem | created from selected parse -> solved/reused/deleted per policy | Yes | Yes | Problem module/PostgreSQL | Not raw OCR, image, or prompt text. |
| Solution | solving | generated -> referenced -> superseded by new solution version | Yes | Yes | Solving module | Not Verification or student Attempt. |
| SolutionStep | solving | ordered immutable part of a Solution | Yes | Yes | Solving module | Not private model chain-of-thought. |
| SolverRun | solving/ai | requested -> completed/failed | Yes | No except safe provenance | AI ledger plus solving module | Not proof of correctness. |
| VerificationRun | verification | started -> completed/requires-review/failed | Yes | Summary visible | Verification module | Not produced by an LLM/provider. |
| VerificationSignal | verification | emitted inside VerificationRun | Yes | Safe subset visible | Verification module/math verifier | Not equivalent to final VERIFIED status. |
| Attempt | attempt | submitted -> evaluated/disputed/reviewed | Yes | Yes | Attempt module/PostgreSQL | Not replaceable by reference Solution. |
| AttemptStep | attempt | parsed/aligned -> evaluated | Yes | Yes | Attempt module | Not SolutionStep. |
| Mistake | mistake | detected -> confirmed/disputed/reviewed/resolved | Yes | Yes | Mistake module | Not ground truth without evidence and policy. |
| Mastery | mastery | initialized -> updated by approved evidence -> versioned history | Yes | Yes | Mastery module | Not editable by iOS, tutor, or raw AI output. |
| MasteryHistory | mastery | append-only update record | Yes | Sometimes | Mastery module | Not a mutable cache snapshot. |
| StudyPlan | studyplan | generated -> active/completed/rebalanced/superseded | Yes | Yes | Studyplan module | Not opaque model prose. |
| StudyPlanItem | studyplan | scheduled -> completed/skipped/rescheduled | Yes | Yes | Studyplan module | Not an ad-hoc notification. |
| StudySession | studyplan | started -> completed/cancelled | Yes | Yes | Studyplan module | Not generic app session analytics. |
| Exam | exam | defined/selected -> active/completed/archived | Yes | Yes | Exam module | Not a generic course label. |
| Entitlement | billing | none/active/grace/billing-retry/expired/revoked | Yes | Yes | Billing module after server verification | Not granted by local StoreKit success alone. |
| Subscription | billing | offered -> purchased/renewed/cancelled/expired | Yes | Yes | App Store plus backend verification | Not equal to entitlement status until verified. |
| AIUsage | ai | recorded per material AI operation | Yes | No | AI module/usage ledger | Not learner-domain truth or training eligibility. |

## Phase 1 Semantic Invariants

- AI output may propose `ProblemParse`, `Solution`, `Mistake`, tutor text, or recommendations, but each remains untrusted until schema validation, semantic validation, and the owning policy accept it.
- Only VerificationPolicy may assign `VERIFIED`; solver agreement, provider confidence, or UI copy cannot assign that state.
- Attempt, Solution, and Verification are separate concepts with separate owners and lifecycles.
- Mastery and entitlement are backend-authoritative and cannot be mutated by iOS caches or model text.
- `ProblemAsset` and `ProblemParse` are evidence/provenance for a `Problem`; neither silently becomes the canonical problem without selection and validation.
- Provider/model identifiers are route provenance and configuration, not core learner semantics.
- Production student data and AIUsage records are not training eligible by default.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Added AI-economics and model-evolution vocabulary

**Foundation Model** — external general-purpose multimodal/reasoning model used as replaceable infrastructure.

**Proprietary Model** — a task-specific model trained/calibrated and operated under the platform's model governance; never synonymous with product truth.

**Route Plan** — versioned decision specifying capability adapter/model, prompt/schema, budget, fallback and escalation policy.

**Secondary Solver Escalation** — conditional invocation of an independent solver based on risk/verification policy.

**Training Eligibility** — explicit governed state defining whether a data item may enter a model-training dataset for a named purpose.

**Dataset Lineage** — reproducible mapping from model dataset items back through transformations to governed sources.

**AI COGS** — variable inference/serving cost attributable to product usage.

**Cost per Verified Solution** — primary engineering/economic measure combining inference spend with a successfully delivered verified outcome.

**Model Release** — versioned external route configuration or proprietary model artifact approved through evaluation and rollout gates.
<!-- HYBRID_AI_STRATEGY_V3:END -->
