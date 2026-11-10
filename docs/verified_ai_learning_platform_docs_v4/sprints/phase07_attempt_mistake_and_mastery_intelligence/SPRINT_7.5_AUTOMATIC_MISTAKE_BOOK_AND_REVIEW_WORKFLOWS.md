# Sprint 7.5 — Automatic Mistake Book and Review Workflows

## Sprint mission

Create the longitudinal Mistake DNA foundation and correction/dispute controls. The reconciled Sprint 7.5 contract below scopes full Passport and practice-again workflows to Sprints 7.7–7.8.

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
- `services/api/src/main/resources/db/migration/platform/V037__learner_mistake_dna_profiles_and_disputes.sql` *(AUTHORED — NOT APPLIED)*
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
- **Status:** `SOURCE_COMPLETE_PENDING_OWNER_TEST`; `PRODUCTION_NOT_AUTHORIZED`.
- **Entry gate:** Sprint 7.4 implementation is committed at `e39bafe`; a matching passing FULL owner run is not established locally. Isolated Sprint 7.5 source work is authorized; acceptance and release remain blocked.
- **Source report:** `SPRINT_7.5_EXECUTION_REPORT.md` contains the implementation map, acceptance coverage, limitations and exact file changes.

## 2. Strategic Learning OS Objective & User Value
Implement the longitudinal Mistake DNA foundation and learner correction/dispute controls in Attempt Analysis.
The original broad Mistake Book/practice-again mission is superseded by roadmap 87: full Passport is Sprint 7.7,
remediation and retention closure are Sprint 7.8, mastery is Sprint 7.6. None is launched here.

## 3. Entry Gate & Preconditions
Preserve V030–V036 files and immutable accepted evidence. The owner-only runner checks original Sprint 7.4 FULL
evidence against committed runtime/test/contracts at `e39bafe`; it does not execute or overwrite old acceptance runs.
An unavailable prior gate returns BLOCKED while independent Sprint 7.5 checks still run.

## 4. Domain Invariants & Mathematical Rules
1. `INV-MDNA-001`: one V036 classification per accepted evaluation; only its validated first wrong step can be causal. Later NOT_ASSESSED/EARLIER_DIVERGENCE steps contribute nothing. No secondary causal error inference.
2. `INV-MDNA-002`: retain `attempt-taxonomy-v2`, `mistake-category-v1`, `evidence-classifier-v1`, `bounded-attempt-v2` and `structural-signatures-v1`. Eligibility requires `SIGNATURE_SUPPORTED`, `DETERMINISTIC_EVIDENCE`, `STRUCTURAL_AND_DISPROOF`, `AUTO_DETECTED`, accepted source/work/execution lineage and a confirmed causal prefix. V036 has no numerical confidence; no numeric or tentative tier is manufactured.
3. `INV-MDNA-003`: an exposure requires the exact canonical source's latest accepted CLASSIFIED primary-skill tag **as of work submission**, a compatible active curriculum ontology, and either the eligible mapped causal diagnosis or a fully verified final answer with every step VALID_EQUIVALENT and NO_ERROR_ESTABLISHED classification. A causal code's existing authoritative skill must match that source tag. Missing, ambiguous, unrelated or unmapped skill evidence cannot produce propensity. This is a conservative eligible-evidence cohort, not a rate across all raw attempts. V031 legacy skill labels/Twin events are not denominators.
4. Weight is `2^(-elapsedUtcDays/21)`; propensity is `(decayedErrors + 4*0.10)/(decayedExposures + 4)`. Stable time/UUID ordering, StrictMath, injected Clock, UTC microsecond snapshots, scale 8 HALF_EVEN output and threshold comparison. Future events are excluded until their event time. Delayed evidence uses original work time. There are no invented severity/exam weights.
5. Recurrence requires **two causal errors in the inclusive preceding 21 days AND rounded propensity >= 0.22**. Only OBSERVED_ONCE and RECURRING_PATTERN are projected. All six enum states are reserved; remediation/resolution/relapse transitions require future evidence and are rejected here. Removing all effective errors removes the profile, not a false resolved badge.
6. `INV-MDNA-004`: append-only learner operations exclude the original attempt from both numerator and denominator; recompute in the same owner-locked transaction. Ordinary structured-work supersession also invalidates the earlier revision. Dispute has no automatic reactivation. Source/parent deletion invalidates cached profiles.

## 5. Owning Spring Modulith Backend Modules & Classes
- `mistake`: LearnerMistakeDnaService, LearnerMistakeDnaProfile, EmpiricalBayesMistakeRateCalculator, MistakeRecurrenceState, MistakeDnaEvidenceSource, MistakeDnaRepository and MistakeDnaPrivacyLifecycleContributor.
- `attempt`: AttemptMistakeDnaEvidenceSource validates immutable lineage through the public mistake port; AttemptMistakeService refreshes accepted/replayed classifications; AttemptWorkService refreshes supersession; AttemptStepOcrCorrectionService creates the replacement work.
- `problem`: SolveSourceAccess.skillAt supplies existing accepted source-specific curriculum provenance. It neither classifies new math nor calls a provider.
- Dependency direction remains attempt -> mistake; mistake imports no attempt implementation classes.

## 6. Python Math Verifier Impact
No verifier algorithms, paid calls or model routing changes. Existing evaluation/classification workflows remain the sole mathematical authority.

