# Mastery and Knowledge Graph

## Mission

Estimate what a learner can reliably do, skill by skill. Mastery is not simply percent correct.

## Canonical graph

Subject → Topic → Skill plus prerequisite edges.

Example:
Calculus → Derivatives → Chain Rule.

## Mastery state

For each User × Skill:
- score [0,1],
- confidence [0,1],
- evidence count,
- last practiced,
- algorithm version.

Score answers: how likely can the learner perform this skill?
Confidence answers: how much evidence supports the estimate?

## Evidence

Possible weighted signals:
- independent attempt success,
- difficulty,
- hint usage,
- response time,
- repeated mistakes,
- mock exam performance,
- recency,
- spaced-repetition recall.

A problem where the user merely viewed the answer should contribute little or no positive mastery.

## V1 algorithm

Start interpretable and versioned:
- baseline,
- success/failure delta,
- difficulty multiplier,
- hint penalty,
- recency/forgetting adjustment,
- confidence accumulation.

Do not start with opaque ML before enough clean evidence exists.

## History

Persist material updates so product can explain progress and debug algorithm changes.

## Prerequisites

Weak advanced skill plus weak prerequisite may cause planner to prioritize prerequisite. Failing an advanced skill alone does not automatically decrease unrelated prerequisite scores.

## Cold start

Use low-confidence baseline, optional self-assessment and a diagnostic. Self-report is weak evidence.

## Metrics

- future-answer calibration,
- stability,
- confidence calibration,
- mastery gain prediction,
- user trust.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Deterministic-first mastery strategy

Production V1 should use an explainable deterministic mastery policy before introducing a learned mastery predictor. Longitudinal evidence collected under stable semantics becomes the baseline dataset for later modeling.

A future predictor may recommend/update evidence weights only under explicit validation; it cannot erase historical attempts or make opaque irreversible mastery changes.
<!-- HYBRID_AI_STRATEGY_V3:END -->
