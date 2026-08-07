# Mistake Intelligence

## Mission

Turn a wrong answer into structured, reusable learning evidence.

The learner should know:
- where the first meaningful divergence occurred,
- what kind of error it was,
- which skill is involved,
- how to avoid it,
- what to practice next.

## Taxonomy

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

Stable taxonomy enables longitudinal analytics.

## Pipeline

1. Parse student attempt.
2. Obtain verified/reference solution state.
3. Align steps.
4. Identify first causal divergence.
5. Use deterministic checks where possible.
6. AI proposes category and explanation.
7. Validate taxonomy/evidence.
8. Persist Mistake.
9. Publish MistakeDetected.
10. Mastery/planner consume evidence according to confidence.

## First-divergence principle

Do not report five downstream errors when one sign error caused all of them. Teach the root cause.

## Mistake evidence

Store:
- actual step,
- expected relation/form,
- deterministic comparison result,
- skill,
- classifier confidence,
- review status.

## User-facing format

Bad: "Wrong answer."

Good: "In Step 3 you distributed the negative sign incorrectly. `-(x+2)` becomes `-x-2`."

Then provide a compact corrective exercise.

## Review states

- AUTO_DETECTED
- USER_CONFIRMED
- USER_DISPUTED
- HUMAN_REVIEWED

Disputed automatic mistakes should carry lower/zero mastery evidence until resolved by policy.

## Aggregation

Mistake Book supports grouping by category, skill, recency and exam relevance.

## Metrics

- classification precision,
- dispute rate,
- first-divergence accuracy,
- repeated-error reduction,
- corrective exercise success.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Model-evolution rule for mistake classification

V1 may use external AI plus deterministic policies for mistake classification. The output taxonomy and evidence contract must remain stable so the classifier can later be replaced by a compact proprietary model without changing learner-domain semantics.

User corrections are valuable labels but are not automatic ground truth or training-eligible data.
<!-- HYBRID_AI_STRATEGY_V3:END -->
