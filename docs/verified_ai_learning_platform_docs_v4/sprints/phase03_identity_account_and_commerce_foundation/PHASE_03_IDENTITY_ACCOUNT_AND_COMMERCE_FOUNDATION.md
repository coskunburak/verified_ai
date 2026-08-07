# Phase 3 — Identity, Account & Commerce Foundation
## Phase objective
This phase groups the production delivery units required for **Identity, Account & Commerce Foundation**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_3.1` — **Sign in with Apple End-to-End Authentication** — Implement native Apple authentication and backend token verification with replay-safe nonce handling and account linking semantics.
- `SPRINT_3.2` — **Backend Sessions, Access Tokens, Refresh Rotation, and Revocation** — Implement server-authoritative sessions with short-lived access tokens, rotating refresh-token families, reuse detection, logout, and device-session revocation.
- `SPRINT_3.3` — **Learner Profile and Production Onboarding** — Capture only the learner attributes needed for personalization, education level, locale, goals, daily study availability, and onboarding completion.
- `SPRINT_3.4` — **Entitlement Domain and Server-Authoritative Access Policy** — Model Free, Pro, Pro+ and future feature entitlements independently from client UI and App Store presentation.
- `SPRINT_3.5` — **StoreKit 2 Product Loading, Purchase, Restore, and Local UX** — Implement resilient client-side purchase flows, transaction observation, restore, interrupted purchase recovery, and transparent paywall states.
- `SPRINT_3.6` — **App Store Server API and Server Notifications V2** — Make backend billing state authoritative through validated App Store transactions and asynchronous subscription notifications.
- `SPRINT_3.7` — **Account, Privacy Controls, Data Export, and Deletion Workflow** — Provide complete account settings, session management, export requests, retention-aware deletion, and user-visible privacy controls.
- `SPRINT_3.8` — **Authentication Abuse, Rate Limits, Audit, and Security Hardening** — Harden identity and billing boundaries against brute force, token replay, purchase spoofing, abusive clients, and privileged-support misuse.

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

**Identity, account and commerce**

- Make server authority and entitlement enforcement compatible with variable AI COGS.
- Protect account/student data from accidental training eligibility.
- Ensure quotas/fair-use can be enforced without client trust.

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
