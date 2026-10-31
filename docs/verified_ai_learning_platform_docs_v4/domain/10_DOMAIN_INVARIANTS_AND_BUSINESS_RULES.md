# Domain Invariants and Business Rules

This is a highest-precedence document.

## Verification invariants

1. Only VerificationPolicy may assign overall VERIFIED.
2. An LLM self-confidence score cannot be sole verification evidence.
3. Solver agreement alone is not deterministic verification.
4. If deterministic evidence contradicts the solution, status cannot be VERIFIED.
5. PARTIALLY_VERIFIED must identify what was and was not checked.
6. Historical verification runs are immutable.
7. User-facing language distinguishes AI generation from deterministic verification.
8. A verification plan selects required methods; selection is neither execution nor evidence.
9. Planning requires current owned source/solution lineage. Historical, ambiguous or unsupported inputs cannot silently become current executable plans.
10. Mathematical applicability is separate from installed, approved execution capability; missing domain or completeness checks cannot be discarded to claim verification.

## Problem invariants

1. ProblemAsset is not the canonical Problem.
2. RecognitionEvidence is not a ProblemParse, canonical Problem, classification, solution, or verification result.
3. Recognition output is untrusted input and must pass recognition schema and recognition-level semantic validation before becoming normalized evidence.
4. ProblemParse is not a canonical Problem, safe verifier representation, primary skill classification, solution, or verification result.
5. Every parse has exact RecognitionEvidence revision, parser/provider/model/prompt/schema/route provenance, and raw-vs-normalized output separation.
6. A schema-valid parser response is not sufficient; parser semantic validation, supported-scope validation, source-reference validation, and ownership checks must pass before a durable accepted revision exists.
7. User correction creates an explicit parse revision; historical parse is not silently overwritten.
8. Canonicalization and solving use the ProblemSession selected parse, not raw RecognitionEvidence, unreviewed raw provider output, or an implicit latest parse row.
9. Unsupported problem types fail explicitly rather than being coerced into an incorrect schema.
10. User-corrected parses are not verified mathematical truth, training data, or evaluation gold data by default.

## Canonical solution invariants (Sprint 5.6)

- Canonical syntax is not proof; even an incorrect accepted candidate may have a faithful projection.
- Exactly one immutable accepted primary or secondary candidate originates each versioned solution.
- Roles, source ownership, selected parse identity and canonical revision must match; agreement never fuses solutions.
- Steps retain exact typed claims and original IDs, with contiguous order and only earlier dependencies.
- New candidate or normalizer versions preserve prior history. Stale source projections never appear in current reads.
- Canonicalization adds no inference, billing debit, learner evidence or training eligibility.

## Attempt invariants

1. Student attempt is never replaced by reference Solution.
2. Submitted attempt content is immutable except explicit metadata correction/revision semantics.
3. Mistake diagnosis references concrete evidence.
4. Mastery cannot be updated from generated content pretending to be student work.

## Mastery invariants

1. Mastery belongs to User × Skill.
2. Score range is [0,1].
3. Every update stores algorithm version.
4. Only authorized evidence types modify mastery.
5. One problem should not cause unbounded mastery jumps without an explicit tested rule.
6. Mastery and mastery confidence are distinct.

## Study plan invariants

1. Plan only recommends supported/eligible skills.
2. Plan respects time constraints.
3. Planner cannot mark skills mastered.
4. Planner stores structured items, not opaque model prose.
5. Replanning never erases completed study history.

## Billing invariants

1. Client cannot grant entitlement.
2. Backend verifies external purchase state.
3. Premium server operations enforce server-side entitlement/quota.
4. Usage counters are race-safe and idempotent.
5. Expired premium users retain owned history unless deletion policy says otherwise.

## Security invariants

1. No AI provider secret in mobile app.
2. Math verifier is internal-only.
3. PostgreSQL/Redis are not public internet services.
4. Authorization is checked on each resource, not inferred from UUID obscurity.
5. Raw problem content is not sent to analytics by default.

## AI invariants

