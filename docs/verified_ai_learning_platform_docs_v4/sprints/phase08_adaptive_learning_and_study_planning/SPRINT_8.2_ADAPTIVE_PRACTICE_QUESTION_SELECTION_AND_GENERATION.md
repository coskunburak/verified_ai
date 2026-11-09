# Sprint 8.2 — Adaptive Practice Question Selection and Generation

## Sprint mission

Construct practice sets with controlled difficulty, skill coverage, old-mistake similarity, and quality safeguards.

This sprint is a production delivery unit. It is not complete when the happy-path UI or endpoint exists; it is complete when domain semantics, failure behavior, security, telemetry, test coverage, documentation, and operational ownership are coherent.

## Why this sprint exists

The platform intentionally separates product semantics, learner state, AI-generated artifacts, deterministic verification, billing truth, and presentation concerns. This sprint reduces a specific category of future architecture drift and creates a stable contract for the work that follows it.

## Preconditions and dependencies

- Read `00_MASTER_INDEX.md` and the relevant canonical documents before implementation.
- Re-check `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`.
- Re-check accepted ADRs that affect persistence, modularity, AI providers, or verifier boundaries.
- Confirm no prior sprint left a temporary implementation that would silently become a permanent contract.
- If an API/schema/domain contract changes, identify all downstream iOS, backend, test, analytics, and documentation consumers before coding.

## In scope

- The smallest complete production slice necessary to fulfill the sprint mission.
- Explicit happy path, validation path, degraded path, and retry/recovery path.
- Stable names and typed contracts rather than ad-hoc dictionaries or unversioned JSON.
- Automated tests appropriate to the risk.
- Privacy-safe observability for critical operations.
- Documentation changes required to keep source-of-truth files synchronized.

## Explicitly out of scope

- Unrelated refactors justified only as cleanup.
- Premature microservice extraction.
- Hidden fallback behavior that changes business semantics without product visibility.
- New generic `utils`, `helpers`, or catch-all service abstractions.
- Direct client access to AI providers, PostgreSQL, Redis, object storage credentials, or the math verifier.
- Production behavior that exists only in a prompt and has no schema, policy, or evaluation coverage when a durable contract is required.

## Deliverables

1. **Semantic deliverable** — definitions, states, invariants, and ownership are explicit.
2. **Implementation deliverable** — production code follows repository/module conventions.
3. **Contract deliverable** — API, schema, event, persistence, or UI contracts are versioned where applicable.
4. **Quality deliverable** — automated tests cover core behavior and failure modes.
5. **Operational deliverable** — logs/metrics/traces/alerts exist where this behavior can fail materially.
6. **Documentation deliverable** — canonical docs and this sprint record agree with implemented behavior.

## Domain and business-rule checklist

- Identify the aggregate or module that owns each decision introduced in this sprint.
- Define legal state transitions and reject illegal transitions explicitly.
- Preserve historical learner evidence rather than overwriting attempts or verification evidence.
- Do not treat client state as authoritative for identity, entitlement, verification, mastery, billing, or exam scoring.
- If AI participates in a decision, define what remains deterministic/server-authoritative.
- Ensure uncertainty is represented explicitly when correctness cannot be established.

## AI / verification / evaluation work

- AI output is untrusted input and must pass schema validation before entering durable domain state.
- Record provider, model, prompt version, schema version, latency, token usage, and trace/correlation identifiers.
- Keep model/provider selection in routing policy rather than feature business code.
- Add representative examples to the relevant golden dataset before enabling materially new AI behavior.
- Define deterministic validation or independent corroboration whenever the problem class permits it.
- Do not manufacture confidence from an LLM self-reported percentage.
- Add explicit `UNVERIFIED`/review paths rather than forcing a successful-looking result.
- Measure quality, latency, and cost regression against the last approved baseline.

## API and contract considerations

For any new/changed endpoint or asynchronous event:

- Use `/api/v1` versioning conventions.
- Define request validation independently from domain invariants.
- Return stable machine-readable error `code` values.
- Include correlation/trace context.
- Document polling/SSE/job semantics if work is asynchronous.
- Define authorization and entitlement requirements.
- Add contract fixtures consumed by client tests when response shape is user-facing.
- Treat backward compatibility as a release concern; do not silently repurpose existing fields.

## Security and privacy checklist

- Threat-model new inputs, especially images, PDFs, user-authored math, prompts, deep links, webhooks, and admin actions.
- Validate content type, size, ownership, and object references server-side.
- Do not log tokens, full uploaded images, raw payment credentials, or unnecessary PII.
- Apply least privilege to internal service credentials.
- Verify object-level authorization, not merely authenticated-user status.
- Define abuse limits for computationally expensive operations.
- Ensure account deletion and retention policies can reach any new durable data.

