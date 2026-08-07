# Phase 6 — Solution Experience & Tutoring
## Phase objective
This phase groups the production delivery units required for **Solution Experience & Tutoring**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_6.1` — **Production Solution Results Experience** — Deliver final answer, steps, method, state restoration, error recovery, and readable mathematical rendering without overwhelming the learner.
- `SPRINT_6.2` — **Verification Transparency and Evidence UX** — Expose what was and was not verified, why a result has its status, and when the system requires caution.
- `SPRINT_6.3` — **Explanation Depth, Beginner-to-Deep Modes, and Personalization** — Support concise, standard, deep, and beginner explanations without changing mathematical truth.
- `SPRINT_6.4` — **Tutor Session Domain and Conversation Persistence** — Create persistent, resumable tutoring sessions linked to problem, skill, learner state, and safety policy.
- `SPRINT_6.5` — **Socratic Tutor Behavior and Pedagogical State Machine** — Make Tutor Mode guide the learner with questions instead of immediately revealing answers whenever the selected policy requires productive struggle.
- `SPRINT_6.6` — **Progressive Hint System and Hint Cost Semantics** — Introduce structured hint levels, track hint use as a mastery signal, and prevent accidental full-answer leakage.
- `SPRINT_6.7` — **Analyze My Work: User Solution Capture and Step-Level Comparison** — Allow handwritten or typed learner work to be compared against verified reasoning and identify the first meaningful divergence.
- `SPRINT_6.8` — **Alternative Solution Methods and Method Comparison** — Offer legitimate alternate methods and explain tradeoffs without fabricating unnecessary variants.
- `SPRINT_6.9` — **Interactive Mathematical Visualizations and Concept Explanations** — Add graph/geometry/number-line visual tools when they improve understanding and remain deterministic or clearly labeled.
- `SPRINT_6.10` — **Solution/Tutor Accessibility, Localization, Performance, and UX Polish** — Make the core learning experience production-ready across Dynamic Type, VoiceOver, localization, reduced motion, weak networks, and older supported devices.

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

**Solution and tutoring experience**

- Present verification and uncertainty honestly while maintaining premium UX.
- Bound conversational context/cost and avoid expensive hidden calls.
- Keep tutor behavior provider-independent and testable against pedagogical policy.

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
