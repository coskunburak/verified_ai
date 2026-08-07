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
