# Sprint 9.2 — Learner Exam Goal Setup and Candidate Profile

## Sprint mission

Capture exam date, target score, accommodations, available study time, and curriculum version as planning inputs.

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

- `services/api/src/main/java/com/verifiedlearning/<owning-module>/domain/**`
- `services/api/src/main/java/com/verifiedlearning/<owning-module>/application/**`
- `services/api/src/main/java/com/verifiedlearning/<owning-module>/api/**`
- `services/api/src/main/java/com/verifiedlearning/<owning-module>/infrastructure/**`
- corresponding module tests and architecture tests.

## Data and persistence work

- Identify canonical tables and foreign-key ownership before adding persistence.
- Prefer explicit relational columns for business facts; use JSONB only for evolving artifacts/metadata where relational querying is not the primary contract.
- Add Flyway migrations; never rely on ORM auto-DDL in production.
- Define unique constraints and indexes from access patterns and invariants, not only after performance problems appear.
- Define retention/deletion classification for any newly persisted student or AI-derived data.
- Ensure audit/history requirements are satisfied without logging raw sensitive problem content unnecessarily.
- Add Testcontainers integration coverage for PostgreSQL-specific behavior.

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
- **Sprint ID:** `SPRINT_9.2`
- **Canonical Title:** Learner Exam Goal Setup, Candidate Profile & "Snap-to-Plan" Syllabus/Exam Ingestion (`H-14`)
- **Status:** `LOCAL_ACCEPTANCE_PASSED` (owner host run completed `2026-11-15`; rollout remains feature-flagged behind `ADR-011`; see `SPRINT_9.2_EXECUTION_REPORT.md` & `SPRINT_9.2_FILE_MANIFEST.md`)
- **Primary Spec Reference:** `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md` (`H-14`), `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md` (Screens 8 & 9)

## 2. Strategic Learning OS Objective & User Value
Captures target exam date, target net score, daily/weekly study budget, and accessibility accommodations (`1.00x`/`1.25x`/`1.50x`/`2.00x` extra-time multiplier, high contrast, screen reader optimization, reduced motion), alongside **"Snap-to-Plan" Syllabus & Exam Schedule Ingestion (`H-14` Astra AI Benchmark)**: allowing learners to upload a PDF or photograph of their school exam timetable or teacher's syllabus sheet and deterministically map extracted topics onto verified curriculum nodes.

## 3. Entry Gate & Preconditions
- `ADR-011` production gating preserved via feature flags (`verifiedai.exam.candidate-profile-enabled: false`, `verifiedai.exam.syllabus-ingestion-enabled: false`); `Sprint 9.1` (`V054`) implemented.

## 4. Domain Invariants & Rules
1. **Immutable Candidate Exam Horizon:** Profile updates compute days remaining (`> 0`), effective daily capacity (`min(daily, weekly / 7)`), required daily study minutes, and feasibility status (`OPTIMAL`, `STRETCH`, `AT_RISK`, `UNREALISTIC`), rejecting past/today dates (`422 CANDIDATE_EXAM_DATE_IN_PAST`), invalid budgets (`< 15` or `> 960` min/day or `weekly < daily` or `> 6720`, `422 CANDIDATE_STUDY_BUDGET_INVALID`), invalid accommodations (`422 CANDIDATE_ACCOMMODATION_INVALID`), and appending immutable audit rows to `candidate_profile_horizon_history`.
2. **Syllabus Topic Disambiguation:** Ingested syllabus items map to canonical `CurriculumTaxonomyCatalog` skills using normalized bilingual token similarity (`threshold = 0.70`); items at or above threshold auto-confirm (`PARSED_AUTO_CONFIRMED`), while lower-confidence items enter `NEEDS_DISAMBIGUATION` with ranked alternatives until confirmed via `POST /v1/candidate/syllabus-uploads/{uploadId}/confirm` (`USER_CONFIRMED`).
3. **Modulith Event Boundary:** `CandidateProfileService` publishes `CandidateGoalUpdatedEvent` under `com.verifiedai.exam.events` (`@NamedInterface("events")`), consumed by `com.verifiedai.studyplan.rebalance.CandidateGoalRebalanceListener`.

## 5. Owning Spring Modulith Backend Modules & Classes
- `com.verifiedai.exam.domain`: `CandidateProfileModels.java`, `CandidatePaceCalculator.java`, `SyllabusMediaValidator.java`, `CurriculumTopicDisambiguator.java`.
- `com.verifiedai.exam.application`: `CandidateProfileService.java`, `SyllabusIngestionService.java`, `CandidateProfileRepository.java`, `SyllabusVisionGateway.java`.
- `com.verifiedai.exam.events`: `CandidateGoalUpdatedEvent.java`.
- `com.verifiedai.exam.infrastructure`: `CandidateProfileJdbcRepository.java`, `DeterministicSyllabusVisionGateway.java`, `GeminiSyllabusVisionGateway.java`, `ExamPrivacyLifecycleContributor.java`.
- `com.verifiedai.exam.api`: `CandidateProfileController.java`.
- `com.verifiedai.studyplan.rebalance`: `CandidateGoalRebalanceListener.java`.

## 6. Python Math Verifier Impact
- None (`100%` JVM backend parsing, curriculum disambiguation, and profile management).

