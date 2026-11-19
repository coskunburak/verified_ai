# Sprint 9.10 — Assessment Integrity, Accessibility, Failure Recovery, and Reliability

## Sprint mission

Harden exam mode against accidental data loss, timer inconsistencies, unfair accessibility barriers, and invalid scoring states.

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

## iOS implementation work

- Keep SwiftUI views declarative and free of transport/persistence policy.
- Introduce typed view state and explicit loading/error/retry states.
- Access remote behavior through use cases/repositories and shared `APIClient` contracts.
- Store sensitive tokens only in Keychain-backed secure storage.
- Make navigation and deep-link behavior deterministic and testable.
- Add Dynamic Type, VoiceOver labels/hints, reduced-motion behavior, and localization keys during implementation rather than after feature completion.
- Ensure app termination/backgrounding does not corrupt long-running user workflows.

### iOS files to review/create

- `apps/ios/VerifiedLearning/Features/<Feature>/Domain/**`
- `apps/ios/VerifiedLearning/Features/<Feature>/Data/**`
- `apps/ios/VerifiedLearning/Features/<Feature>/Presentation/**`
- feature unit tests, accessibility coverage, and critical UI-flow tests.

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

## Phase-level engineering objective — Exam and assessment platform

- Keep scoring/evidence server-authoritative and deterministic where possible.
- Calibrate readiness/confidence from evidence rather than LLM self-assessment.
- Prevent model changes from silently changing exam semantics.

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

### AI / ModelOps
- Define capability, route policy version, input/output schema and provenance fields before implementation.
- Establish baseline quality/cost/latency numbers before changing routing or models.
- Add at least one failure/degraded case where the system refuses false certainty.
- Confirm whether secondary solving is required by risk policy or can be skipped.
- Document rollback configuration and evaluation dataset version.

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
- **Sprint ID:** `SPRINT_9.10` (**Option A — Wave 5 of 5 / Phase 9 Exit Gate**)
- **Canonical Title:** Assessment Integrity, Tamper-Resistance, Mathematical VoiceOver Accessibility & Reliability (Phase 9 Exit Gate)
- **Status:** `PLANNED_OPTION_A_WAVE_5` (`ADR-011` feature-flagged; depends on `SPRINT_9.9`)
- **Primary Spec Reference:** `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`, `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`, `sprints/phase09_exam_and_assessment_platform/PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md`

## 2. Strategic Learning OS Objective & User Value
Hardens the entire exam engine against accidental data loss, timer tampering, network severance, and unfair accessibility barriers. Implements cryptographic submission integrity, ensures complete VoiceOver spoken math compliance for visually impaired learners across `V060` Turkish TYT/AYT expressions, and certifies Phase 09 exit criteria across all exam definitions, session runtimes, readiness models, and attribution reports.

## 3. Entry Gate & Preconditions
- `Sprints 9.1–9.6`, `9.6B (V060)`, `9.7 (V061)`, `9.8 (V062)`, and `9.9 (V063)` accepted (`LOCAL_ACCEPTANCE_PASSED`).

## 4. Domain Invariants & Integrity Rules
1. **Cryptographic Submission Integrity (`INV-EXAM-006`):** Final submission packets are signed with `HMAC-SHA256` (`exam-submit-hmac-v1`) using an HKDF-SHA256 session-scoped proof key tied to authenticated owner, `sessionId`, `startedAt`, UTC millisecond `clientTimestamp`, `Idempotency-Key`, single-use `submissionNonce`, and canonical submit body digest. Trust boundary: symmetric proof keys protect transit/session/replay binding across authenticated endpoints, whereas server-authoritative deadlines (`serverDeadlineAt`, `graceDeadlineAt`) and server autosave persistence remain the authoritative source against client tampering.
2. **Universal Spoken Math Accessibility (`ADR-010`):** Every mathematical formula in exam stems/options (`V060` Turkish TYT/AYT expressions included) scales with Dynamic Type (`xSmall` to `AX3`) and emits semantic VoiceOver speech strings in `tr-TR` via presentation-side bounded formatting (`SpokenMathTurkishAccessibilityFormatter.swift`, e.g., $\int_0^1 x^2 \, dx$ spoken as *"sıfırdan bire x kare d x integrali"*).
3. **Phase 9 Exit Gate Certification:** Formally validates that `CNT-LOS-10` (Exam Sessions) and `CNT-LOS-11` (Readiness Projection) execute with `< 350ms` P95 SLA over verified owner staging evidence (`>= 200` warmed samples per endpoint), zero P0/P1 defects, and full PostgreSQL/Flyway migration integrity (`V054`–`V064`).

