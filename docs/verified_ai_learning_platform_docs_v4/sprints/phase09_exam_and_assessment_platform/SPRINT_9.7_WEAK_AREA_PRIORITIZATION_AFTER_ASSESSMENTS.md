# Sprint 9.7 — Weak-Area Prioritization After Assessments

Status (2026-11-17): **COMPLETED — OWNER HOST ACCEPTANCE PASSED**. See [execution report](SPRINT_9.7_EXECUTION_REPORT.md).

## Sprint mission

Convert mock-exam outcomes into high-value skill recommendations and targeted review.

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
- **Sprint ID:** `SPRINT_9.7` (**Option A — Wave 2 of 5**: includes `Bridge B4` Phase 13 Clean Seed Contract)
- **Canonical Title:** Post-Assessment Mistake Attribution ("Deneme Hata Karnesi"), YKS Net-Gain Simulator (`H-03`) & Phase 13 Clean Seed Export (`Bridge B4`)
- **Status:** `PLANNED_OPTION_A_WAVE_2` (`ADR-011` feature-flagged; depends on `SPRINT_9.6` & `SPRINT_9.6B`)
- **Primary Spec Reference:** `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md` (`H-03`), `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md` (Screens 9 & 10), `sprints/phase09_exam_and_assessment_platform/PHASE_09_TYT_AYT_BRIDGE_AND_REMAINING_SPRINTS_PLAN.md`

## 2. Strategic Learning OS Objective & User Value
Converts completed mock/diagnostic sessions or photographed paper deneme errors into a **4-Category Post-Exam Attribution Breakdown**:
1. `CONCEPTUAL_GAP` (low skill mastery $M_{u,s} < 0.45$)
2. `RECURRING_MISTAKE_TRAP` (matches an active `mistake_code` in Mistake DNA `V037` or distractor rationale in `V060`)
3. `TIME_PRESSURE_SLIP` (`ARITH_BASIC_COMPUTATION` under high time-per-item pressure)
4. `STRATEGIC_OMISSION` (unattempted item)
and ranks study recommendations by **Expected Net Gain per Study Hour ($\Delta\text{Net}/\text{hr}$)** while establishing the `ADR-006`-compliant `evaluations/exports/yks-tr-phase13-seed/` SME/SymPy clean export contract (`Bridge B4`).

## 3. Entry Gate & Preconditions
- `Sprint 9.6` (`V059`) and `Sprint 9.6B` (`V060`, `Bridge B1+B2+B3`) accepted (`LOCAL_ACCEPTANCE_PASSED`).

## 4. Domain Invariants & Attribution Rules
1. **Every Wrong Mock Item Links to Canonical Attempt Evaluation (`V035`/`V036`):** When a student uploads or enters steps for a missed mock question, the step verifier attributes the exact `mistake_code` rather than treating the question as a generic binary wrong answer.
2. **Expected Net-Gain per Study Hour Formula (`H-03`):**
   $$\frac{\Delta\widehat{\text{Net}}}{\text{hr}}(u, s) = \frac{1.25 \cdot w_{E,s} \cdot \left(0.85 - p_{u,s}^{\text{exam}}(t)\right)_+}{\text{EstimatedRemediationHours}(M_{u,s}, \theta_{u,m})}$$

## 5. Owning Spring Modulith Backend Modules & Classes
- `com.verifiedai.exam`: `PostAssessmentAttributionService.java`, `NetGainSimulatorService.java`.
- `com.verifiedai.studyplan`: consumes post-exam attribution to reprioritize the next 7 days of `DailyCoachSession` tasks.

## 6. Python Math Verifier Impact
- Uses `attempt_verification_v2.py` and `attempt_diagnosis.py` on submitted mock scratchpad work.

## 7. Database Migration & Persistence Contract (`PLANNED V061`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V061__sprint_9_7_post_assessment_attribution.sql` *(builds on `V057`–`V060`)*
- **Tables:**
  - `exam_session_item_attributions` (`attribution_id`, `session_id`, `item_evaluation_id`, `user_id`, `skill_id`, `attribution_category`, `matched_mistake_code`, `time_spent_seconds`, `estimated_net_impact`, `recommended_action_type`, `training_eligible = FALSE`, `llm_tokens_used = 0`)
  - `exam_study_reallocation_batches` (`batch_id`, `user_id`, `source_session_id`, `top_roi_plan_json`, `applied_to_study_plan`, `created_at`, `training_eligible = FALSE`)

## 8. REST API Contracts (`CNT-LOS-10` & `CNT-LOS-11`)
- `GET /v1/exams/sessions/{sessionId}/attribution-report` (& `/api/v1/...`), `POST /v1/exams/sessions/{sessionId}/apply-study-rebalance`, and `GET /v1/exams/readiness`.

## 9. iOS (`apps/ios/VerifiedAI/`) & Future Android Scope
- Renders `PostExamAttributionView.swift`, `PostExamAttributionViewModel.swift`, offline SQLite cache (`cached_exam_attributions`), and High-ROI Topic Allocator Table (`EXP-LOS-07`).

## 10. AI Capability & Unit Economics Guardrail
- Attribution math and ROI ranking are **100% deterministic (`$0.00` LLM cost)**. `Bridge B4` seed exports strictly exclude all production student rows (`training_eligible = FALSE`) and include only SME/synthetic `SymPy`-verified records.

## 11. Privacy, Safety & Compliance
- Covered by `ExamPrivacyLifecycleContributor`.

## 12. Telemetry & Observability
- Emits `exam_attribution_generated` and `net_gain_simulator_task_started` (`EXP-LOS-07`).

## 13. Feature Flags & Rollback Plan
- `verifiedai.exam.net-gain-simulator-enabled: false`.

## 14. Automated Test Matrix (Categories A–F)
- Verifies deterministic attribution into the 4 categories and verifies high-weight exam skills with remediable traps rank at the top of $\Delta\text{Net}/\text{hr}$.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_9_7_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not predict university placement rank percentiles (`Sıralama`) without explicit disclaimer bounds.

## 17. Exit Gate & Downstream Consumers
- Unlocks `Sprint 9.8` (Pre-Exam 48-Hour Trap Immunity Cram Pack `H-05`) and `Sprint 9.9`.

## 18. Canonical Cross-References
- `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md`
- `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md`
- `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