## Observability requirements

Where applicable add:

- success/failure counters,
- latency histogram,
- domain outcome metric,
- retry/fallback count,
- privacy-safe structured logs,
- trace spans across API → AI/verifier/storage boundaries,
- cost metrics for AI operations,
- alert thresholds only for conditions with an actionable response.

Every operational record should be diagnosable by a correlation identifier without exposing sensitive content.

## Testing strategy

### Unit tests
- Domain rules and state transitions.
- Parsing/mapping/policy edge cases.
- View-model/use-case behavior where applicable.

### Integration tests
- PostgreSQL constraints and queries.
- External adapter contracts through fakes or controlled sandbox endpoints.
- Internal math-verifier/API contracts when touched.

### Contract tests
- Request/response schema compatibility.
- Stable error-code behavior.
- Serialization compatibility between backend and iOS fixtures.

### UI tests
- Critical happy path.
- At least one recoverable error path for user-visible workflows.
- Accessibility smoke coverage for new major screens.

### AI evaluation tests
- Required whenever prompt/model/parser/solver/classifier/tutor behavior changes materially.

## Acceptance criteria

- [ ] Sprint mission can be demonstrated end-to-end in a production-like local/staging environment.
- [ ] No domain invariant is bypassed by client logic, prompt text, or persistence shortcuts.
- [ ] Failure and retry behavior is intentionally designed and tested.
- [ ] Authorization/entitlement rules are enforced server-side where required.
- [ ] All new durable data has ownership, retention, and deletion semantics.
- [ ] Critical behavior emits privacy-safe observability data.
- [ ] Relevant unit/integration/contract/UI/AI-evaluation tests pass.
- [ ] No P0/P1 defect is knowingly carried out of the sprint scope.
- [ ] Canonical Markdown documentation is updated in the same change.
- [ ] CI is green and architecture/dependency checks show no new violations.

## Definition of Done

A sprint is Done only when implementation, tests, documentation, security review, telemetry, migration/contract review, and a reproducible demonstration are complete. “Implemented behind an undocumented TODO” is not Done. A feature flag may protect incomplete rollout, but it may not be used to hide an incomplete invariant or unsafe production state.

## Required demo

Demonstrate the primary user/system flow plus one meaningful failure or degraded scenario. Show the corresponding server/client state, trace or metric, and the automated test that protects the behavior.

## Risks to evaluate before closing

- Does this create a new source of truth accidentally?
- Does it couple a domain module to a provider or framework?
- Does it make the iOS client authoritative for a server-owned decision?
- Does it create unbounded AI/storage/notification cost?
- Can a retry duplicate a charge, solve, plan item, or user-visible artifact?
- Can incorrect AI output be mistaken for deterministic truth?
- Does newly collected data exceed what is needed for the user value?
- Is there an operational recovery path if the new dependency fails?

## Documentation updates

Review and update the relevant files under:

- `product/`
- `domain/`
- `architecture/`
- `ios/`
- `backend/`
- `data/`
- `ai/`
- `learning/`
- `security/`
- `operations/`
- `quality/`
- `adr/` when a durable decision changes.

## Sprint exit gate

The next sprint may rely on this work only after all acceptance criteria are met or an explicit documented exception names the owner, risk, expiry condition, and remediation sprint.

<!-- PRODUCTION_EXECUTION_V3:START -->
# Production Execution Specification v3 — Hybrid AI / Cost-Aware Revision

## Revision intent

This section upgrades the sprint from a task checklist into a production implementation contract aligned with the accepted API-first, progressively proprietary AI strategy. It is additive to the sprint's original scope; if any older text implies unconditional expensive model use or training-by-default, this revision takes precedence together with ADR-005/006/007.

## Phase-level engineering objective — Adaptive learning and planning

- Start with explainable rules/heuristics and collect evidence before learned recommenders.
- Measure learning outcomes, not engagement-only metrics.
- Create counterfactual/offline evaluation hooks for future recommendation models.

## Mandatory source-of-truth review before coding

At minimum, the implementer/agent must read:

- `00_MASTER_INDEX.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- `quality/41_ENGINEERING_STANDARDS_FOR_CODEX.md`
- `quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`
- `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md`
- `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
- ADR-005, ADR-006 and ADR-007 when AI/model/data economics are touched.

The sprint owner must additionally list the exact domain/API/data documents materially changed by this sprint in the PR description.

## Implementation decomposition requirement

Before writing production code, produce a short sprint-local implementation map containing:

1. owning bounded context/module;
2. domain states/invariants touched;
3. iOS screens/view-models/use cases touched;
4. backend commands/queries/policies/adapters touched;
5. database migrations/indexes touched;
6. API/event/schema changes;
7. AI capability/route implications;
8. privacy/security implications;
9. telemetry/SLO/cost implications;
10. test/evaluation evidence required;
11. rollout/feature-flag strategy;
12. rollback/recovery plan.

