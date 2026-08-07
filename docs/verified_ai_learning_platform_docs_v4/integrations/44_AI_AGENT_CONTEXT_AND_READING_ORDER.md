# AI Agent Context and Reading Order

## Project summary

We are building an iOS-first production AI mathematics learning platform with Swift/SwiftUI, Java/Spring Boot, PostgreSQL, a small internal Python/SymPy verifier and provider-neutral external AI models.

The differentiator is not "AI can solve math." It is:
- structured problem parsing,
- multi-solver orchestration,
- deterministic verification,
- visible uncertainty,
- mistake intelligence,
- per-skill mastery,
- adaptive study plans,
- exam readiness.

## Source-of-truth order

1. Domain invariants.
2. Accepted ADRs.
3. Domain/architecture.
4. API/data contracts.
5. Product docs.
6. Roadmap.

## Codex reading recipes

### Backend feature
Read 00, 06, 07, 08, 10, 19, 20, 22, 40, 41 plus feature-specific doc.

### iOS screen
Read 00, 03, 04, 10, 14, 15, 16, 17 plus relevant domain doc.

### AI solver/verifier
Read 10, 25, 26, 27, 28, 29, 41.

### Mastery/planner
Read 06, 07, 10, 30, 31, 32, 40.

### Billing
Read 05, 10, 18, 20, 21, 35.

## Non-negotiable context

- PostgreSQL source of truth.
- No Firestore primary DB.
- Modular monolith, not microservice sprawl.
- Mobile never calls AI provider/verifier directly.
- AI response is untrusted.
- VerificationPolicy owns VERIFIED.
- Student attempt is separate from Solution.
- Mastery is server-authoritative.
- Entitlement is server-authoritative.
- Prompt/model changes require evaluation.

## Expected coding-agent behavior

1. Identify owning context.
2. Locate invariants.
3. State files/modules to modify.
4. Add tests.
5. Add migration if persistence changes.
6. Update docs if semantics change.
7. Avoid unrelated refactors.

Conversation memory is never the canonical architecture source when repository documentation exists.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Additional mandatory AI-strategy reading

For any task involving providers, model routing, inference cost, datasets, fine-tuning, self-hosting or proprietary ML, read in order:

1. `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md`
2. `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
3. `ai/58_PROPRIETARY_DATASET_GOVERNANCE_AND_TRAINING_ELIGIBILITY.md`
4. `quality/64_AI_MODEL_REPLACEMENT_DECISION_GATES.md`
5. ADR-005/006/007.
<!-- HYBRID_AI_STRATEGY_V3:END -->

## V4 hierarchy context

For any code-generation task that creates files, include `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md` in the context bundle. If the task spans documentation discovery, also include `quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md`. For uncertain ownership, add `quality/66_FILE_PLACEMENT_OWNERSHIP_AND_DEPENDENCY_MATRIX.md`.
