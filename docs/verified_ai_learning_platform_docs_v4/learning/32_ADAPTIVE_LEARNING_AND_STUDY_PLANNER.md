# Adaptive Learning and Study Planner

## Product question

Given limited time, what should this learner do next?

## Inputs

- mastery and confidence,
- prerequisite graph,
- recent mistakes,
- spaced repetition due items,
- exam weights and target date,
- daily available minutes,
- recent workload,
- user preferences.

## Candidate activities

- targeted skill practice,
- mistake review,
- prerequisite remediation,
- micro-lesson,
- mixed review,
- mock exam,
- no extra work when daily cap is reached.

## Priority factors

- exam importance,
- weakness severity,
- mastery confidence,
- forgetting risk,
- prerequisite centrality,
- repeated-error frequency,
- expected learning gain,
- time cost.

Start rule-based and explainable.

## Structured daily plan

Example:
- Chain Rule — 10 min — WEAK_HIGH_EXAM_WEIGHT
- Sign Error Review — 5 min — REPEATED_MISTAKE
- Mixed Calculus — 10 min — SPACED_REVIEW

## Replanning triggers

- missed study day,
- completed session,
- major mastery update,
- new exam date,
- mock exam result,
- user time-budget change.

Completed history is never erased.

## Question selection

Prefer curated/validated bank when possible. AI-generated practice requires validation and should have lower assessment weight until confidence is established.

Difficulty should keep the student challenged but not overwhelmed.

## User control

User may skip, reschedule or manually focus a topic. Recommendations are transparent rather than coercive.

## Metrics

- plan completion,
- mastery gain per minute,
- retention,
- skip patterns,
- readiness improvement.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Recommendation evolution strategy

Start with explainable heuristics/rules using mastery, recency, prerequisites, mistakes and exam priority. A learned ranking model is considered later only if offline/online evaluation demonstrates improved learning outcomes over the deterministic baseline.

This avoids training a recommendation model before sufficient interaction data exists.
<!-- HYBRID_AI_STRATEGY_V3:END -->
