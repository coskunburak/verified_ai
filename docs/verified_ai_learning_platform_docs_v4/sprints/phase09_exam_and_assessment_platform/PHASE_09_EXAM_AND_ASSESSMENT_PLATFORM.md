# Phase 9 — Exam & Assessment Platform
## Phase objective
This phase groups the production delivery units required for **Exam & Assessment Platform**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_9.1` — **Exam Definitions, Curriculum Mapping, and Versioning** — Create stable exam models, syllabus mappings, skill weights, versions, and effective dates without hardcoding UI assumptions.
- `SPRINT_9.2` — **Learner Exam Goal Setup and Candidate Profile** — Capture exam date, target score, accommodations, available study time, and curriculum version as planning inputs.
- `SPRINT_9.3` — **Question Bank, Blueprint, and Assessment Assembly Rules** — Build reproducible assessment blueprints with topic/difficulty distribution and source provenance.
- `SPRINT_9.4` — **Timed Mock Exam Runtime** — Implement interruption-resistant timed assessments with navigation, autosave, accessibility, and clear no-hint policy.
- `SPRINT_9.5` — **Scoring Engine and Evidence Traceability** — Compute deterministic score components and retain evidence so reported scores are explainable and reproducible.
- `SPRINT_9.6` — **Exam Readiness Model and Confidence Semantics** — Estimate readiness from mastery, assessment evidence, syllabus coverage, recency, and uncertainty without claiming unsupported precision.
- `SPRINT_9.7` — **Weak-Area Prioritization After Assessments** — Convert mock-exam outcomes into high-value skill recommendations and targeted review.
- `SPRINT_9.8` — **Final Review and Pre-Exam Study Mode** — Assemble high-yield review from weak skills, old mistakes, spaced repetition needs, and remaining time.
- `SPRINT_9.9` — **Exam Analytics, Reports, and Progress Comparison** — Show score movement, coverage, timing, section weakness, and confidence across assessment history.
- `SPRINT_9.10` — **Assessment Integrity, Accessibility, Failure Recovery, and Reliability** — Harden exam mode against accidental data loss, timer inconsistencies, unfair accessibility barriers, and invalid scoring states.

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

**Exam and assessment platform**

- Keep scoring/evidence server-authoritative and deterministic where possible.
- Calibrate readiness/confidence from evidence rather than LLM self-assessment.
- Prevent model changes from silently changing exam semantics.

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