## 7. Database Migration & Persistence Contract (`V055`)
- **Migration:** `services/api/src/main/resources/db/migration/platform/V055__candidate_profiles_accommodations_and_syllabus_ingestion.sql`
- **Tables:**
  - `candidate_profiles` (`profile_id`, `user_id` FK `users(id) ON DELETE CASCADE`, `active_exam_definition_id`, composite FK `(active_exam_code, pinned_exam_version)` to `exam_definitions`, `active_target_id` FK `student_exam_targets(target_id) ON DELETE SET NULL`, `target_date`, `target_score`, `daily_study_minutes`, `weekly_study_minutes`, `accommodations_json`, `adjusted_sections_json`, `feasibility_status`, `training_eligible = FALSE`, `idempotency_key`)
  - `candidate_profile_horizon_history` (append-only audit snapshots, `training_eligible = FALSE`)
  - `candidate_syllabus_uploads` (`upload_id`, `user_id`, `image_hash`, `mime_type`, `byte_size`, `status`, `parsed_topics_json`, `llm_tokens_used`, `training_eligible = FALSE`, `expires_at` = +30 days, `confirmed_at`)

## 8. REST API Contracts (`/v1/candidate/**` & `/api/v1/candidate/**`)
- `PUT /v1/candidate/profile` & `POST /v1/candidate/profile` (upsert candidate profile + horizon history + domain event)
- `GET /v1/candidate/profile`
- `GET /v1/candidate/profile/history`
- `POST /v1/candidate/syllabus-ingest` (`multipart/form-data` or `application/json`)
- `GET /v1/candidate/syllabus-uploads/{uploadId}`
- `POST /v1/candidate/syllabus-uploads/{uploadId}/confirm`
- All Draft 2020-12 JSON schemas under `packages/schemas/exam/v1/` and OpenAPI 3.1 contracts mirrored byte-identically between `packages/contracts/openapi/public-api.yaml` and `packages/openapi/verified-ai-core-api.yaml`.

## 9. iOS (`apps/ios/VerifiedAI/`) & Future Android Scope
- `Features/Exam/Domain/ExamModels.swift` (Sprint 9.2 DTOs/accommodations/feasibility/syllabus types)
- `Features/Exam/Data/ExamAPIService.swift` (`multipart/form-data` & JSON client + `FixtureExamAPIService`) & `ExamOfflineSQLiteStore.swift` (`cached_candidate_profiles`, `cached_syllabus_uploads`, `purgeUserData`)
- `Features/Exam/Presentation/{CandidateProfileViewModel,CandidateGoalSetupView,SyllabusSnapToPlanSheet}.swift` wired via `AppDependencies`, `RootView`, `HomePlaceholderView`, `CoachHubView`, and `ExamCatalogAndTargetView` with complete EN/TR `Localizable.xcstrings` strings.

## 10. AI Capability & Unit Economics Guardrail
- `DeterministicSyllabusVisionGateway` is active by default (`verifiedai.exam.syllabus-live-ai-enabled: false`) at `$0.00` LLM token cost; `GeminiSyllabusVisionGateway` (isolated via `RestClient` + Jackson 3 without forbidden SDK imports) activates only when explicitly opted in, still subject to SHA-256 hash cache deduplication and the $\le 3$ unique uploads per UTC month quota (`429 SYLLABUS_QUOTA_EXCEEDED`).

## 11. Privacy, Safety & Compliance
- Purged on account deletion and exported on DSAR via `ExamPrivacyLifecycleContributor` (`category() = "exam"`), without exposing raw file bytes or stigmatizing copy.
- `candidate_syllabus_uploads` enforces 30-day retention (`expires_at`) via scheduled and callable `purgeExpiredSyllabusArtifacts(Instant)`, and DB CHECK constraints forbid `training_eligible != FALSE`.

## 12. Telemetry & Observability
- Emits Micrometer counters `candidate_profile_upserted_total`, `candidate_goal_horizon_updated_total`, `syllabus_upload_received_total`, `syllabus_upload_dedup_cache_hit_total`, `syllabus_upload_quota_rejected_total`, `syllabus_auto_confirmed_total`, `syllabus_disambiguation_required_total`, and timer `syllabus_ingestion_latency_seconds`.

## 13. Feature Flags & Rollback Plan
- `verifiedai.exam.candidate-profile-enabled: false`
- `verifiedai.exam.syllabus-ingestion-enabled: false`
- `verifiedai.exam.syllabus-live-ai-enabled: false`
- `verifiedai.exam.syllabus-monthly-quota: 3`, `syllabus-retention-days: 30`, `syllabus-similarity-threshold: 0.70`

## 14. Automated Test Matrix (Categories A–F)
- Backend unit, WebMvc, SQL seed consistency, privacy lifecycle, StudyPlan rebalance listener, and Spring Modulith / AI provider boundary tests (`CandidateProfileServiceTest`, `SyllabusIngestionServiceTest`, `CandidateProfileControllerWebMvcTest`, `V055MigrationSqlConsistencyTest`, `ExamPrivacyLifecycleContributorTest`, `ModularityTest`, `ModulithArchitectureTest`, `AiProviderBoundaryTest`, `RepositoryHierarchyTest`, plus `ExamMigrationV055IntegrationTest` for Docker PostgreSQL Testcontainers).
- iOS `CandidateProfileViewModelTests` & `ExamCatalogUITests` + Draft 2020-12 JSON schema / negative mutation / localization gates.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_9_2_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not replace manual date overrides; student can always edit parsed dates or disambiguated skill mappings.

## 17. Exit Gate & Downstream Consumers
- Unlocks `Sprint 9.3` and feeds `Sprint 8.4`/`8.5` daily mission and rebalancing consumers.

## 18. Canonical Cross-References
- `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md`
- `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md`
- `sprints/phase09_exam_and_assessment_platform/SPRINT_9.2_EXECUTION_REPORT.md`
- `sprints/phase09_exam_and_assessment_platform/SPRINT_9.2_FILE_MANIFEST.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
