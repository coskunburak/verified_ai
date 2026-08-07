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
