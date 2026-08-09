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

## Problem invariants

1. ProblemAsset is not the canonical Problem.
2. RecognitionEvidence is not a ProblemParse, canonical Problem, classification, solution, or verification result.
3. Recognition output is untrusted input and must pass recognition schema and recognition-level semantic validation before becoming normalized evidence.
4. Every parse has parser/prompt/schema provenance.
5. User correction creates an explicit parse revision; historical parse is not silently overwritten.
6. Solving uses the selected canonical parse.
7. Unsupported problem types fail explicitly rather than being coerced into an incorrect schema.

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
