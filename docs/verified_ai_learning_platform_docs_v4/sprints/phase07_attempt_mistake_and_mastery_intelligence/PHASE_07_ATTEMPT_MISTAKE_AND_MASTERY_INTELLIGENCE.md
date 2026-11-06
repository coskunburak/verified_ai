# Phase 7 — Attempts, Mistakes & Mastery Intelligence
## Phase objective
This phase groups the production delivery units required for **Attempts, Mistakes & Mastery Intelligence**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_7.1` — **Attempt Domain and Learner Answer Submission** — Persist learner work independently from reference solutions and preserve every meaningful attempt as learning evidence.
- `SPRINT_7.2` — **Attempt Step Extraction and Structured Work Representation** — Convert typed/handwritten work into reviewable step structures while preserving source evidence and uncertainty.
- `SPRINT_7.3` — **Attempt Evaluation and Step-Level Correctness** — Evaluate final and intermediate steps against verified mathematical constraints without overwriting the learner attempt.
- `SPRINT_7.4` — **Mistake Taxonomy and Mistake Classification Pipeline** — Classify concept, formula, algebra, sign, calculation, interpretation, unit, and incomplete-solution mistakes with confidence and provenance.
- `SPRINT_7.5` — **Automatic Mistake Book and Review Workflows** — Create a durable, searchable, grouped mistake history with review states and practice-again actions.
- `SPRINT_7.6` — **Mastery Model V1 and Deterministic Update Policy** — Implement skill-level mastery using accuracy, difficulty, hints, attempt quality, and recency while avoiding opaque fake precision.
- `SPRINT_7.7` — **Mastery Confidence, History, Decay, and Reassessment** — Track uncertainty and temporal change in mastery so one lucky answer cannot permanently inflate skill state.
- `SPRINT_7.8` — **Knowledge Graph Projection and Learning State APIs** — Expose learner skill state, prerequisites, weak areas, and mastery relationships through stable read models.
- `SPRINT_7.9` — **Mastery and Mistake Dashboard UX** — Build understandable learner-facing visualizations that explain strengths, weaknesses, recurring mistake types, and recent movement.
- `SPRINT_7.10` — **Learning Intelligence Evaluation and Bias/Calibration Hardening** — Validate mastery updates and mistake classification against curated cases and guard against systematic misclassification or misleading scores.

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

**Attempts, mistakes and mastery**

- Preserve immutable learner evidence and explainable deterministic baselines.
- Design stable task contracts that can later support small proprietary classifiers.
- Separate user correction, model suggestion and verified labels in provenance.

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
# Phase Production Execution Contract v6 (Mistake Passport, 18-Class Taxonomy & Deterministic BKT-Lite)

## 1. Commercial Wedge Fast-Track (`SPRINT_7.0A`) vs. Full Phase 7 Expansion
- **Fast-Tracked into the 30-Day Commercial Wedge (`SPRINT_7.0A` in `sprints/02_FOCUSED_COMMERCIAL_WEDGE_SPRINT_PROGRAM.md`):**
  - Flyway migration `V026__create_mistake_passport_twin_drills_and_mastery_v1.sql` (`learner_mistake_events`, `learner_skill_mastery`, `counterfactual_twin_drills`).
  - The 18-class deterministic mathematical mistake taxonomy (`ALG_SIGN_DISTRIBUTION_ERROR`, `ALG_FRESHMAN_DREAM_POWER`, `ALG_FRESHMAN_DREAM_RADICAL`, `ALG_RATIONAL_DENOM_ZERO_IGNORED`, `ALG_DIVISION_BY_VARIABLE_ROOT_LOSS`, `ALG_INEQUALITY_NEGATIVE_MULTIPLY_NO_FLIP`, `ALG_ABSOLUTE_VALUE_SINGLE_BRANCH_ONLY`, `ALG_QUADRATIC_FORMULA_DISCRIMINANT_ARITHMETIC`, `ALG_LOG_DOMAIN_VIOLATION`, `CALC_CHAIN_RULE_INNER_DERIVATIVE_OMITTED`, `CALC_PRODUCT_RULE_NAIVE_MULTIPLICATION`, `CALC_INDEFINITE_INTEGRAL_MISSING_CONSTANT_C`, etc.) documented in `learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`.
  - Deterministic **`BKT-Lite` + Exponential Forgetting Curve** (`BktLiteMasteryCalculator.java`) with zero LLM token overhead (`P(L_0)=0.25, P(T)=0.12, P(S)=0.10, P(G)=0.05`, half-life `H_n` doubling on verified independent solves and resetting on recurring mistakes).
  - **Personal Mistake Passport (`F05`)** (`GET /api/v1/me/mistake-passport` + `MistakePassportDashboardView.swift`) and `LearningStatePrivacyLifecycleContributor.java`.
- **Full Phase 7 Expansion (`SPRINT_7.1`–`7.10`, Unlocks After 50-Student TestFlight Gate `ADR-011`):**
  - Prerequisite DAG propagation (`SPRINT_7.5`), multi-skill mastery attribution (`SPRINT_7.7`), and deep misconception remediation paths (`SPRINT_7.8`).

## 2. Anti-Gaming Mastery Invariant
- Passively viewing a complete solution (`solution_peek_count++`) grants **`0.0` mastery increase**. A learner's `p_learned` and memory half-life `half_life_days` only advance when they submit a verified independent attempt (`Analyze My Work` -> `FULLY_CORRECT` / `ALTERNATIVE_VALID_PATH`) or solve a `SymPy`-verified Counterfactual Twin Drill (`COMPLETED_CORRECT`).
<!-- PHASE_PRODUCTION_V6:END -->

## Sprint 7.2 source handoff

Owner-authorized local source preparation delivers immutable attempt structured work, explicit OCR/review/syntax boundaries,
V034, integrated iOS review/recovery, strict contracts and an independent owner-only A–G acceptance runner. Source status is
SOURCE_COMPLETE; current-source execution remains PENDING_OWNER_TEST. This is not a passed phase exit gate or release approval.
ADR-011 and the 50-student TestFlight gate above remain external and unverified. No paid inference route or deployment was enabled.
See `SPRINT_7.2_IMPLEMENTATION_MAP.md`, `SPRINT_7.2_FUNCTIONAL_COMPLETION_MATRIX.md` and `SPRINT_7.2_EXECUTION_REPORT.md`.