## 5. Owning Spring Modulith Backend Modules & Classes
- `com.verifiedai.exam`: `ExamIntegrityCanonicalizer.java`, `AssessmentIntegrityGuard.java`, `ExamQualityGateService.java`, `ExamIntegrityAuditRepository.java`, `ExamSessionRuntimeController.java`, `AdminExamQualityGateController.java` (kept inside `com.verifiedai.exam.api` to respect Spring Modulith boundaries).

## 6. Python Math Verifier Impact
- Presentation-side semantic accessibility validation only; `services/math-verifier/` Python code and verification logic remain unchanged. Deterministic Swift formatter + 50 golden LaTeX-to-`tr-TR` fixtures verify lossless spoken speech tokens without runtime AI calls.

## 7. Database Migration & Persistence Contract (`V064`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V064__sprint_9_10_exam_integrity_audit_logs.sql`
- **Tables:** `exam_integrity_audit_logs` (`id`, `session_id`, `user_id`, `event_type`, `signature_verified`, `client_clock_skew_ms`, `payload_sha256`, `nonce_hash`, `integrity_key_id`, `idempotency_key_hash`, `protocol_version`, `reason_code`, `training_eligible = FALSE`, `created_at`) and `exam_integrity_used_nonces` (`session_id`, `user_id`, `nonce_hash`, `idempotency_key_hash`, `payload_sha256`, `integrity_key_id`, `training_eligible = FALSE`, `consumed_at`, `expires_at`).

## 8. REST API Contracts
- `GET /v1/admin/exams/quality-gate` and `/api/v1/admin/exams/quality-gate` (`ROLE_ADMIN`, `Cache-Control: no-store`, canonical schema `exam-quality-gate-v1`).
- `GET /v1/exams/sessions/{sessionId}/integrity-proof` and `/api/v1/exams/sessions/{sessionId}/integrity-proof` (`Cache-Control: no-store`, `exam-integrity-proof-response-v1`).
- `POST /v1/exams/sessions/{sessionId}/submit` and `/api/v1/exams/sessions/{sessionId}/submit` extended additively with optional `submissionSignature`, `integrityKeyId`, `submissionNonce`, `integrityProtocolVersion` (`exam-session-submit-request.schema.json`).

## 9. iOS (`apps/ios/VerifiedAI/`) & Future Android Scope
- Full VoiceOver accessibility certification and network failure injection tests in `Features/Exam/`.

## 10. AI Capability & Unit Economics Guardrail
- **`$0.00` LLM token cost** (pure infrastructure hardening).

## 11. Privacy, Safety & Compliance
- Compliant with minor protection and KVKK data integrity standards; audit logs purged on account deletion via `ExamPrivacyLifecycleContributor`.

## 12. Telemetry & Observability
- Emits `exam_integrity_verified`, `tamper_attempt_rejected`, `exam_quality_gate_passed`.

## 13. Feature Flags & Rollback Plan
- `verifiedai.exam.integrity-guard-enabled: false`.

## 14. Automated Test Matrix (Categories A–F)
- Security penetration tests attempting timer rollbacks and replay attacks; VoiceOver snapshot tests for 50 complex formulas.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_9_10_owner_acceptance.sh --VerifyHost` (`--verify-host` and `--full` aliases supported).
- Current implementation tracking: `SOURCE_IMPLEMENTED_PENDING_OWNER_ACCEPTANCE` ([execution report](SPRINT_9.10_EXECUTION_REPORT.md), [acceptance matrix](SPRINT_9.10_ACCEPTANCE_MATRIX.md), [implementation map](SPRINT_9.10_IMPLEMENTATION_MAP.md)).

## 16. Explicit Non-Goals
- Does not install invasive proctoring software or webcam-monitoring spyware.

## 17. Exit Gate & Downstream Consumers
- Formally certifies **Phase 9 Exit Gate** and unlocks Phase 10 (`Sprint 10.1` Monetization, Entitlements & Subscription Engine).

## 18. Canonical Cross-References
- `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
- `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
