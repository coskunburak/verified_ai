# Sprint 6.7 — product implementation map

Current source: 2026-11-03, baseline `main` at `4acc57ea8ee9b22536a8caa3f4f98ccc945a7855`.
All changes remain uncommitted. Source integration is authored; execution status is **NOT_EXECUTED / PENDING_OWNER**.
Code completeness here means the bounded execution paths below exist, not that production is approved.

## Ownership and execution paths

| Workstream | Production source path | Supported behavior and boundary |
|---|---|---|
| Canonical/domain | `AttemptMathParserV2`, `CanonicalProblemBuilder`, `AttemptSourceDocument`, `canonical_v2.py`, `attempt_verification_v2.py` | Typed real sets, natural-domain-before-cancellation, exact equation sets and expression identity; explicit source domain or learner-confirmed REAL task. Unresolved domain never silently becomes REAL. |
| Photo/OCR | `NativeAttemptExtraction`, `AttemptStepReviewView`, `AttemptExtractionReader/Controller` | Existing camera/photo/crop/rotation and quality checks; native Vision OCR, image comparison, per-line correction/confirmation. Optional owned existing recognition gateway; no direct provider call. |
| 17 diagnoses | `attempt_diagnosis.py`, `attempt-taxonomy-v2.json` | All 17 named codes have bounded structural predicates following independent disproof. Generic `INVALID_TRANSFORMATION` otherwise; legacy `ALG_TRANSFORMATION_INVALID` remains v1. |
| Semantic diff | `attempt_diff.py`, `FirstWrongStepDiffView`, `AttemptMathView` | Evidence-linked AST path, real UTF-16 ranges when present, explicit step fallback when a precise correspondence cannot be established. Native fractions/roots/sets. |
| Twin drills | `attempt_learning.py`, `AttemptLearningService/Repository`, `CounterfactualTwinSheetView` | 17 deterministic template families, five parameter variants, four independently checked/distinct options, stable question, private answer, first-answer idempotency, durable history, optional 60-second wall-clock display. |
| Alternative methods | `attempt_learning.py`, `AlternativeMethodComparisonView` in `CounterfactualTwinSheetView.swift` | Exact factoring, completing square and quadratic formula for eligible real quadratics; domain-preserving symbolic factoring for expressions. Every published transition verified; compare with learner work. |
| Mastery/Passport | `LearnerMasteryPolicy`, `AttemptLearningRepository`, `AttemptLearningPanel` | Unique evidence events, BKT-lite versioned updates with decay and bounded half-life, assistance-aware twin result, timestamp/confidence/count and private history. |
| Persistence | V030, V031, V032 | Forward active Flyway chain, immutable attempt/step/artifact evidence, owned cascades, training exclusions, canonical v3 constraint. |
| Privacy/security | `StudentAttemptPrivacyLifecycleContributor`, repositories, shared `ProcessExecutor`, existing auth/network clients | Ownership/revision rechecks, account lock order, no locks over verification HTTP, bounded input/output/CPU/time, export/deletion, protected drafts and pending submissions, async account fences. |

Exact created/modified source inventory is in `SPRINT_6.7_FILE_MANIFEST.md`; test and release states are in `SPRINT_6.7_EXECUTION_REPORT.md`.

## Canonical compatibility and mathematical scope

`canonical-problem-v1` remains legacy algebra; existing `canonical-problem-v2` remains the released calculus envelope.
Neither is renamed to pretend it supplies ADR-009 set algebra. New reviewed set/function sources use
`canonical-problem-v3` and `real-set-source-v1`. Current-source lookup selects the latest revision; prior attempt
lineage and historical verifier results remain immutable. New submissions use `manual-canonical-v2` when eligible.
An explicit learner confirmation establishes the reviewed REAL task scope and is retained in request identity;
it is not a claim that OCR or an UNKNOWN source independently proved the domain.

Typed grammar: explicit multiplication, conventional unary/power precedence, one variable, rational numbers,
pi, finite sets, EmptySet/Reals, Interval/Union/Intersection, bounded Piecewise and relations including membership.
Natural-domain guard handles denominator zeros, nonpositive integer powers, radicals, logarithms and tangent
poles before cancellation. Equations compare complete concrete real solution sets; inequalities compare sets.
Expressions require equal natural domains and exact symbolic identity; numeric agreement cannot prove equality.
A disproof probe must be exact, defined and inside the original domain. No witness is fabricated when symbolic
set difference is proven but the bounded witness search finds no publishable point.

Supported calculus is bounded differentiation, finite two-sided limits, and proper polynomial definite integrals.
Trigonometric equation sets require resolvable finite scope; implicit/infinite ImageSet solutions stop with uncertainty.
Word problems support the explicit `total(unit_price,item_count)` model only, not arbitrary prose understanding.
Multivariate reasoning, general transcendental proofs, improper/indefinite integrals, arbitrary word problems,
Vieta/substitution method generation and a measured 30-second exam shortcut are not implemented capabilities.
The first uncertain step stops all later assessment; a non-equivalent step does not prove a completed solution.
Bounds include 12 lines, 256 UTF-16 units/line, one variable, parser depth 24, bounded AST/process limits.

## API and contracts

Public base: `/api/v1/problem-sessions/{problemId}/attempts`.