## Detailed workstreams

### Learning Intelligence
- Define the deterministic baseline and explanation of every score/recommendation before considering learned models.
- Persist raw evidence separately from derived state so algorithms can evolve reproducibly.
- Specify offline and online outcome metrics needed to evaluate any future proprietary model.

## AI economics / proprietary-model guardrail

For this sprint explicitly answer:

- Does this add or multiply an inference call?
- What is the expected invocation rate per learner action?
- Can deterministic code/cache remove the call?
- Can an economy-tier approved route satisfy the task?
- Is a secondary solver actually required, and what triggers it?
- What telemetry will reveal cost regression?
- Does this create data that might later be useful for training? If yes, it remains **not training eligible by default** and requires separate governed eligibility.

No sprint may introduce model training/self-hosted inference unless it belongs to the conditional proprietary-ML phase or an accepted ADR explicitly authorizes it.

## Production data contract checklist

If durable state changes:

- define canonical owner and source of truth;
- add migration with forward/backward operational plan;
- define FK/unique/check constraints where appropriate;
- define indexes from concrete access patterns;
- define retention/account-deletion behavior;
- define audit/provenance fields;
- ensure raw AI output is not persisted as authoritative domain state without validation;
- add fixture/contract updates for downstream consumers.

## API and concurrency contract checklist

If network/API behavior changes:

- validate request shape separately from domain rules;
- specify authentication, object authorization and entitlement;
- use idempotency for harmful duplicate mutation;
- define timeout/retry semantics;
- define async job state if response cannot complete in request budget;
- return stable machine-readable error codes;
- test duplicate/out-of-order/retry conditions;
- maintain backward compatibility or explicitly version the contract.

## Security/privacy abuse cases to test

At least evaluate:

- unauthorized object reference;
- malformed/oversized/adversarial input;
- retry or concurrency abuse;
- quota bypass;
- prompt injection where AI input is involved;
- private content appearing in logs/analytics;
- account deletion/retention omission;
- accidental training-data eligibility or dataset export.

## Observability and cost evidence

The sprint is not complete until a maintainer can diagnose it without reading raw student content. Add or verify:

- trace/correlation ID;
- success/failure/domain-outcome counters;
- p50/p95 latency where meaningful;
- retry/fallback/escalation counters;
- AI token/unit/cost attribution if AI is touched;
- cache hit/miss if caching is touched;
- alert or dashboard update for material operational risk.

## Test and evaluation matrix

Required as applicable:

| Layer | Evidence |
|---|---|
| Domain | unit tests for invariants/state transitions |
| Persistence | PostgreSQL/Testcontainers constraints and query behavior |
| API | contract + authz + stable error tests |
| iOS | ViewModel/use-case tests + critical UI/recovery path |
| AI | schema validation + golden dataset regression |
| Verification | deterministic/property-based tests for supported math |
| Security | abuse/object-authorization/secret/PII tests |
| Operations | failure injection or rollback demonstration |
| Economics | before/after expected invocation and cost impact when material |

## Rollout contract

For user-visible or model-sensitive behavior:

1. local/integration proof;
2. staging with production-like dependencies;
3. feature flag or route configuration;
4. internal/dogfood exposure;
5. canary if risk warrants;
6. observe product/quality/cost metrics;
7. progressive rollout;
8. documented rollback trigger.

## Expanded acceptance gate

- [ ] Implementation map exists and matches code ownership.
- [ ] Domain invariants remain server-authoritative.
- [ ] New AI calls have measured/estimated unit-cost impact.
- [ ] Secondary solver use is policy-driven rather than unconditional.
- [ ] New user data is not implicitly training eligible.
- [ ] Failure/degraded/retry path is implemented and tested.
- [ ] Observability supports diagnosis without sensitive-content logging.
- [ ] All relevant automated tests/evaluations pass.
- [ ] Feature flag/rollback exists when blast radius is material.
- [ ] Canonical docs were updated in the same change.
- [ ] No temporary dependency or folder violates the repository hierarchy.

## Required sprint evidence package

Attach or persist links/artifacts for:

- architecture/implementation map;
- migration/API/schema diff if any;
- test summary;
- AI evaluation report if any;
- cost/latency comparison if any;
- screenshots/demo for user-visible work;
- operational trace/metric example;
- rollout and rollback configuration;
- known limitations and follow-up issues.

## Codex execution rule

