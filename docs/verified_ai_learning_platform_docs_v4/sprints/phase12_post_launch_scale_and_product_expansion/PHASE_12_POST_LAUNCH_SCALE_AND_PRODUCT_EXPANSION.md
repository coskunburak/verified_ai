# Phase 12 — Post-Launch Scale & Product Expansion
## Phase objective
This phase groups the production delivery units required for **Post-Launch Scale & Product Expansion**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_12.1` — **Production Feedback Triage and Evidence-Driven Roadmap Reset** — Convert real support, retention, learning, revenue, and AI-quality data into a ranked post-launch backlog instead of blindly executing pre-launch assumptions.
- `SPRINT_12.2` — **AI Model, Prompt, Cache, and Cost Optimization** — Improve quality-per-dollar and latency using production traces, model routing, cache opportunities, and evaluation-gated prompt/model changes.
- `SPRINT_12.3` — **Advanced Calculus and Trigonometry Coverage** — Expand verified math capability only after new domains have deterministic/evaluation coverage and curriculum mappings.
- `SPRINT_12.4` — **Linear Algebra, Probability, and Statistics Coverage** — Add new mathematical problem families with dedicated parsing, solving, verification, mistake, and mastery semantics.
- `SPRINT_12.5` — **PDF, Lecture Note, and Course Workspace Ingestion** — Turn uploaded study materials into source-grounded course workspaces, practice, flashcards, and summaries with provenance.
- `SPRINT_12.6` — **Teacher and Classroom Mode** — Introduce B2B-capable class creation, assignments, aggregate weakness views, and teacher-safe permissions without exposing private student details unnecessarily.
- `SPRINT_12.7` — **Parent/Guardian Progress Experience** — Create carefully permissioned progress summaries for younger learners without turning the product into surveillance.
- `SPRINT_12.8` — **Additional Exam Verticals and Regional Curriculum Packs** — Expand to new exam ecosystems through versioned mappings, localized content, and market-specific quality validation.
- `SPRINT_12.9` — **Scale Thresholds, Service Extraction Criteria, and Architecture Evolution** — Define evidence-based thresholds for extracting modules or adding queues/search/data infrastructure instead of premature microservices.
- `SPRINT_12.10` — **Android, Web, and Cross-Platform Expansion Strategy** — Prepare API, contracts, design semantics, billing abstractions, and product workflows for additional clients after iOS economics and learning outcomes are validated.

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

**Post-launch optimization and expansion**

- Use production evidence to reduce calls, calibrate routing and identify bounded ML candidates.
- Do not self-host/train merely because scale exists; apply formal decision gates.
- Preserve external fallbacks while expanding math/product scope.

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
