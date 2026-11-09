# Product and Technical Roadmap

## Roadmap Naming Clarification

This document describes product maturity milestones. The implementation execution program is governed by `sprints/00_SPRINT_MASTER_PLAN.md`, where Phase 1 means Product Semantics & Architecture Baseline and Phase 2 means Repository & Platform Foundation.

The currently documented implementation phases are the initial production program. They are not the complete lifetime roadmap of the product. Program, phase, sprint, backlog, and release-train semantics are governed by `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md` and `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md`.

Production V1 scope coverage is governed by `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`. Requirement-level traceability is governed by `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`.

When planning delivery, use these product-release labels to avoid confusing commercial scope with sprint numbering:

| Product-release label | Meaning | Relationship to sprint program |
|---|---|---|
| MVP | Earliest useful product slice that proves trusted math solving value for a narrow audience. | Built across later implementation phases after Phase 1/2 foundations. |
| Production V1 | Commercial iOS-first mathematics learning product with verified solving, mistake intelligence, mastery, subscriptions, privacy, operations, and launch hardening. | Requires multiple implementation phases through launch hardening. |
| V1.5 | Learning-depth expansion after V1 trust, retention, and economics are measured. | Post-launch or late-roadmap expansion. |
| V2 | Exam/course platform expansion. | Future product expansion after core learner value is validated. |
| Future | Optional breadth/platform/ML expansion. | Includes Android/web, teacher/parent features, additional subjects, and conditional Phase 13 proprietary ML. |

## Phase 0 — Feasibility

Goal: prove parse → solve → deterministic verify.

Build:
- typed input,
- basic image parse,
- algebra/derivative verifier,
- one solver route,
- evaluation harness.

Exit: reliable verification on the explicitly supported narrow set.

## Phase 1 — MVP

- Sign in with Apple,
- onboarding,
- camera/gallery,
- parse + user correction,
- primary solution,
- basic independent secondary solve,
- verification,
- step explanation,
- history,
- baseline telemetry.

Exit: small beta can complete useful solves.

## Phase 2 — Production V1

- premium design system,
- durable async jobs,
- StoreKit subscription,
- mistake diagnosis,
- mistake book,
- mastery v1,
- tutor mode,
- rate limits,
- admin traces,
- privacy/delete flows,
- CI/CD,
- golden dataset release gates.

Subject remains intentionally narrow.

## Phase 3 — V1.5 Learning System

- adaptive daily plan,
- knowledge graph UI,
- spaced repetition,
- more math domains,
- wider verification coverage,
- weekly reports.

## Phase 4 — V2 Exam Platform

- exam definitions,
- mock exams,
- readiness,
- target-date planner,
- PDF/course import,
- interactive visual lessons.

## Phase 5 — Expansion

Potential:
- physics,
- Android/web,
- teacher tools,
- parent summaries,
- institutional/B2B.

Only after core learner value, trust and economics are validated.

## Sequencing principle

Never expand breadth at the expense of verification quality and learning intelligence.

## Production V1 completeness rule

Production V1 is not complete because a fixed number of phases has elapsed. It is complete only when:

- every `V1 Required = Yes` row in `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` is complete or has an approved release exception;
- every high-impact capability maps to requirement IDs in `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`;
- the release train gates in `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md` are satisfied;
- no non-negotiable domain, security, privacy, billing, or verification invariant is violated.

Post-launch Phase 14+ work should be added only through the expansion policy in `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md`.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Revised AI evolution roadmap

### Production V1
External provider-neutral APIs + deterministic verification + full cost telemetry. No proprietary solver training.

### Post-launch optimization
Reduce unnecessary calls, compress context, calibrate conditional secondary solving, and route bounded tasks to cheaper approved models.

### Proprietary ML phase (conditional)
Only after production scale/data/economics gates:

1. governed training-data pipeline;
2. skill classifier;
3. mistake classifier;
4. difficulty predictor;
5. mastery/recommendation model candidates;
6. optional specialized math-model experiment;
7. optional self-hosted inference if TCO and quality are superior.

External frontier APIs remain fallback for hard-tail problems.
<!-- HYBRID_AI_STRATEGY_V3:END -->

<!-- FOCUSED_LAUNCH_V6:START -->
## V6 Focused Commercial Wedge & 30-Day Go/No-Go Launch Gate (2026-10-31)

