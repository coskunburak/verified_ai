# Product Program Structure

## Purpose

This document clarifies the relationship between product programs, phases, sprints, releases, and backlog items. It prevents a common planning error: treating the currently documented Phase 1-13 sequence as the entire lifetime of the product.

Phase 1-13 is the initial production program. It is broad enough to reach Production V1, launch hardening, early post-launch expansion, and conditional proprietary-ML exploration. It is not a promise that the product ends at Phase 13.

## Planning Levels

| Level | Name | Meaning | Change frequency | Source of truth |
|---|---|---|---|---|
| 1 | Program | A long-lived product investment area with business and architecture intent. | Rare | This document and roadmap documents. |
| 2 | Phase | A major milestone within a program. | Occasional | `sprints/00_SPRINT_MASTER_PLAN.md`. |
| 3 | Sprint | An executable delivery package with acceptance gates. | Frequent | Individual sprint documents. |
| 4 | Work item | PR/issue/task level implementation detail. | Continuous | Issue tracker, PRs, sprint evidence. |
| 5 | Release train | Promotion path from local/CI/staging/TestFlight/production. | Continuous | `operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md`. |

## Initial Product Program

| Program | Scope | Included phases | Exit intent |
|---|---|---|---|
| Program 0 - Product Definition | Product semantics, domain language, architecture contracts, invariants, roadmap baseline. | Phase 1 | Architecture-ready. |
| Program 1 - Platform Foundation | Repository, executable iOS/API/verifier foundations, identity/account/commercial shell. | Phase 2, Phase 3 | Authenticated commercial platform-ready. |
| Program 2 - Core Verified Solving | Capture, canonicalization, AI solving, deterministic verification, learner-facing solution/tutor. | Phase 4, Phase 5, Phase 6 | Trusted solve loop usable by real learners. |
| Program 3 - Learning Intelligence | Attempts, mistakes, mastery, adaptive planning, exam readiness. | Phase 7, Phase 8, Phase 9 | Product shifts from answer tool to learning system. |
| Program 4 - Commercial Production | Monetization, growth/product operations, privacy/security/launch hardening. | Phase 10, Phase 11 | Production V1 launch. |
| Program 5 - Post-Launch Evolution | Evidence-driven expansion after V1 launch. | Phase 12 and future phases | V1.5/V2 growth without corrupting V1 contracts. |
| Program 6 - Proprietary Intelligence | Conditional internal ML/model independence work. | Phase 13 and future gated phases | Lower cost or higher quality only when evidence supports it. |

## Release Interpretation

| Release label | Required program coverage | Notes |
|---|---|---|
| Architecture-ready | Program 0 complete. | No runtime product promise yet. |
| Platform-ready | Program 1 Phase 2 complete. | Executable foundation exists, but users/accounts are not complete. |
| Authenticated commercial shell | Program 1 complete. | Required before user-owned learning/product data grows. |
| Core solving beta | Program 2 materially complete. | Narrow math scope is acceptable; false certainty is not. |
| Learning intelligence beta | Program 3 materially complete. | Mistakes/mastery/adaptive decisions become product differentiators. |
| Production V1 | Programs 0-4 complete with accepted exceptions only. | Commercial iOS-first release. |
| V1.5/V2 | Program 5 work promoted through evidence. | New breadth follows the same source-of-truth and quality gates. |

## Non-Negotiable Sequencing

- Phase 3 identity/account/commerce must precede user-owned product data growth.
- Phase 4 capture/canonicalization must precede solving features that depend on structured problems.
- Phase 5 verification must precede user-visible verified-solve promises.
- Phase 7 mastery/mistake work must not run from generated answers masquerading as student attempts.
- Phase 13 proprietary-ML work remains conditional and cannot bypass ADR-005, ADR-006, ADR-007, or documents 57-64.

## Program Expansion Rule

New phases after Phase 13 are added only when a durable capability gap, market evidence, technical risk, regulatory requirement, or scale threshold cannot be handled as a sprint inside an existing phase.

A new phase proposal must state:

- program ownership;
- product capability or risk being addressed;
- source-of-truth documents affected;
- affected bounded contexts and data classes;
- release target;
- entry gates;
- exit gates;
- minimum acceptance evidence;
- rollback or non-adoption path.

Phase count is not a quality signal. Evidence, source-of-truth traceability, and production readiness are the quality signals.

