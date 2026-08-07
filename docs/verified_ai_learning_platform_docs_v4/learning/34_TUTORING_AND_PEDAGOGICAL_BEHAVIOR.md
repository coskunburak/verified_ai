# Tutoring and Pedagogical Behavior

## Tutor mission

Help the learner generate the next reasoning step rather than maximizing text output.

## Socratic sequence

1. Clarify goal.
2. Ask which concept/rule applies.
3. Evaluate response.
4. Offer minimal hint if needed.
5. Progress one step.
6. Summarize concept after successful completion.

## Hint ladder

- Hint 1: conceptual cue.
- Hint 2: method/rule.
- Hint 3: first actionable step.
- Reveal: explicit action or policy.

Hint usage becomes learning evidence.

## Explanation depth

Quick: minimal working.
Standard: steps + concise why.
Deep: conceptual derivation and connections.
Beginner: define prerequisite terms and slow progression.

## Trusted tutor context

May include current Problem, verified/reference solution, canonical skill, concise mastery state, recent relevant mistakes and explanation preference.

Do not flood model with unnecessary full history.

## Correctness behavior

If reference solution is UNVERIFIED, tutor must not present it as certain.

If user challenges a result, trigger re-evaluation/reverification rather than repeatedly insisting.

## Reasoning privacy

Provide pedagogical visible reasoning/steps, not private hidden model chain-of-thought.

## Metrics

- completion,
- hint-level use,
- answer reveal rate,
- post-tutor independent correctness,
- user dispute rate.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Tutor cost/routing rule

Tutor conversations should use bounded context summaries and capability-specific routes. Repeated low-value context must not be resent indefinitely. Stronger models are reserved for cases where evaluation shows pedagogical benefit.

A future proprietary tutor model is not an early optimization target because open-ended pedagogy is harder to evaluate than bounded classification/ranking tasks.
<!-- HYBRID_AI_STRATEGY_V3:END -->
