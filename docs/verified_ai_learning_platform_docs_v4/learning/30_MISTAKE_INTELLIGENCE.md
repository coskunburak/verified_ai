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

## Sprint 6.7 versioned detection boundary

attempt-taxonomy-v2 contains the 17 listed specific codes (the prose count of 18 was inconsistent). Each has a
bounded structural classifier after deterministic disproof; otherwise INVALID_TRANSFORMATION. Legacy v1 generic
ALG_TRANSFORMATION_INVALID remains readable. Unknown/uncertain work produces no diagnosis/mastery event.
Specific disproof and independently verified twin completion generate unique private evidence; assistance-aware
BKT-lite-v1 updates decay toward 0.15 and expose confidence/count/timestamps. Generic errors cannot infer a skill.
Training eligibility remains false. All new execution/accuracy evidence is PENDING_OWNER, not measured performance.

## Sprint 7.4 accepted-evidence classification

The deterministic production path consumes immutable Sprint 7.3 AttemptEvaluation. It makes no new model
or verifier call. The earlier pipeline's AI proposal is a future governed extension, not an enabled route.
The canonical 17 fine-grained codes remain attempt-taxonomy-v2; mistake-category-v1 separately maps their
pedagogical categories. Historical prose mentioning 18 codes is not a mandate to add a code.
SIGNATURE_SUPPORTED requires exact disproof plus an unambiguous supported structural predicate. Multiple
possible signatures retain only INVALID_TRANSFORMATION with category=null and review REQUIRED. An optional
rational witness is separate from the accepted exact disproof. Only the first causal divergence is recorded.

Evidence bands are STRUCTURAL_AND_DISPROOF, EXACT_DISPROOF and NONE; none is a probability. Authority types
are DETERMINISTIC_EVIDENCE, MODEL_SUGGESTION, USER_CORRECTION and HUMAN_REVIEW, but the v1 publication
contract accepts only deterministic authority. USER_DISPUTED and HUMAN_REVIEWED cannot be forged through
this route; review workflows are Sprint 7.5. Classification never changes mastery or mathematical verdicts.

UNCERTAIN, UNSUPPORTED, SOURCE_REVIEW_REQUIRED, INCOMPLETE_WORK and NO_ERROR_ESTABLISHED carry no code,
category or confirmed causal step. Calculation/unit/notation predicates and OTHER_REVIEWED are unsupported;
missing final work is not a confirmed INCOMPLETE_SOLUTION error. WORD_VARIABLE_MISASSIGNMENT is limited
to omission of an explicit TOTAL operand. It does not infer natural-language interpretation or units.
See mistake-category-v1.json and Sprint 7.4 completion matrix for exact coverage. All new tests are pending owner execution.

### Signature-policy compatibility

New deterministic diagnoses optionally carry classifierPolicyVersion=structural-signatures-v1. Java/Python/Swift
and embedded evaluation schemas accept this additive field and continue reading old diagnoses without it.
Sprint 7.4 requires this marker for a specific structural category. A historical specific code without the
marker retains its accepted disproof but produces generic INVALID_TRANSFORMATION / REQUIRED with no category
or skill. Original evaluation evidence is never rewritten. Unknown policy markers fail closed.
Operational rollout must install compatible API readers before the Python producer, then validate owner
evidence before enabling classification. An older strict API reader fails closed on new producer output;
rolling back the API also requires restoring the compatible producer. Flag-only classifier rollback retains
current readers and producer and is the preferred forward-only recovery. No production rollout was performed.
