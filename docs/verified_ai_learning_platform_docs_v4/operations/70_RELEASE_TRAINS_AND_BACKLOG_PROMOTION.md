# Release Trains and Backlog Promotion

## Purpose

This document defines how work moves from idea to shipped production behavior. It keeps roadmap growth controlled as the product expands beyond the initial Phase 1-13 program.

## Release Trains

| Train | Purpose | Typical scope | Entry gate | Exit gate |
|---|---|---|---|---|
| Local Foundation | Prove implementation locally before CI. | Small code/docs changes, migrations, tests. | Source-of-truth docs read and affected requirements identified. | Local checks pass or documented environment limitation exists. |
| CI Integration | Prove repository consistency. | PR validation. | Branch/PR opened with evidence. | CI passes and review blockers resolved. |
| Staging | Prove deployed behavior with non-production data. | Backend, iOS API integration, jobs, verifier, billing sandbox, AI routes. | CI green and rollout plan exists. | Acceptance, telemetry, security, and rollback evidence captured. |
| Internal TestFlight | Prove iOS installability and closed internal user flow. | iOS release candidates and API-compatible backend. | Staging stable, privacy/logging review complete. | Internal smoke, crash-free baseline, critical flows pass. |
| External Beta | Prove value and reliability with controlled real users. | Production-like V1 flows. | Support/runbook readiness and data deletion/export path reviewed. | Feedback triaged, P0/P1 bugs burned down, release candidate criteria met. |
| Production V1 | Commercial launch. | Programs 0-4 complete with accepted exceptions only. | V1 coverage matrix has no pending unexcepted required rows. | Rollout reaches target population within SLO/error/cost gates. |
| Post-Launch Expansion | Evidence-driven V1.5/V2 capability growth. | Phase 12+ and future phases. | Production telemetry or market evidence justifies expansion. | New capability meets the same traceability and release gates as V1. |

## Backlog Promotion States

| State | Meaning | Required evidence before promotion |
|---|---|---|
| Idea | Unvalidated request, hypothesis, or observation. | Problem statement and affected user/operation. |
| Candidate | Worth exploring. | Source-of-truth documents identified; no known invariant conflict. |
| Program-ready | Belongs to a Program and target release label. | Program owner, capability ID or proposed new capability, risk class. |
| Phase-ready | Fits an existing phase or has an approved new phase proposal. | Entry/exit gates, affected bounded contexts, data/security class. |
| Sprint-ready | Ready for implementation planning. | Requirement IDs, acceptance criteria, test plan, telemetry plan, docs impact. |
| Implementation-ready | Ready for code. | API/schema/migration/UX contracts understood and dependency policy satisfied. |
| Release-ready | Ready for staging/beta/production promotion. | CI green, tests/evals passed, observability and rollback complete. |
| Done | Shipped or explicitly closed. | Evidence retained in sprint/report/PR artifacts. |

No work item may move to `Implementation-ready` if it changes domain, security, API, data, AI, billing, or privacy behavior without traceability to `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`.

## Post-Launch Phase Expansion Policy

Phase 12 is the first explicit post-launch expansion phase. Future Phase 14+ work is created only when one of these triggers exists:

- validated production user demand;
- retention, learning-outcome, conversion, margin, or support evidence;
- a regulatory/platform deadline;
- scale threshold that cannot be handled by existing architecture;
- a new subject/vertical requiring new domain contracts;
- a competitive product gap with a clear business case;
- a technical risk that deserves isolated delivery and rollback.

Every future phase must include:

- program association from `roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md`;
- capability coverage impact;
- requirement traceability impact;
- source-of-truth document changes;
- entry gates;
- exit gates;
- explicit non-goals;
- risk and rollback plan;
- evidence package.

## Release Exception Policy

Production V1 exceptions are allowed only when documented in the release evidence and only if they do not violate a non-negotiable invariant.

Exception record must include:

- capability or requirement ID;
- reason;
- user impact;
- security/privacy/billing impact;
- mitigation;
- owner;
- expiry condition;
- remediation sprint or phase.

Exceptions are forbidden for:

- false `VERIFIED` prevention;
- server-authoritative entitlement;
- account deletion/privacy obligations;
- raw student content logging/analytics minimization;
- mobile exposure of provider or internal verifier secrets;
- PostgreSQL source-of-truth requirements.

## Technical Debt Handling

Technical debt is not hidden in chat history. It must be recorded in `operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md` when it is:

- caused by an accepted workaround;
- visible in CI/test output;
- a dependency deprecation or vulnerability;
- a skipped validation due to environment limits;
- a known operational weakness;
- a planned cleanup required before launch.

Debt may not be used to bypass a blocker without an owner, priority, impact, and trigger for remediation.

