# Sprint Execution Standard — Production Delivery Contract

## 1. Purpose

Every sprint in this repository is an implementation contract, not a backlog title. A sprint is complete only when the delivered behavior is semantically correct, secure, observable, testable, economically understood, documented, and safely deployable.

## 2. Mandatory sections for every sprint

Each sprint document must define or explicitly mark N/A for:

1. mission and product/system outcome;
2. prerequisites and source-of-truth documents;
3. in-scope and out-of-scope boundaries;
4. domain/module ownership;
5. iOS work;
6. backend work;
7. database/storage work;
8. API/event/schema work;
9. AI/model/verification impact;
10. privacy/security/abuse impact;
11. observability/SLO/cost impact;
12. automated test/evaluation plan;
13. rollout/feature-flag strategy;
14. rollback/recovery path;
15. acceptance criteria;
16. Definition of Done;
17. required evidence package;
18. documentation updates.

## 3. Production-vs-prototype rule

Prototype shortcuts may exist in a spike branch, but a sprint exit gate cannot depend on:

- hard-coded secrets;
- direct client/provider coupling;
- unversioned JSON;
- manual database state;
- “temporary” production TODOs that bypass invariants;
- unbounded AI calls;
- undocumented retries;
- invisible fallback behavior;
- untested migration assumptions.

## 4. AI-affecting sprint rule

Every AI-affecting sprint must quantify:

- capability invoked;
- expected calls per learner action;
- model route tier;
- secondary-solver invocation trigger if relevant;
- quality baseline;
- cost baseline;
- latency baseline;
- fallback behavior;
- evaluation dataset version;
- rollback mechanism.

No sprint before conditional Phase 13 may introduce production model training or self-hosted generative inference unless an explicit accepted ADR changes the roadmap.

## 5. Data-affecting sprint rule

Every new durable field/table/object must answer:

- who owns it;
- why it is stored;
- whether it contains PII/student content;
- how long it is retained;
- how account deletion reaches it;
- whether it is authoritative or derived;
- whether it is training eligible (default: no);
- required indexes/constraints;
- migration/rollback plan.

## 6. API-affecting sprint rule

Every API change includes:

- authn/authz;
- request validation;
- idempotency/concurrency behavior;
- timeout/retry policy;
- stable error codes;
- version/backward-compatibility decision;
- contract fixtures/tests;
- observability.

## 7. UI-affecting sprint rule

Every major screen or flow includes:

- loading;
- empty;
- success;
- recoverable error;
- non-recoverable error;
- offline/degraded state where relevant;
- accessibility;
- localization readiness;
- analytics semantics;
- performance budget.

## 8. Required evidence at sprint review

A reviewer should be able to inspect:

- source/documentation diff;
- implementation map;
- tests/evaluations;
- a production-like demo;
- trace/metrics example;
- migration/API contract changes;
- security/privacy delta;
- AI quality/cost delta if relevant;
- feature flag/rollback configuration;
- known limitations.

## 9. Exit philosophy

The next sprint may rely on this sprint only after its documented exit gate is satisfied. If a risk is deliberately deferred, record owner, severity, expiry condition, and exact remediation sprint.
