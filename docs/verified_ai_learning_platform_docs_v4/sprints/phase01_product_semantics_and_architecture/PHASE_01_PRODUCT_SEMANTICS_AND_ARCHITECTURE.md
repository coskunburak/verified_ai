# Phase 1 — Product Semantics & Architecture Baseline
## Phase objective
This phase groups the production delivery units required for **Product Semantics & Architecture Baseline**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_1.1` — **Product Charter, Outcomes, and Release Success Metrics** — Freeze the initial product promise, target learner, measurable outcomes, non-goals, and launch success criteria before implementation creates accidental product commitments.
- `SPRINT_1.2` — **Ubiquitous Language and Non-Negotiable Domain Invariants** — Turn product language into implementable semantics and ensure every engineer and agent uses the same definitions for Problem, Attempt, Solution, Verification, Mistake, Mastery, StudyPlan, and Entitlement.
- `SPRINT_1.3` — **Curriculum, Topic, Skill, and Prerequisite Ontology** — Create stable curriculum identifiers and prerequisite relationships that all future mastery, recommendation, exam, and analytics systems depend on.
- `SPRINT_1.4` — **System Context, Runtime Boundaries, and Deployment Baseline** — Define the iOS, Spring Boot, PostgreSQL, Redis, object storage, AI provider, and internal math-verifier boundaries and trust relationships.
- `SPRINT_1.5` — **API, Contract, Error, and Idempotency Baseline** — Define public API conventions, asynchronous solve semantics, Problem Details errors, versioning, idempotency, pagination, and correlation identifiers.
- `SPRINT_1.6` — **Security, Privacy, Data Classification, and Threat Model Baseline** — Identify protected student data, attack surfaces, retention classes, trust boundaries, and minimum security controls before writing production flows.
- `SPRINT_1.7` — **Engineering Standards, Dependency Policy, and Repository Governance** — Establish code quality, dependency admission, branching, documentation, code-review, generated-code, and AI-agent rules.
- `SPRINT_1.8` — **Production Delivery Map, Quality Gates, and Phase Exit Criteria** — Convert the architecture into an executable release sequence with explicit MVP, Production V1, beta, launch, and post-launch gates.

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

**Semantic and decision foundation**

- Turn product assumptions into versioned source-of-truth contracts before implementation.
- Explicitly encode API-first/progressively-proprietary AI as a strategic constraint.
- Define measurable quality, cost, privacy and release gates so later teams/agents cannot improvise them.

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
