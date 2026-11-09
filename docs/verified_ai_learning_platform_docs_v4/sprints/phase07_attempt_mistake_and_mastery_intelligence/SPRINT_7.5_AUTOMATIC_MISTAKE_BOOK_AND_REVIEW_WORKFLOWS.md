# Sprint 7.5 — Automatic Mistake Book and Review Workflows

## Sprint mission

Create a durable, searchable, grouped mistake history with review states and practice-again actions.

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

## Backend implementation work

- Place implementation in the owning Spring Modulith module rather than a global technical-layer package.
- Define application commands/queries and keep controllers thin.
- Put domain rules in domain/application policy, not JPA callbacks or HTTP mapping code.
- Expose infrastructure through ports/adapters when external systems are involved.
- Use typed Problem Details error codes for client-recoverable failures.
- Make mutating retryable operations idempotent where duplicate execution is materially harmful.
- Emit domain events only after the owning aggregate/application rule has accepted the state transition.
- Add authorization checks at the application boundary, not solely in UI visibility.

### Backend files to review/create

- `services/api/src/main/java/com/verifiedai/mistake/**`
- `services/api/src/main/java/com/verifiedai/attempt/**`
- `services/api/src/main/resources/db/migration/platform/V037__learner_mistake_dna_profiles_and_disputes.sql` *(PLANNED)*
- corresponding module tests (`MistakeDnaServiceTest`, `MistakeDisputeRollbackIntegrationTest`) and `ModularityTests`.

## iOS implementation work

- Keep SwiftUI views declarative and free of transport/persistence policy.
- Introduce typed view state and explicit loading/error/retry states.
- Access remote behavior through use cases/repositories and shared `APIClient` contracts.
- Store sensitive tokens only in Keychain-backed secure storage.
- Make navigation and deep-link behavior deterministic and testable.
- Add Dynamic Type, VoiceOver labels/hints, reduced-motion behavior, and localization keys during implementation rather than after feature completion.
- Ensure app termination/backgrounding does not corrupt long-running user workflows.

### iOS files to review/create

- `apps/ios/VerifiedAI/Features/AttemptAnalysis/**`
- `apps/ios/VerifiedAI/Features/ProblemReview/**`
- feature unit tests, accessibility coverage, and critical UI-flow tests.

## Data and persistence work

- Identify canonical tables and foreign-key ownership before adding persistence.
- Prefer explicit relational columns for business facts; use JSONB only for evolving artifacts/metadata where relational querying is not the primary contract.
- Add Flyway migrations; never rely on ORM auto-DDL in production.
- Define unique constraints and indexes from access patterns and invariants, not only after performance problems appear.
- Define retention/deletion classification for any newly persisted student or AI-derived data.
- Ensure audit/history requirements are satisfied without logging raw sensitive problem content unnecessarily.
- Add Testcontainers integration coverage for PostgreSQL-specific behavior.

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

## Phase-level engineering objective — Attempts, mistakes and mastery

- Preserve immutable learner evidence and explainable deterministic baselines.
- Design stable task contracts that can later support small proprietary classifiers.
- Separate user correction, model suggestion and verified labels in provenance.

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
- **Sprint ID:** `SPRINT_7.5`
- **Canonical Title:** Automatic Mistake Book, Longitudinal Mistake DNA Ledger, and OCR Dispute Rollback Workflows
- **Status:** `PLANNED` (`GATED_ON_SPRINT_7_4_OWNER_ACCEPTANCE`)
- **Primary Spec Reference:** `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`

## 2. Strategic Learning OS Objective & User Value
Transforms isolated per-attempt mistake classifications (`V036` `attempt_mistake_classifications`) and raw mistake events (`V031` `learner_mistake_events`) into a longitudinal **Mistake DNA Ledger** that tracks exposure-adjusted recurring error propensity ($\theta_{u,m}$), enforces first-meaningful-error causal filtering (`INV-MDNA-001`), manages the 6-state recurrence lifecycle (`OBSERVED_ONCE` -> `RECURRING_PATTERN` -> `IN_REMEDIATION` -> `RESOLVED_PENDING_RETENTION` -> `STABLE_RESOLVED` / `RELAPSED`), and empowers learners to self-correct OCR step misreads or dispute diagnoses with immediate `<250ms` rollback (`INV-MDNA-004`).

