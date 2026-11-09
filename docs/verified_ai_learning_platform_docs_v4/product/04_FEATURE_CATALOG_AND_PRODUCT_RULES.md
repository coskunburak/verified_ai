# Feature Catalog and Product Rules

## A. Capture and Input

### A1 Camera capture
- auto-detect question region,
- manual crop override,
- perspective correction,
- blur/glare warning,
- multi-question page selection.

### A2 Gallery and document import
- image import in V1,
- PDF page selection later,
- asset metadata and checksum.

### A3 Typed input
- plain text,
- math expression input,
- future math keyboard.

### A4 Own-solution input
Problem and student attempt should be separable whenever possible.

## B. Problem Intelligence

### B1 Parser
Produces subject, topic, skill, problem type, variables, task, normalized expression and parse confidence.

### B2 Editable parse
User can correct OCR/semantic interpretation before solving.

### B3 Canonical taxonomy
AI output must map to canonical skill IDs rather than invent arbitrary labels.

## C. Solving

### C1 Primary solver
Quality/cost-optimized route.

### C2 Secondary independent solver
Receives normalized problem but not primary hidden reasoning.

### C3 Arbitration
Compares final answer and structured solution artifacts.

### C4 Alternative method
Optional and pedagogically justified.

## D. Verification

### D1 Verification planner
Selects symbolic, numeric, matrix, unit or domain-specific checks.

### D2 Status
- VERIFIED
- PARTIALLY_VERIFIED
- UNVERIFIED

### D3 Evidence details
Expose user-safe evidence only, not private model chain-of-thought.

## E. Explanation

- step-by-step solution,
- "Why?" per step,
- explanation depth: Quick / Standard / Deep / Beginner,
- localized terminology while preserving mathematical notation.

## F. Tutoring

- Socratic mode,
- three-level hint ladder,
- answer reveal as explicit action,
- attempt evaluation.

## G. Mistake Intelligence

Initial categories:
- CONCEPT_ERROR
- FORMULA_ERROR
- ALGEBRA_ERROR
- SIGN_ERROR
- CALCULATION_ERROR
- INTERPRETATION_ERROR
- UNIT_ERROR
- NOTATION_ERROR
- INCOMPLETE_SOLUTION
- OTHER_REVIEWED

## H. Mastery

Per User × Skill:
- mastery score,
- mastery confidence,
- evidence count,
- last practiced,
- algorithm version.

## I. Adaptive Learning

Inputs:
- mastery,
- mistakes,
- spaced repetition,
- exam weighting,
- time budget.

Output:
- structured ranked practice plan.

## J. Exam Mode

- exam profile,
- syllabus mapping,
- readiness,
- mock tests,
- timed sessions,
- post-exam analysis.

## K. History/Library

- solved problems,
- verification filters,
- favorites,
- mistake book,
- saved learning content.

## L. Subscription

Free, Pro and Pro+ are semantic tiers. Exact quotas/prices are configuration, not hard-coded UI logic.

## Cross-feature rules

1. Never claim verified without VerificationPolicy.
2. Never overwrite the student's original attempt.
3. Always distinguish AI-generated explanation from deterministic evidence.
4. Preserve raw asset separately from normalized Problem.
5. User can correct the parse.
6. Premium denial identifies required capability/tier.
7. Every AI failure should expose a useful recovery option when possible.
8. Model/provider changes must not erase or invalidate learning history.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI execution rules added to the feature catalog

- Model/provider selection is invisible implementation detail unless disclosure is required for product trust/compliance.
- Secondary solving is conditional, not a guaranteed call per problem.
- “Verified” is a product state produced only by verification policy.
- Free/paid quotas may limit expensive capabilities but may not convert uncertainty into false certainty.
- Future proprietary models must preserve identical user-facing contracts or introduce explicitly versioned behavior.
<!-- HYBRID_AI_STRATEGY_V3:END -->

<!-- LEARNING_OS_FEATURE_RULES_V7:START -->
## V7 Learning OS product rules and feature catalog extension (2026-11-07)

The feature catalog is extended into the **Verified AI Learning OS** (`product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md`):

1. **Evidence-Grounded Mistake Passport (`F05` / `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`):** Mistake cards (`OPEN_ACTIVE`, `REMEDIATION_IN_PROGRESS`, `STABILIZING`, `MASTERED_CLOSED`, `RELAPSED`) are populated strictly from verified attempt diagnoses (`V030`–`V036`, `PLANNED V037`).
2. **Progressive 3-Variant Twin Practice (`F04` / `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`):** Remediation uses Variant A (Isomorphic Trap Twin), Variant B (Structural Twin), and Variant C (Exam Synthesis Twin) with a mandatory 60-Second Independent Verification Protocol.
3. **Personal AI Mathematics Coach (`learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md`):** Composes a deterministic 15-minute `1-2-1` daily study session (`1` Mistake Closure + `2` Spaced Retrieval Drills + `1` Exam Synthesis Challenge) with guilt-free missed-day rebalancing.
4. **Anti-Illusion Mastery Rule (`learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md`):** Passive solution viewing (`SOLUTION_REVEALED_ASSISTED`) never increases mastery (`\Delta P(L) = 0.0`). Only `INDEPENDENT` and bounded `HINT_ASSISTED` solves update BKT-Lite mastery and half-life decay.
5. **Anti-Bloat Guardrails:** Social feeds, public leaderboards, unverified AI essay chat, and cosmetic avatar stores are permanently out of scope.
<!-- LEARNING_OS_FEATURE_RULES_V7:END -->
