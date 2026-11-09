# Phase 8 — Adaptive Learning & Study Planning
## Phase objective
This phase groups the production delivery units required for **Adaptive Learning & Study Planning**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
## Phase-wide quality rules
- Preserve all domain invariants and accepted ADRs.
- Update canonical documentation when semantics or contracts change.
- Add automated tests at the cheapest reliable layer.
- Add observability before relying on a behavior in production.
- Never expose AI provider credentials or internal verifier endpoints to the mobile client.
- All user-visible failure states require a recovery path.
- All durable schema changes use reviewed Flyway migrations.
- All AI behavior changes require traceable prompt/model versions.
## Sprint sequence
- `SPRINT_8.1` — **Next-Best-Action Recommendation Engine** — Select what the learner should do next using weak skills, prerequisites, exam urgency, spacing, recent attempts, and available time.
- `SPRINT_8.2` — **Adaptive Practice Question Selection and Generation** — Construct practice sets with controlled difficulty, skill coverage, old-mistake similarity, and quality safeguards.
- `SPRINT_8.3` — **Spaced Repetition Scheduling for Skills and Mistakes** — Schedule revisits using explicit review states and mastery confidence instead of naïve calendar reminders.
- `SPRINT_8.4` — **Daily Study Plan Generation** — Create time-bounded daily plans that map learner goals and exam horizon into concrete sessions.
- `SPRINT_8.5` — **Missed-Day Recovery and Study Plan Rebalancing** — Recompute future work after missed sessions, overload, or rapid mastery changes without punishing the learner.
- `SPRINT_8.6` — **AI Micro-Lessons Triggered by Diagnosed Weaknesses** — Generate short, bounded concept lessons only when the learning state indicates a clear misconception or knowledge gap.
- `SPRINT_8.7` — **Study Session Orchestration and Resume Semantics** — Create deterministic session progress, interruption recovery, offline-safe checkpoints, and completion semantics.
- `SPRINT_8.8` — **Goals, Streaks, Achievements, and Non-Manipulative Gamification** — Add motivation systems that support learning outcomes without dark patterns or encouraging meaningless engagement.
- `SPRINT_8.9` — **Weekly Learning Report and Explainable Recommendations** — Summarize solved questions, mastery movement, mistake patterns, time, and next focus with evidence-backed explanations.
- `SPRINT_8.10` — **Personalization Calibration, Cold Start, and Recommendation Quality Gates** — Ensure the planner behaves safely with sparse data, conflicting goals, inactive users, and inaccurate inferred preferences.

## Phase exit gate
The phase is complete only when every sprint-specific acceptance criterion is satisfied, phase-level integration tests pass, documentation is current, no unresolved P0/P1 defects remain in the phase scope, and the next phase can consume the delivered contracts without relying on undocumented assumptions.

## Required review at phase close
- Architecture drift review.
- Security/privacy delta review.
- AI cost/quality delta review when applicable.
- Database migration and data-retention review.
- iOS accessibility/localization review for user-visible work.
- Observability and supportability review.
- Backlog reprioritization using evidence collected during the phase.

<!-- PHASE_PRODUCTION_V3:START -->
# Phase Production Contract v3

## Phase objective alignment

**Adaptive learning and planning**

- Start with explainable rules/heuristics and collect evidence before learned recommenders.
- Measure learning outcomes, not engagement-only metrics.
- Create counterfactual/offline evaluation hooks for future recommendation models.

## Hybrid-AI rule

This phase must preserve the accepted API-first/provider-neutral architecture. Proprietary training/self-hosting is forbidden before Phase 13 entry gates, except for isolated evaluation research that never affects production and uses eligible data. All AI-affecting work must measure quality, latency and cost together.

## Phase exit evidence

The phase exit review must include:

- completed sprint acceptance evidence;
- unresolved P0/P1 risks;
- architecture/documentation drift check;
- security/privacy review for changed trust boundaries;
- AI quality/cost trend if AI routes were touched;
- migration/rollback readiness;
- next-phase dependency statement.
<!-- PHASE_PRODUCTION_V3:END -->

<!-- PHASE_PRODUCTION_V6:START -->
# Phase Production Execution Contract v6 (Spaced Mistake Repair Queue & Prerequisite Root-Cause Backtracker)

## 1. Entry Gate (`ADR-011`)
- Phase 8 execution is strictly gated on passing the **30-Day Focused Commercial Wedge (`Sprint 11.9A`)** with `>= 30%` Week-2 retention across the 50-student TestFlight cohort.

## 2. Signature Capabilities & Mathematical Logic (`learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`)
- **Spaced-Repetition Mistake Repair Queue (`SPRINT_8.3` / `F05`):**
  - Driven directly by `learner_skill_mastery.next_review_due_at` and `half_life_days` (`V026`). When effective retention $P_{\text{eff}}(t) = 0.10 + (P(L_n) - 0.10)\cdot 2^{-\Delta t / H_n}$ drops below `0.70`, the skill is automatically queued into the learner's **Daily 5-Minute Trap & Mistake Repair Session**.
- **Prerequisite Root-Cause Backtracker (`SPRINT_8.2` / `F26`):**
  - When a student fails a composite topic (e.g., AYT Derivative Maxima/Minima or Definite Integrals) twice due to an underlying foundational weakness (e.g., sign table analysis of quadratic inequalities or rational exponent rules), the planner dynamically inserts a 2-question foundational micro-drill before returning to the advanced topic.
- **Zero-Token Deterministic Planning:**
  - Study plan generation, daily queue ranking, and spaced review scheduling must be **100% deterministic Java domain logic** (`0` LLM tokens per plan refresh). LLMs are invoked only when generating new `SymPy`-verified practice or twin items that are not already in the verified item bank.
<!-- PHASE_PRODUCTION_V6:END -->

<!-- PHASE_PRODUCTION_V7_LEARNING_OS:START -->
# Phase Production Execution Contract v7 — Verified AI Learning OS Personal Math Coach (`2026-11-07`)

## 1. Entry Gate & Migration Lineage (`ADR-011`)
- Phase 8 production rollout remains **BLOCKED_BY_ENTRY_GATE (`ADR-011`)** until the Focused Commercial Wedge (`Sprint 6.2B` + `Sprint 11.9A` 50-student TestFlight cohort) records an explicit owner `GO` decision (`roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`).
- Database references to `V026` in v6 notes correspond in actual repository lineage to **`V031__add_pedagogical_diagnosis_twin_drills_and_mastery.sql`** (`learner_skill_mastery`, `counterfactual_twin_drills`) and **`PLANNED V037–V045`** (`architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`).
- All backend implementations belong to `com.verifiedai.studyplan`, `com.verifiedai.tutoring`, `com.verifiedai.mastery`, and `com.verifiedai.analytics`; iOS views reside under `apps/ios/VerifiedAI/Features/`.

## 2. Learning OS Canonical Specifications Governed by Phase 8
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md` (`SPRINT_8.2` 8-family structural trap invariant verifier & 3-variant ladder A/B/C)
- `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md` (`SPRINT_8.1`, `8.3`, `8.4`, `8.6`, `8.9` — Daily 15-Minute Coach Hub replacing `HomePlaceholderView.swift`, 90s Micro-Lessons, and 1-Click Weekly Tutor/Parent PDF Share Packet)
<!-- PHASE_PRODUCTION_V7_LEARNING_OS:END -->