Per `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` and `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`, execution of Phases 8, 9, 10 (non-essential growth/admin slices), 12, and 13 is frozen until the **Focused Commercial Wedge** is shipped to at least 50 target learners on TestFlight:

1. **Week 1 (Live Cloud AI Activation):** Wire `Gemini 2.5 Flash` (`VISION_PARSE`) and `GPT-4.1-mini` / `o4-mini` (`PRIMARY_SOLVE` / `SECONDARY_SOLVE`) behind existing ports (`adr/ADR-008_MANAGED_CLOUD_API_ROUTING_AND_QWEN_4B_DE_PROMOTION.md`).
2. **Week 2 (Algebraic Completeness & 2D Math UX):** Upgrade `CanonicalMathDocument` to `canonical_v2` (`FiniteSet`, `Interval`, `DomainConstraintGuard` per `adr/ADR-009_CANONICAL_SOLUTION_SET_ALGEBRA_AND_DOMAIN_GUARD.md`) and integrate native 2D mathematical rendering (`SwiftMath` per `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`).
3. **Week 3 (Signature Differentiator — `Analyze My Work`):** Ship handwritten student attempt diagnosis (`First Wrong Step` pinpointing via SymPy step-equivalence + concrete counterexamples + Counterfactual Twin drill generation per `learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`).
4. **Week 4 (50-Student TestFlight Beta & Go/No-Go Gate):** Measure Week-2 retention (`>=30%`), `VERIFIED` yield (`>=85%`), and zero false `VERIFIED` labels before unlocking full Phase 7–11 commercial rollout.
<!-- FOCUSED_LAUNCH_V6:END -->

<!-- LEARNING_OS_ROADMAP_V7:START -->
## V7 Learning OS Strategic Expansion & Reconciled Execution Sequence (2026-11-07)

Following the 2026-11-07 repository baseline audit (`e39bafe88ea369f98c96a65497a94462dc79ef33`, migration head `V036`), the product roadmap is aligned around the **Verified AI Learning OS** (`product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md` and `roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`):

1. **Stage 0 — Baseline Verification & Commercial Wedge Gate (`ADR-011`):**
   - Execute owner acceptance for `Sprint 7.2` (`V034`), `Sprint 7.4` (`V036`), and `Sprint 6.10`.
   - Activate live cloud AI provider adapters (`Sprint 6.2B`) and execute the 150-case `verified-ai-eval-v2` benchmark + 50-student TestFlight Beta (`Sprint 11.9A`).
2. **Stage 1 — Core Learning OS Loop (Phase 7 Completion — `Sprints 7.5`–`7.10`):**
   - Complete Automatic Mistake Book & Closure Workflow (`Sprint 7.5`, `PLANNED V037`), BKT-Lite + Half-Life Decay Mastery Engine (`Sprints 7.6`–`7.7`, `PLANNED V038`), 3-Variant Progressive Twin Practice Engine (`Sprint 7.8`), and Unified Mistake Passport Dashboard UX (`Sprint 7.9`).
3. **Stage 2 — Daily 15-Minute AI Coach & Spaced Retrieval (Phase 8 — `Sprints 8.1`–`8.10`):**
   - Deliver Deterministic Next-Best-Action (`Sprint 8.1`), Spaced Repetition & 48h/7d/21d Retrieval Checks (`Sprint 8.3`, `PLANNED V039`), Daily `1-2-1` Study Plan (`Sprint 8.4`), Guilt-Free Missed-Day Rebalancing (`Sprint 8.5`), and Weekly Learning Report (`Sprint 8.9`).
4. **Stage 3 — Exam Readiness & Monetization Loop (Phases 9–10 — `Sprints 9.1`–`10.10`):**
   - Deliver YKS (`TYT`/`AYT`), `LGS`, `SAT/AP`, and `Calculus I` Exam Blueprints (`Sprint 9.1`, `PLANNED V040`), Calibrated Readiness Score (`Sprint 9.6`), Mistake-Moment Paywall (`Sprint 10.1`), and AI FinOps Budget Enforcement (`Sprint 10.3`).
5. **Stage 4 — Production Launch & Ecosystem Scale (Phases 11–12):**
   - Complete App Store Launch (`Sprints 11.1`–`11.12`) and post-launch Parent/Teacher aggregate summary views and Android/Web expansion (`Sprints 12.6`, `12.7`, `12.10`, `PLANNED V041`) only after commercial and retention gates pass (`quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`).
<!-- LEARNING_OS_ROADMAP_V7:END -->