- POST/GET base and GET `/{attemptId}` preserve durable source/key/text replay and history.
- GET `/context` now returns `attempt-context-v2`; client accepts v1 for historical fixtures.
- POST `/extractions` consumes owned `assetSessionId` from the existing upload/preprocess/recognition flow.
- POST/GET `/{attemptId}/counterfactual-twin`, POST `/{attemptId}/counterfactual-twin/answer`.
- POST `/{attemptId}/alternative-methods`, GET `/drills`, GET `/mistake-passport`.
- Mutating learning operations require `Idempotency-Key`; only first selected option is accepted. Replays do not
  duplicate mastery. Twin/method creation converges on one immutable artifact per attempt/action.
- Internal token-authenticated `/internal/v1/verify/attempt` remains v1; `/attempt-v2` and `/attempt-learning`
  share the isolated worker, correlation checks, bounded HTTP adapter and strict typed response validation.

New `attempt-submission-v2` accepts optional realDomainConfirmed and reviewed capture provenance, never client verdicts.
`student-attempt-response` preserves the student-attempt-v1 envelope and allows versioned analysis v1/v2 plus optional
captureReview. Reviewed OCR still becomes learner-authored MANUAL text; provenance records engine/raw lines and
owned recognition IDs separately. Raw text, normalized syntax and deterministic evidence are distinct.
New contracts include canonical-problem-v3, attempt-verification/analysis-v2, step-diff-v1, attempt-extraction-v1,
counterfactual-twin-v1, attempt-learning/result-v1, mistake-passport-v1 and attempt-taxonomy-v2.
All schema/OpenAPI source is authored; contract execution is pending.

## Persistence, privacy and migration strategy

V030 copies the previously staged attempt lifecycle into the next forward slot; the reserved/staged V025 is
unchanged. V031 adds reviewed-capture metadata, durable twins/methods, unique mistake events and skill mastery.
V032 extends the canonical version constraint without changing released v1/v2 alternatives. V024–V026 are not
inserted behind existing V027–V029. No repair, out-of-order, baseline rewrite or historical checksum change.
Owner acceptance applies the active Flyway chain in disposable Testcontainers; no staged SQL fallback remains.
A shared database where someone manually applied staged V025 requires owner-led reconciliation; V030 intentionally
fails rather than silently adopting unknown tables. Actual shared upgrade/deployment is **NOT_AUTHORIZED**.

All private learning tables enforce training_eligible=false. Evidence UPDATE triggers prohibit rewriting original
attempts/steps, generated artifacts or completed answers; account deletion remains possible through owned cascades.
Privacy export includes reviewed text, analysis, drills/methods and passport/mastery; internal retry keys and verifier
requests are excluded. Existing asset lifecycle owns gateway uploads. Native review thumbnails remain memory-only.
iOS protects pending requests and drafts with complete file protection, backup exclusion and account isolation;
image preview clears after confirmation/cancel/account invalidation. Server history recovers committed drills after
lost acknowledgement/relaunch; unacknowledged attempt POST retains its exact local key.

## Activation, dependency governance and rollback

Default false: ATTEMPT_ANALYSIS_ENABLED, ATTEMPT_VISION_ENABLED, ATTEMPT_PRODUCTION_AUTHORIZED,
MATH_VERIFIER_ATTEMPT_ENABLED and MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED. Production requires separate explicit
owner authorization and the verifier's existing strong internal-token/Linux resource requirements. No flag was enabled.
Gateway extraction also requires an enabled existing managed provider/upload path; no credentials or paid requests
were used. Device OCR supports simple readable images only; real handwriting accuracy is **NOT_EVALUATED**.
Rollback disables new execution while retaining history/privacy; there is no destructive down migration.

SwiftMath is pinned exactly to 1.7.3 in the Xcode project for native two-dimensional math. The owner run resolved the package and built the iOS targets; the generated lockfile is preserved.
Code signing, dependency audit and physical-device rendering remain release steps. MIT code and bundled GUST/OFL font notices
must be retained in distribution; see `quality/53_DEPENDENCY_AND_LIBRARY_GOVERNANCE.md`. The owner-generated Package.resolved pins revision fa8244ed032f4a1ade4cb0571bf87d2f1a9fd2d7; it was not fabricated by the agent. Existing spoken-math paths remain; physical accessibility is not yet accepted.

## Authored validation and evidence policy

Python tests cover all 17 bounded classifiers, false-positive/uncertain cases, domain holes, finite trig poles,
Piecewise/sets, 17×5 twin templates, alternatives and worker/auth failures. Java covers new parser/mastery,
forward migrations, event idempotency/privacy, canonical v3 source and HTTP authority rejection. iOS contract tests
cover safe native math input, hidden open-drill answers and protected draft relaunch. Existing assertions remain.
Tests/builds/lint/schema checks/evaluations/migrations/CI were **NOT_EXECUTED** by the agent.
Only source review and `git diff --check` were performed; these do not establish runtime correctness.

The owner runner recognizes this product revision: changed canonical/worker/migration/shared-renderer dependencies
require new integration evidence, separately justified in selection-reason.json. Subsequent failed product runs select
only failing Python/Java/iOS cases and failed gates. A build/setup failure before any case permits that gate's initial
suite, since no test passed there; missing partial evidence never silently broadens selection. Golden evaluation is
not repeated on a targeted repair after it passed. Linux-only resource evidence remains a separate release gate,
`PLATFORM_SPECIFIC_VALIDATION_PENDING` on macOS. Historical owner PASS is never relabeled current acceptance.