1. AI output is untrusted.
2. Structured output must pass schema validation and semantic validation.
3. Prompt/model version is stored for material AI operations.
4. High-impact AI changes require golden-dataset evaluation.
5. Tool access is allowlisted.
6. User problem content cannot override system policy.
7. Provider fallback preserves the internal semantic contract.

## Product trust invariants

1. Never display fake precision in confidence.
2. If result cannot be verified, say so.
3. Subscription terms are transparent.
4. User can report incorrect parse/solution.
5. User can inspect high-level verification evidence.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Additional non-negotiable AI/model invariants

1. Production student content is not training data by default.
2. Training/fine-tuning requires explicit eligible, lineage-tracked data.
3. Protected golden/holdout evaluation examples must not enter training data.
4. Secondary solving is invoked by policy; it is not a mandatory cost for every problem.
5. A cost budget may limit access or trigger a recoverable state, but may never create false `VERIFIED` status.
6. Proprietary models must implement the same domain capability contract and cannot become a hidden source of truth.
7. No proprietary model is promoted without offline evaluation, cost evidence, rollback capability, and online canary/shadow evidence where applicable.
8. External provider/model details are provenance/configuration, never stored as core learner semantics.
9. Model-training ambitions may not override account deletion, retention, privacy, or data-minimization obligations.
10. Foundation-model training from scratch is outside the approved product strategy unless a future explicit ADR replaces ADR-005.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## Method evidence invariants (Sprint 5.8)

- Execution completion, method proof, solution-set coverage and overall verification status are separate facts.
- Timeouts, admission/resource failure, invalid responses and unsupported mathematics cannot become proof or disproof.
- Domain obligations are read from original typed syntax before cancellation; UNKNOWN is never assumed REAL.
- One valid root proves soundness only. Singleton completeness requires independent restricted root-set derivation.
- Publication reauthorizes the active owner and current selected parse/source after all mathematical network work.
- Published runs and their method evidence are immutable, scoped to exact source/solution/version identities and idempotency keys.
- Historical evidence remains explicit history; currentness is checked on reads. Account/source deletion removes evidence through owned cascades.
- No Sprint 5.8 method assigns an aggregate VERIFIED status or changes canonical solution generation state.

## Sprint 5.10 composite invariants

- A composite describes evidence coverage and consistency, never product verification status.
- Only one current owned plan, candidate revision, source scope and execution may compose;
  duplicate methods, forged descriptors, unsupported versions and incompatible assumptions reject.
- Root soundness cannot establish solution-set completeness. Missing required domain checks
  remain missing; empty evidence cannot establish truth.
- An unsatisfiable source restriction set blocks ordinary proof interpretation. Unknown
  feasibility remains unresolved. Original-expression restrictions survive cancellation.
- Numeric agreement, mismatch and operational failure are strictly non-proof. Exact
  contradictions survive numerical agreement; a conflict is not automatic adjudication.
- Composite assessments and numeric diagnostics are ephemeral; immutable historical runs
  retain their original contracts and five-method limit.

## Sprint 5.11 policy invariants

- Only the versioned VerificationPolicy assigns aggregate status; generation state is independent.
- VERIFIED covers the final-answer obligations only. Explanation steps remain explicitly unchecked.
- Partial status requires a meaningful deterministic claim; domain-only checks and solver/numeric agreement are insufficient.
- Exact disproof and unresolved material conflicts prohibit VERIFIED and PARTIALLY_VERIFIED.
- Source, solution, evidence, capability and secondary freshness are rechecked before publication and current display.
- A newer run invalidates the older current projection even without a source change. History remains immutable.
- GET never dispatches inference, starts verification, retries, reserves usage or writes decisions.
- Retrying evaluation with a key reuses one matching decision; changed scope rejects. No automatic paid escalation exists.
- Calculus composition requires its tagged evidence variant and full source interval, approach and restriction matching; generic evidence cannot substitute.
- Calculus VERIFIED retains OPEN_INTERVAL, ANTIDERIVATIVE_REPRESENTATIVE or PUNCTURED_NEIGHBORHOOD scope; it never implies endpoints, an entire antiderivative family, or explanation steps. Production promotion remains disabled.