## 3. Entry Gate & Preconditions
- `Sprint 7.4` (`V036`, `MistakeTaxonomy` `mistake-category-v1`) must pass owner acceptance (`bash scripts/quality/sprint_7_4_owner_acceptance.sh --full`).
- Existing `V030`–`V036` schema lineage must remain untouched and forward-compatible.

## 4. Domain Invariants & Mathematical Rules
1. **Causal First-Error Isolation (`INV-MDNA-001`):** Only `first_error_step_index` from `AttemptEvaluation` (`V035`) emits a primary causal mistake update. Downstream propagated steps (`DOWNSTREAM_PROPAGATION`) never increment error counts.
2. **Confidence Eligibility Gate (`INV-MDNA-002`):** Only `CLASSIFIED` attempts with `evidence_status == 'SUFFICIENT'` and `confidence >= 0.80` directly mutate recurring pattern states; `0.65 <= confidence < 0.80` records a tentative signal; `< 0.65` or `UNCLASSIFIED` / `STEP_GAP_TOO_WIDE` causes zero mutation.
3. **Exposure-Adjusted Empirical Bayes Rate (`INV-MDNA-003`):**
   $$\theta_{u,m} = \frac{E_{u,m}^{\text{decayed}} + \alpha_0 \mu_m}{N_{u, s(m)}^{\text{decayed}} + \alpha_0}, \qquad h_m = 21\text{ days},\; \alpha_0 = 4.0$$
4. **Atomic Dispute/OCR Rollback (`INV-MDNA-004`):** Editing a misread step or disputing a diagnosis marks the prior event `SUPERSEDED_BY_OCR_CORRECTION` or `DISPUTED_BY_LEARNER` and recomputes $\theta_{u,m}$ deterministically.

## 5. Owning Spring Modulith Backend Modules & Classes
- `com.verifiedai.mistake`:
  - `LearnerMistakeDnaProfile.java` (Aggregate root for `(userId, mistakeCode, canonicalSkillCode)`)
  - `MistakeRecurrenceState.java` (`OBSERVED_ONCE`, `RECURRING_PATTERN`, `IN_REMEDIATION`, `RESOLVED_PENDING_RETENTION`, `STABLE_RESOLVED`, `RELAPSED`)
  - `EmpiricalBayesMistakeRateCalculator.java` (pure deterministic math service)
  - `LearnerMistakeDnaService.java` (handles `AttemptMistakeClassifiedEvent`, state transitions, and dispute rollbacks)
  - `MistakeDnaPrivacyLifecycleContributor.java` (implements `AccountPrivacyLifecycleContributor`)
- `com.verifiedai.attempt`:
  - `AttemptStepOcrCorrectionService.java` (re-evaluates corrected step via `AttemptEvaluationService` and triggers DNA rollback)

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- Reuses `services/math-verifier/app/verifiers/attempt_diagnosis.py` and `counterexample_engine.py`; no new paid LLM dependency.

