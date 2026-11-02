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

## Sprint 6.5 implemented boundary

`socratic-policy-v1` implements goal/concept/step elicitation, one minimal conceptual nudge, reflection,
review and bounded pause. It emits EN/TR templates labelled DETERMINISTIC_POLICY and explicit uncertainty.
It does not read or assess problem mathematics, estimate mastery, reveal answers or run a verifier.
The initial goal question is shown in the existing Tutor screen; subsequent replies are durable turns.
ADVANCE_ONE_STEP is reserved for trusted step evidence and cannot be reached by current application code.
COMPLETED means reflection complete. A disputed result stops and points to the existing source/evidence
screen; the tutor honestly says no recheck was started. Start a new session after review when ready.

The provider capability remains disabled. At the Sprint 6.5 baseline, structured hint ladders/economics
were deferred; the completed bounded Sprint 6.6 implementation is described below. Synthetic structural
regression remains separate from expert-labelled teaching quality, which is NOT_EVALUATED and release-blocking.

## Sprint 6.6 implemented hint boundary

`progressive-hints-v1` supports canonical-v1, one-variable `a*x+b=c` with integer a=2..99,
b=1..999 and c=1..999, no constraints/assumptions/restrictions. UNKNOWN domain is not upgraded to REAL
or verified; the content only proposes inverse operations that remain to be performed by the learner.
Other structures return unavailable. The six EN/TR templates are selected by a typed structural family,
never by student instructions, source prose or unvalidated model output. H3 asks the learner to subtract
the constant on both sides without doing the division or printing a computed intermediate/final answer.

Each durable hint moves the existing Socratic progress to OFFER_MINIMAL_SUPPORT, preserves unproductiveCount,
and consumes one interaction. Hints stop at terminal phases or the reserved final pause interaction.
Review, reflection and ordinary turns continue through the same state machine. Legacy SMALL_HINT intent
retains its existing generic Socratic nudge meaning; the dedicated level-expecting hint operation owns
structured problem-specific assistance and never interprets a text request as reveal authorization.

REVEAL has its own confirmed command and UI. No tutor-authorized verified answer reader exists locally;
it returns TUTOR_REVEAL_UNAVAILABLE without evidence or debit. This limitation is explicit, not a fourth
hint or a simulated success. Both tutoring flags default false; live pedagogy quality remains unevaluated.