Codex must implement this sprint in small reviewable slices, re-reading the highest-precedence semantic documents before changing domain/API/data contracts. It must not “simplify” the architecture by moving rules into prompts, client code, provider SDK adapters, generic helpers, or ungoverned ML scripts.
<!-- PRODUCTION_EXECUTION_V3:END -->

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-07`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_8.2`
- **Canonical Title:** 8-Family Verified Twin Practice Generator & Progressive Variant Ladder (`Variant A / B / C`)
- **Status:** `BLOCKED_BY_ENTRY_GATE (ADR-011)`
- **Primary Spec Reference:** `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`

## 2. Strategic Learning OS Objective & User Value
Expands `V031` `counterfactual_twin_drills` and `services/math-verifier/app/verifiers/attempt_learning.py` into the full **8-Family Verified Twin Engine** with a 3-tier progressive difficulty ladder:
- **Variant A (`ISOMORPHIC_TRAP_TWIN`):** Parameter-perturbed isomorphic problem preserving the exact structural hazard.
- **Variant B (`REVERSED_MASKED_TWIN`):** Reverses target/given (e.g., find parameter $k$ given extraneous root or critical point).
- **Variant C (`EXAM_SYNTHESIS_TWIN`):** Combines the target skill with an adjacent prerequisite under exam framing.

## 3. Entry Gate & Preconditions
- `ADR-011` `GO` decision recorded; `Sprint 8.1` accepted.

## 4. Domain Invariants & Mathematical Rules (`learning/84` Section 5)
1. **100% SymPy Trap Invariant Gate (`G-MATH-05`):** Every candidate twin across all 8 families (Rational holes $x \neq a$, Radical/Log extraneous roots, Lost roots from variable division, Absolute value branches, Negative inequality multiplier flips, Quadratic discriminant regimes, Calculus chain/product rules, $0/0$ limits & $+C$ integrals) is symbolically verified by SymPy before storage.
2. **Fail-Closed Refusal (`INV-TWIN-008`):** Any candidate failing symbolic verification after 2 retries within budget (`<1,500ms` deterministic, `<3,500ms` hybrid) is rejected fail-closed and replaced by a curated pre-verified skill item.

## 5. Owning Spring Modulith Backend Modules & Classes
- `com.verifiedai.studyplan`: `TargetedTwinOrchestrator.java`, `TwinVariantLadderPolicy.java`.
- `com.verifiedai.attempt`: `CounterfactualTwinDrill.java` (`V031` + `V041`).

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- `services/math-verifier/app/verifiers/attempt_learning.py`: adds deterministic parametric AST generators and SymPy structural invariant assertions for all 8 families.

## 7. Database Migration & Persistence Contract (`PLANNED V041`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V041__verified_twin_ladder_and_templates.sql` *(PLANNED)*
- **Schema Changes:** `ALTER TABLE counterfactual_twin_drills` (`V031`) adding `variant_tier VARCHAR(32) NOT NULL DEFAULT 'VARIANT_A_ISOMORPHIC'`, `generation_tier VARCHAR(32) NOT NULL DEFAULT 'DETERMINISTIC_TEMPLATE'`, `evidence_type_result VARCHAR(32)`, `hints_used_count INT NOT NULL DEFAULT 0`.

## 8. REST API Contracts (`CNT-LOS-03`)
- `POST /v1/attempts/{attemptId}/twins?variantTier={A|B|C}` and `POST /v1/twins/{twinId}/submit`.

## 9. iOS (`apps/ios/VerifiedAI/`) & Future Android Scope
- Updates `apps/ios/VerifiedAI/Features/ProblemSolving/` to display Variant A/B/C progression badges and the 60-second independent lock timer.

## 10. AI Capability & Unit Economics Guardrail
- Tier-1 deterministic templates serve $\ge 80\%$ of requests at **`$0.00` LLM token cost**; Tier-2 hybrid (`AiCapability.PRACTICE_GENERATE`) is guarded by `PostgresAiSpendGuard` (`V016`/`V017`).

## 11. Privacy, Safety & Compliance
- Covered by `StudentAttemptPrivacyLifecycleContributor` (`V031` cascade).

## 12. Telemetry & Observability
- Emits `twin_variant_generated` (`variant_tier`, `generation_tier`, `latency_ms`) and `twin_invariant_check_failed`.

## 13. Feature Flags & Rollback Plan
- `verifiedai.studyplan.twin-ladder-enabled: false`, `verifiedai.studyplan.hybrid-twin-llm-enabled: false`.

## 14. Automated Test Matrix (Categories A–F)
- `test_attempt_learning_twins.py` testing 100 generated twins per family (800 cases) for non-empty solution sets and active trap preservation (`100%` pass requirement).

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_8_2_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not generate unverified Euclidean geometry drawings.

## 17. Exit Gate & Downstream Consumers
- Unlocks `Sprint 8.3` (Spaced Repetition Scheduler) and `Sprint 8.4` (Daily 15-Minute Coach Session).

## 18. Canonical Cross-References
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`
- `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