## 7. Database Migration & Persistence Contract
`V037__learner_mistake_dna_profiles_and_disputes.sql` is authored, NOT APPLIED.
- `learner_mistake_dna_profiles` is a disposable derived cache keyed by owner/code/skill/policy, with taxonomy/category versions, decayed counts, propensity, recent count, recurrence, last event and as-of time.
- `learner_mistake_dispute_events` is append-only, owner/attempt bound, unique by owner/key and attempt/operation type. Composite FKs bind classifications and replacement work. Hash-bound retries return the original operation. OCR rows reference original/replacement work instead of copying private step text.
- V036 remains the immutable diagnosis ledger; V034 remains revision history. V031 is not duplicated or rewritten. Profile deletion on parent classification cascade prevents orphaned derived retention.

## 8. REST API Contracts
- `GET /api/v1/mistakes/dna`: FREE receives at most two grouped profiles and no ledger entries; current PRO/PRO_PLUS entitlement permits the owner ledger. Stable grouping/ranking is propensity descending then code/skill; no full Passport experience.
- `GET /api/v1/mistakes/{eventId}/effective-state`: owner-only per-diagnosis state, available independently of premium history access.
- `POST /api/v1/mistakes/{eventId}/dispute`: strict `mistake-dispute-request-v1` and Idempotency-Key; response `mistake-dispute-result-v1`, EXCLUDED.
- `POST /api/v1/problem-sessions/{problemId}/attempts/{id}/steps/{stepIndex}/correct-ocr`: strict `attempt-ocr-correction-request-v1`; preserves original OCR and creates a new immutable work revision. Response `attempt-ocr-correction-result-v1`, REEVALUATION_REQUIRED. Explicit existing evaluation/classification actions subsequently admit new accepted evidence. No network I/O occurs in the correction transaction.
- Existing Problem Details, 16 KiB body bound, owner locks, rate limiting and 8–128 character idempotency keys apply. Foreign resources return 404. Manual work cannot be relabeled as OCR.

## 9. iOS Scope
Attempt Analysis gains localized EN/TR dispute status and a step correction editor. Responses are fenced by account,
generation and original evidence identity. Correction retries are owner/problem scoped, size bounded, protected on disk,
excluded from backup and covered by the existing account purge. An uncertain acknowledgement can be retried with its
saved key after relaunch. Reviewing saved history discards only the local retry, never undoes a server correction.
No Android application or Passport screen is introduced.

## 10. AI Capability & Unit Economics Guardrail
Zero new inference calls for aggregation/dispute/correction. Explicit re-evaluation uses the existing bounded verifier,
not a paid model. No student data becomes training-eligible.

## 11. Privacy, Safety & Compliance
The existing AccountDataLifecycleContributor mechanism exports cached profiles (explicit as-of snapshots) and immutable
operation references. Account deletion removes profiles and parent-work cascades remove operations. Retention follows
DERIVED_LEARNING and existing backup expiry/deletion reconciliation; no new raw-image TTL or training consent is invented.

## 12. Telemetry & Observability
After commit: mistake_dna_updated, mistake_dna_state_transitioned (bounded from/to), attempt_step_ocr_corrected,
attempt_diagnosis_disputed. No student IDs, step text or private evidence IDs in metric tags. Rollback duration includes
DB projection work and exposes a 250 ms histogram boundary. Production/staging latency remains unverified.

## 13. Feature Flags & Rollback Plan
`verifiedai.mistake.dna-enabled` / `MISTAKE_DNA_ENABLED` defaults false, additionally requires attempt capability and
existing production authorization outside local/test. Disabling stops new DNA operations/projection delivery; original
classification/history/privacy and accepted operation replay remain. Retain V037 and compatible readers; do not roll back
migrations or erase evidence. Re-enabling rebuilds derived state from effective history.

## 14. Automated Test Matrix (Authored, Not Executed)
A: migration/source inventory and historical entry gate. B: Java domain, PostgreSQL migration/immutability/rollback,
concurrency, source/ownership/privacy, fixed clock and local synthetic rollback latency; existing ModularityTest and
7.1–7.4 regression suites. C: strict JSON/OpenAPI contracts and localization. D: Swift DTO/view-model/retry/account fences
and EN/TR dispute accessibility. E: fresh full backend security/privacy evidence. F: canonical documentation/manifest.
G: verifier regressions and synthetic acceptance-evidence integrity tests. Missing tools, required tests or evidence cannot PASS.

## 15. Owner-Only Acceptance Command
`bash scripts/quality/sprint_7_5_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
No mastery mutations/reversals: this V034–V036 path creates none. No new V031 learning events, numeric confidence,
secondary causal diagnoses, skill mappings, severity ranking, resolved badges, Passport, Twin remediation or Phase 8 work.

## 17. Exit Gate & Downstream Consumers
`NOT RUN — MANUAL OWNER EXECUTION REQUIRED`. All authored source remains pending owner verification. Sprint 7.4's
FULL entry evidence, current Sprint 7.5 FULL results and later owner approval are required before acceptance or advancement.
Sprint 7.6 has not begun; Sprint 7.7 owns Passport and Sprint 7.8 owns remediation closure.

## 18. Canonical Cross-References
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
- `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
