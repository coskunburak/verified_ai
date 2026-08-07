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
