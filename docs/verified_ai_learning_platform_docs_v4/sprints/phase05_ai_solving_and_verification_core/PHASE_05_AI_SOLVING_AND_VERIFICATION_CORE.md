# Phase 5 — AI Solving & Verification Core
## Phase objective
This phase groups the production delivery units required for **AI Solving & Verification Core**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_5.1` — **Provider-Neutral AI Gateway and Capability Model** — Introduce AI ports/adapters so no domain module depends directly on OpenAI, Gemini, or any specific SDK.
- `SPRINT_5.2` — **Prompt Registry, Schema Governance, and Prompt Release Workflow** — Treat prompts, examples, structured schemas, metadata, and prompt versions as reviewed production artifacts.
- `SPRINT_5.3` — **Model Router, Cost Budgets, Fallback, and Reliability Policy** — Route requests by capability, difficulty, latency target, entitlement, and cost budget while preventing runaway spend.
- `SPRINT_5.4` — **Primary Solver Pipeline and Structured Solution Candidate** — Generate a schema-valid independent solution candidate with method, steps, final answer, assumptions, and model provenance.
- `SPRINT_5.5` — **Conditional Independent Secondary Solver and Blind Agreement Analysis** — Implement an independent second-solver capability that is invoked conditionally by the risk/verification policy; when invoked, compute structured agreement/disagreement signals without exposing primary reasoning.
- `SPRINT_5.6` — **Canonical Solution and Step Domain Model** — Create durable solution/step semantics that separate mathematical content, explanation text, provenance, and presentation.
- `SPRINT_5.7` — **Verification Planner and Verification Method Selection** — Choose deterministic and independent checks based on problem type instead of applying one generic validator.
- `SPRINT_5.8` — **Arithmetic, Algebra, and Equation Verification** — Implement deterministic checks for arithmetic, simplification, equations, polynomial roots, substitutions, and equivalence.
- `SPRINT_5.9` — **Calculus Verification: Limits, Derivatives, and Basic Integrals** — Implement safe deterministic calculus checks, including derivative re-evaluation of antiderivatives and bounded numerical fallback.
- `SPRINT_5.10` — **Numerical, Symbolic, and Equivalence Composite Verification** — Combine exact symbolic checks, random-point evaluation, tolerance policy, domain restrictions, and contradiction detection.
- `SPRINT_5.11` — **Arbitration, Uncertainty, Retry, and Human-Honest Verification Policy** — Resolve solver disagreement without hiding uncertainty; only the verification policy may assign VERIFIED.
- `SPRINT_5.12` — **Golden Dataset, AI Evaluation, Cost Regression, and Model Release Gate** — Block model/prompt changes that regress correctness, parse accuracy, uncertainty honesty, latency, or unit economics beyond approved thresholds.

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

**AI solving and verification**

- Implement provider-neutral routing, cheap-by-default inference and conditional escalation.
- Prefer deterministic verification over LLM judging wherever supported.
- Measure quality, latency and cost per stage so model replacement can be evidence-driven.

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
