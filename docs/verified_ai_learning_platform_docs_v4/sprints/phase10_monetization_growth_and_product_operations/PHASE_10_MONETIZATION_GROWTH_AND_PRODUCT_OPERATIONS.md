# Phase 10 — Monetization, Growth & Product Operations
## Phase objective
This phase groups the production delivery units required for **Monetization, Growth & Product Operations**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_10.1` — **Paywall Architecture, Offer Presentation, and Pricing Configuration** — Create transparent configurable offers that show value before purchase and do not encode business logic only in the client.
- `SPRINT_10.2` — **Free, Pro, Pro+ Feature Guards and Entitlement Enforcement** — Enforce premium capabilities consistently in backend and UI with clear downgrade behavior.
- `SPRINT_10.3` — **Usage Quotas, AI Budgets, Credits, and Fair-Use Enforcement** — Control costly AI operations per entitlement while preventing confusing or predatory quota behavior.
- `SPRINT_10.4` — **Notification Infrastructure and Preference Center** — Create typed notification intents, delivery tracking, opt-in preferences, quiet hours, and idempotent scheduling.
- `SPRINT_10.5` — **Lifecycle Messaging and Learning-Relevant Re-Engagement** — Use notifications only when tied to study plans, reviews, exam deadlines, or account state; avoid spam-driven engagement.
- `SPRINT_10.6` — **Analytics Event Catalog and Product Metrics Implementation** — Instrument activation, verified learning sessions, mastery outcomes, retention, conversion, latency, and cost with privacy-safe semantics.
- `SPRINT_10.7` — **Feature Flags, Remote Configuration, A/B Tests, and Kill Switches** — Enable controlled rollout and rapid shutdown of risky AI, paywall, or recommendation changes.
- `SPRINT_10.8` — **Admin and Support Console Foundations** — Give trusted operators user lookup, problem trace, subscription visibility, AI/verification evidence, and safe remediation workflows.
- `SPRINT_10.9` — **Organic Growth Loops, Sharing, Referral, and Store Optimization Hooks** — Implement non-spammy product loops such as shareable progress, referral attribution, review prompts, and deep-linkable learning artifacts where appropriate.
- `SPRINT_10.10` — **Unit Economics, AI Cost Dashboards, and Margin Safeguards** — Track revenue, model spend, cost per verified solution, heavy-user cohorts, and automated budget guardrails.

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

**Monetization, growth and operations**

- Make AI unit economics visible by tier/cohort and enforce transparent usage policies.
- Use feature flags and route controls to manage spend and quality independently.
- Never optimize margin by misrepresenting verification quality.

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
