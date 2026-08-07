# Phase 11 — Production Hardening, Beta & Launch
## Phase objective
This phase groups the production delivery units required for **Production Hardening, Beta & Launch**. It must leave the repository in a coherent, testable state and may not transfer unresolved foundational risk into later phases without an explicit recorded exception.
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
- `SPRINT_11.1` — **iOS Performance, Memory, Battery, and Network Profiling** — Profile camera, image processing, SwiftUI rendering, persistence, networking, and long tutoring sessions under production-like conditions.
- `SPRINT_11.2` — **Backend Load Testing, Concurrency, and Capacity Baseline** — Measure API, solve-job, AI orchestration, and queue behavior under realistic concurrency and burst patterns.
- `SPRINT_11.3` — **PostgreSQL Query Plans, Indexing, Pooling, and Data Growth Review** — Validate indexes, N+1 avoidance, query plans, connection-pool sizing, archiving, and expected growth paths.
- `SPRINT_11.4` — **Security Testing, Abuse Simulation, and Privilege Review** — Perform application security review covering authentication, authorization, SSRF/file handling, prompt injection, rate limits, admin access, and secret exposure.
- `SPRINT_11.5` — **Privacy, Retention, Account Deletion, and Compliance Readiness** — Verify documented data lifecycle behavior with real execution tests and App Store privacy declarations.
- `SPRINT_11.6` — **Failure Injection, AI Provider Outage, and Chaos Scenarios** — Test degraded operation for provider timeouts, object-storage failure, Redis loss, verifier failure, slow DB, and duplicate notifications.
- `SPRINT_11.7` — **Incident Response, Alerting, Runbooks, and Operational Readiness** — Ensure every launch-critical alert has an owner, severity, diagnostic path, mitigation, and rollback procedure.
- `SPRINT_11.8` — **Backup, Point-in-Time Recovery, and Disaster Recovery Test** — Prove database and critical configuration can be restored within defined objectives rather than merely assuming backups work.
- `SPRINT_11.9` — **Closed TestFlight Beta and Structured Feedback Program** — Release to controlled learners, capture qualitative/quantitative feedback, monitor AI quality, crashes, conversion, and support burden.
- `SPRINT_11.10` — **Beta Iteration, Bug Burn-Down, and Release Candidate Freeze** — Resolve launch blockers, regressions, UX confusion, high-cost AI paths, and critical reliability defects before release candidate.
- `SPRINT_11.11` — **App Store Submission, Privacy Manifest, Metadata, and Launch Readiness** — Prepare store assets, review notes, subscriptions, privacy disclosures, screenshots, support URLs, and operational launch checklist.
- `SPRINT_11.12` — **Production Launch, Progressive Rollout, War Room, and Rollback Gates** — Launch gradually with explicit technical/product stop conditions, live dashboards, rollback paths, and post-launch verification.

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

**Production hardening and launch**

- Load/chaos-test provider outages, quota exhaustion and cost anomalies.
- Validate rollback paths for model/route changes without an App Store release.
- Establish real production baselines needed before proprietary ML investment.

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