## 7. Database Migration & Persistence Contract (`PLANNED V037`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V037__learner_mistake_dna_profiles_and_disputes.sql` *(PLANNED)*
- **Tables:**
  - `learner_mistake_dna_profiles`: `id (UUID PK)`, `user_id (UUID NOT NULL)`, `mistake_code (VARCHAR(64) NOT NULL)`, `category (VARCHAR(64) NOT NULL)`, `canonical_skill_code (VARCHAR(128) NOT NULL)`, `taxonomy_version (VARCHAR(32) NOT NULL)`, `decayed_exposure_count (NUMERIC(10,4))`, `decayed_error_count (NUMERIC(10,4))`, `smoothed_propensity (NUMERIC(6,4))`, `priority_score (NUMERIC(6,2))`, `recurrence_state (VARCHAR(32) NOT NULL)`, `consecutive_independent_passes (INT NOT NULL DEFAULT 0)`, `last_attempt_id (UUID)`, `last_counterexample_json (JSONB)`, `last_occurred_at (TIMESTAMPTZ)`, `last_remediated_at (TIMESTAMPTZ)`, `next_retention_check_at (TIMESTAMPTZ)`, `updated_at (TIMESTAMPTZ)`. Unique index on `(user_id, mistake_code, canonical_skill_code)`.
  - `learner_mistake_dispute_events`: `id (UUID PK)`, `user_id (UUID NOT NULL)`, `attempt_id (UUID NOT NULL)`, `step_index (INT)`, `dispute_type (VARCHAR(32) NOT NULL)`, `previous_step_latex (TEXT)`, `corrected_step_latex (TEXT)`, `resolution_status (VARCHAR(32) NOT NULL)`, `created_at (TIMESTAMPTZ NOT NULL)`.

## 8. REST API Contracts (`CNT-LOS-01` & `CNT-LOS-02`)
- `GET /v1/mistake-passport` (`schemaVersion: "mistake-passport-v1"`)
- `POST /v1/attempts/{attemptId}/steps/{stepIndex}/correct-ocr` (`Idempotency-Key` required)
- `POST /v1/mistakes/{eventId}/dispute` (`Idempotency-Key` required)

## 9. iOS (`apps/ios/VerifiedAI/`) & Future Android (`apps/android/`) Scope
- Adds `Adımı Düzenle (OCR Hatası)` and `Hatalı Analiz Bildir` actions to `apps/ios/VerifiedAI/Features/AttemptAnalysis/` (Screen 3 in `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md`).
- Shared JSON fixtures exported for Android parity (`Sprint 12.10`).

## 10. AI Capability & Unit Economics Guardrail
- **100% Deterministic CPU execution (`$0.00` LLM token cost)** for Mistake DNA aggregation, Beta-Binomial rate smoothing, and dispute rollback.

## 11. Privacy, Safety & Compliance
- Registers `MistakeDnaPrivacyLifecycleContributor` so `DELETE /v1/accounts/me` purges `learner_mistake_dna_profiles` and `learner_mistake_dispute_events` (`G-PRIV-01` in `quality/91`).

## 12. Telemetry & Observability
- Emits `mistake_dna_updated`, `mistake_dna_state_transitioned`, `attempt_step_ocr_corrected`, and `attempt_diagnosis_disputed`.

## 13. Feature Flags & Rollback Plan
- Controlled by `verifiedai.mistake.dna-ledger-enabled: false` (default off in `application.yml`). Disabling preserves `V037` rows read-only while reverting UI to single-attempt classification view.

## 14. Automated Test Matrix (Categories A–F)
- **A:** `ModularityTests` verifying `mistake` <-> `attempt` boundaries.
- **B:** `EmpiricalBayesMistakeRateCalculatorTest` (verifying 2/3 errors ranks higher than 3/40 errors), `MistakeDnaStateMachineTest`, `MistakeDisputeRollbackIntegrationTest`.
- **C/D:** iOS `AttemptAnalysisViewModelTests` for OCR edit and dispute state recovery.
- **E:** Privacy deletion cascade test & cross-user `403` authorization test.
- **F:** `python3 scripts/quality/docs_check.py`.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_7_5_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not modify BKT skill mastery transition weights (owned by `Sprint 7.6`).
- Does not build the full Daily Coach Study Planner (owned by `Phase 8`).

## 17. Exit Gate & Downstream Consumers
- Unlocks `Sprint 7.6` (Evidence-Stratified Mastery), `Sprint 7.7` (Decay & Reassessment), and `Sprint 7.9` (Mistake Passport Dashboard UX).

## 18. Canonical Cross-References
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
- `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
