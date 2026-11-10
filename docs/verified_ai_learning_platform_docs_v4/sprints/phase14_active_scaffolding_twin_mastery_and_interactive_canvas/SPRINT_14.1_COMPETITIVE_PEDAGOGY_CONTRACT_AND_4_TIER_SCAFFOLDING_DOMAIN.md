# Sprint 14.1 — Competitive Pedagogy Contract and 4-Tier Cognitive Scaffolding Domain

## Sprint mission

Establish the server-authoritative **4-Tier Cognitive Scaffolding Domain (`SocraticScaffoldingSession`)**, state transition rules (`TIER_1_CONCEPT` -> `TIER_2_STRATEGY` -> `TIER_3_CRITICAL_MOVE` -> `TIER_4_FULL_SOLUTION`), and additive persistence schema (`PLANNED V039`) so the platform guides learners through active retrieval rather than dumping passive answers.

This sprint is a production delivery unit. It is complete only when domain semantics, state transitions, persistence invariants, telemetry, test coverage, and canonical documentation are coherent and verified.

## Why this sprint exists

General-purpose LLMs (ChatGPT, Gemini) and video-solver wrappers (Solvi / Solvely, Gauth) immediately expose full solutions, inducing the **Illusion of Explanatory Depth (Yetkinlik İllüzyonu)**. This sprint encodes the ICAP pedagogical hierarchy (*Interactive > Constructive > Active > Passive*) directly into the backend domain layer so that no client or prompt can bypass progressive scaffolding when `ACTIVE_SCAFFOLDING` mode is active.

## Preconditions and dependencies

- Read `00_MASTER_INDEX.md`, `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`, `learning/34_TUTORING_AND_PEDAGOGICAL_BEHAVIOR.md`, and `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`.
- Confirm existing `com.verifiedai.tutoring` (`V027`) and `com.verifiedai.attempt` (`V030`–`V036`) modules remain untouched and backwards-compatible.
- Re-check `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md` and `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`.

## In scope

- `SocraticScaffoldingSession` and `SocraticScaffoldingTurn` domain models inside `com.verifiedai.tutoring`.
- Deterministic 4-tier finite state machine (`ScaffoldingTier`: `TIER_1_CONCEPT`, `TIER_2_STRATEGY`, `TIER_3_CRITICAL_MOVE`, `TIER_4_FULL_SOLUTION`).
- Epistemic assistance provenance tracking (`SOLVED_AT_TIER_1`, `SOLVED_AT_TIER_2`, `SOLVED_AT_TIER_3`, `REVEALED_TIER_4_NO_MASTERY_CREDIT`).
- Additive Flyway migration `PLANNED V039__create_socratic_scaffolding_sessions.sql`.
- Privacy lifecycle integration (`TutorSessionPrivacyLifecycleContributor` extension for export/deletion).

## Explicitly out of scope

- Modifying existing `V001`–`V038` migration files.
- Unverified LLM free-text chat without deterministic step-anchor grounding.
- Live PencilKit canvas streaming (covered in `SPRINT_14.6`–`14.8`).

## Deliverables

1. **Semantic deliverable** — `ScaffoldingTier`, `ScaffoldingSessionState`, and `ScaffoldingEpistemicOutcome` enums with strict state transition guards.
2. **Implementation deliverable** — Domain entities, repository ports, and `ScaffoldingProgressionPolicy.java` inside `services/api/src/main/java/com/verifiedai/tutoring/`.
3. **Contract deliverable** — JSON schema `packages/schemas/tutoring/socratic-scaffolding-session.v1.schema.json`.
4. **Quality deliverable** — Unit and Testcontainers persistence suites verifying illegal transition rejection and cascade deletion.
5. **Operational deliverable** — Micrometer counters `verifiedai.scaffolding.session.started` and `verifiedai.scaffolding.tier.transition`.
6. **Documentation deliverable** — Synchronized updates with `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`.

---

<!-- LEARNING_OS_PRODUCTION_SPEC_V1:START -->
# Learning OS Production Implementation Specification v1 (`2026-11-11`)

## 1. Sprint Metadata & Status
- **Sprint ID:** `SPRINT_14.1` (Fast-track bridge alias: `SPRINT_6.5B-Part1`)
- **Canonical Title:** Competitive Pedagogy Contract and 4-Tier Cognitive Scaffolding Domain
- **Status:** `PLANNED` (`ADDITIVE_NON_BREAKING`)
- **Primary Spec Reference:** `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`

## 2. Strategic Learning OS Objective & User Value
Transforms passive solution viewing into a structured 4-stage Socratic discovery loop. Each tier unlocks only after the learner either submits a micro-attempt (e.g., selecting or typing the leading term $a \cdot 2^n x^{n+1}$) or explicitly requests the next scaffolding rung with transparent mastery-cap consequences.

## 3. Entry Gate & Preconditions
- Existing `com.verifiedai.tutoring` (`Sprint 6.4`–`6.6`) local baseline present.

