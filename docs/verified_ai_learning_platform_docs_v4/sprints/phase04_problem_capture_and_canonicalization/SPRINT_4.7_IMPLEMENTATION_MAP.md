# Sprint 4.7 Implementation Map

## Status
COMPLETE — BACKEND AND IOS PRODUCTION FLOW INTEGRATED

## Core invariant

ProblemParse != CanonicalProblem != ProblemClassification

Classification consumes only the selected current CanonicalProblem.

## Runtime flow

CanonicalProblem
→ eligibility
→ bounded candidate projection
→ durable QUEUED job
→ RUNNING
→ provider execution outside database transaction
→ strict output normalization
→ domain/taxonomy validation
→ candidate-set validation
→ backend confidence policy
→ immutable classification revision
→ SUCCEEDED

## Semantic statuses

- CLASSIFIED
- REVIEW_REQUIRED
- UNKNOWN
- UNSUPPORTED

FAILED is not a semantic classification status.

## Persistence

- problem_classification_jobs
- problem_classifications
- problem_classification_secondary_skills
- canonical-scoped immutable revision
- SHA-256 logical request fingerprint
- relational secondary skills with ordinal 0..4

## AI trust boundary

Provider output may contain only:

- schemaVersion
- ontologyVersion
- status
- primarySkillId
- secondarySkillIds
- difficulty
- reviewReason

Provider confidence and reasoning are forbidden.

## Confidence

Policy: classification-confidence-v1

- CLASSIFIED → MEDIUM
- CLASSIFIED with upstream risk → LOW
- REVIEW_REQUIRED → LOW
- UNKNOWN / UNSUPPORTED → UNKNOWN
- HIGH is never emitted
- calibration = UNCALIBRATED

## Production-reachable acceptance coverage

- arithmetic evaluate
- algebra simplify
- linear equation
- quadratic equation
- inequality

Calculus skills exist in curriculum-v1-seed but calculus is not claimed
as Sprint 4.7 CanonicalProblem E2E coverage.

## Privacy

Classification lifecycle participates in account export/deletion.

Raw classifier provider output is excluded from account export.

## iOS production flow

Sprint 4.7 is exposed in iOS through the existing `ProblemCapture`
workflow boundary rather than a separate client-side classifier.

- `ProblemAssetUploadServicing` includes canonicalization and
  classification commands/read endpoints:
  `POST /api/v1/problem-sessions/{sessionId}/canonicalize`,
  `GET /api/v1/problem-sessions/{sessionId}/canonical-problem`,
  `POST /api/v1/problem-sessions/{sessionId}/classification`, and
  `GET /api/v1/problem-sessions/{sessionId}/classification`.
- `ProblemAssetUploadPhase` keeps parse, canonical, and classification
  references as separate typed states.
- Capture UX now progresses from successful parse to verification
  preparation, canonical summary, durable classification start/polling,
  classified, review-required, unsupported, and retryable failure states.
- Parse review close refreshes the backend-selected current parse before
  the user can prepare verification from a corrected revision.
- Problem history/recovery invokes backend-derived `CANONICALIZE`,
  `START_CLASSIFICATION`, and `RETRY_CLASSIFICATION` actions through the
  same workflow API. It does not synthesize taxonomy decisions locally.
- iOS tests cover canonicalization transition, classification success,
  classification retry, review-required classification, and history
  recovery action dispatch.

## Explicitly out of scope

- user correction / revision UI (Sprint 4.8)
- solving
- verification
- mastery mutation
- curriculum mutation
- taxonomy creation
- calculus CanonicalProblem E2E