## 4. Domain Invariants & Mathematical Rules
1. **Strict Monotonic Tier Progression (`INV-SCAF-001`):** A session starts at `TIER_1_CONCEPT`. Transitions must follow `TIER_1_CONCEPT -> TIER_2_STRATEGY -> TIER_3_CRITICAL_MOVE -> TIER_4_FULL_SOLUTION` unless the learner solves the step early (`COMPLETED_BY_LEARNER`) or triggers an explicit `EXAM_TRIAGE_FAST_REVEAL` override (which permanently marks the session `SOLUTION_REVEALED_ASSISTED` with `0.0` mastery gain).
2. **Zero Answer Leakage in Tiers 1–3 (`INV-SCAF-002`):** No payload emitted in `TIER_1_CONCEPT`, `TIER_2_STRATEGY`, or `TIER_3_CRITICAL_MOVE` may contain the final canonical answer string or the correct multiple-choice option letter (`A`–`E`). Deterministic token-redaction guard enforces this before serialization.
3. **Epistemic Mastery Ceiling (`INV-SCAF-003`):**
   - Solved at `TIER_1_CONCEPT`: up to `100%` of eligible step mastery credit (`1.0x` weight).
   - Solved at `TIER_2_STRATEGY`: capped at `0.78` mastery ceiling (`0.70x` weight).
   - Solved at `TIER_3_CRITICAL_MOVE`: capped at `0.65` mastery ceiling (`0.40x` weight).
   - Reached `TIER_4_FULL_SOLUTION`: `0.0` mastery credit; requires passing an Isomorphic Twin (`SPRINT_14.4`) to earn mastery.

## 5. Owning Spring Modulith Backend Modules & Classes
- `services/api/src/main/java/com/verifiedai/tutoring/domain/ScaffoldingTier.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/SocraticScaffoldingSession.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/SocraticScaffoldingTurn.java`
- `services/api/src/main/java/com/verifiedai/tutoring/application/ScaffoldingProgressionPolicy.java`
- `services/api/src/main/java/com/verifiedai/tutoring/application/FinalAnswerLeakageGuard.java`

## 6. Python Math Verifier Impact (`services/math-verifier/`)
- None in `SPRINT_14.1` (verifier breakpoint decomposition is wired in `SPRINT_14.2`).

## 7. Database Migration & Persistence Contract (`PLANNED V039`)
- **Migration File:** `services/api/src/main/resources/db/migration/platform/V039__create_socratic_scaffolding_sessions.sql` *(PLANNED — allocated sequentially after `V038`)*
- **Tables:**
  - `socratic_scaffolding_sessions` (`id UUID PK`, `user_id UUID NOT NULL`, `problem_session_id UUID NOT NULL`, `attempt_id UUID NULL`, `current_tier VARCHAR(32) NOT NULL`, `max_tier_reached VARCHAR(32) NOT NULL`, `resolution_state VARCHAR(32) NOT NULL`, `epistemic_outcome VARCHAR(40) NOT NULL`, `student_interventions_count INT NOT NULL DEFAULT 0`, `training_eligible BOOLEAN NOT NULL DEFAULT FALSE`, `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`).
  - `socratic_scaffolding_turns` (`id UUID PK`, `scaffolding_session_id UUID NOT NULL REFERENCES socratic_scaffolding_sessions(id) ON DELETE CASCADE`, `turn_index INT NOT NULL`, `tier VARCHAR(32) NOT NULL`, `target_step_index INT NOT NULL`, `prompt_markdown TEXT NOT NULL`, `micro_check_choices_json JSONB`, `learner_response_raw TEXT`, `step_evaluation_status VARCHAR(32)`, `created_at TIMESTAMPTZ`).

## 8. REST API Contracts
- Internal domain & persistence foundation in `14.1`; public REST endpoints exposed in `SPRINT_14.2`.

## 9. iOS (`apps/ios/VerifiedAI/`) Scope
- Domain models `ScaffoldingTier.swift` and `SocraticScaffoldingSessionDTO.swift` under `apps/ios/VerifiedAI/Features/Tutor/Domain/`.

## 10. AI Capability & Unit Economics Guardrail
- `100%` deterministic state machine and leakage guard (`$0.00` inference cost).

## 11. Privacy, Safety & Compliance
- `training_eligible = false` enforced by DB constraint and domain constructor (`ADR-006`).
- Registered in `TutorSessionPrivacyLifecycleContributor` for full GDPR/KVKK account erasure (`ON DELETE CASCADE`).

## 12. Telemetry & Observability
- `verifiedai.scaffolding.session.created{initial_tier}`
- `verifiedai.scaffolding.leakage_guard.blocked_count`

## 13. Feature Flags & Rollback Plan
- Feature flag: `verifiedai.tutoring.active-scaffolding-enabled: false` (default off in production until `SPRINT_14.2` acceptance).

## 14. Automated Test Matrix (Categories A–F)
- **Gate A (Domain):** `ScaffoldingProgressionPolicyTest` (verifies all legal/illegal tier transitions and mastery weight ceilings).
- **Gate B (Leakage Guard):** `FinalAnswerLeakageGuardTest` (verifies that any tier 1–3 prompt containing final root/option is rejected fail-closed).
- **Gate C (Persistence):** `SocraticScaffoldingRepositoryIntegrationTest` via Testcontainers PostgreSQL.

## 15. Owner-Only Acceptance Command
- `bash scripts/quality/sprint_14_1_owner_acceptance.sh --full`

## 16. Explicit Non-Goals
- Does not alter existing `TutorSessionService` endpoints used by `Sprint 6.4`–`6.6`.

## 17. Exit Gate & Downstream Consumers
- Unlocks `SPRINT_14.2` (Step-Breakpoint Detection & Scaffolding REST/iOS Experience).

## 18. Canonical Cross-References
- `learning/93_ACTIVE_SCAFFOLDING_TWIN_VERIFICATION_AND_INTERACTIVE_CANVAS_SPECIFICATION.md`
- `learning/34_TUTORING_AND_PEDAGOGICAL_BEHAVIOR.md`
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
<!-- LEARNING_OS_PRODUCTION_SPEC_V1:END -->
