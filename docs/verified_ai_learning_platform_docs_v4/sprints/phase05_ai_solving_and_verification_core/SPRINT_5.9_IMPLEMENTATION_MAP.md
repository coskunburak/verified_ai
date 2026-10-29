# Sprint 5.9 — Implementation map

## Baseline and decision

Remote main was read from GitHub and fetched on 2026-10-29:
`a4e1070cfdbdda0a1025cf022c022119399c46e1`. Clean original branch
`codex/fix-ios-ci-simulator` at `be0a37e30829dfc316be9ed4f0d044e08cd8ea1f`;
implementation branch `codex/sprint-5-9-calculus` starts at the fetched main.
No tracked/untracked owner changes or applicable AGENTS.md files were found.
Sprint 5.8 implementation and the merged simulator repair are present.

The source boundary is BLOCKED: `problem-parse-v1` names calculus tasks but has no
typed approach target/direction or integral-family semantics. `CanonicalProblemBuilder`,
`SolutionCandidateValidator` and `CanonicalCandidateMapper` accept only the existing
non-calculus v1 task contracts. Guessing metadata from display text would be unsound.
Use the explicitly authorized partial delivery: an isolated typed internal calculus
executor, with no production planner, source reader or publication activation.

## Ownership and exact paths

- Verification Java domain: `services/api/src/main/java/com/verifiedai/verification/domain/model/CalculusExecution.java`.
- Verification wire boundary: `services/api/src/main/java/com/verifiedai/verification/infrastructure/http/CalculusWireCodec.java`.
- Python schemas: `services/math-verifier/app/api/schemas/calculus.py`.
- Python mathematics: `services/math-verifier/app/verifiers/calculus.py` and `calculus_domains.py`.
- Existing supervised transport: `services/math-verifier/app/policies/process_executor.py` and `app/api/routes/verify.py`.
- Shared contracts: `packages/schemas/calculus-execution.schema.json`, `calculus-execution-result.schema.json`.
- Shared cases: `packages/test-fixtures/verification/calculus-v1/cases.json`.

## Version and proof contracts

`calculus-execution-v1` / `calculus-execution-result-v1` are separate from every
published canonical, candidate and execution v1. AST syntax is reused unchanged.
`calculus-task-v1` is an INTERNAL task representation, not an accepted durable
canonical problem or solution. An opaque scope identity plus source/candidate revisions
and representation versions binds the request and response. These are correlation,
not authorization; only a future owned canonical reader can establish authority.
No user IDs, prompts, explanations or source text cross this boundary.

One explicit variable; REAL open connected interval with rational endpoints (null
means unbounded). UNKNOWN/INTEGER domains block ordinary real calculus. Integral
scope is ANTIDERIVATIVE_REPRESENTATIVE, never a complete family or an invented C.
Limits require rational finite target and LEFT/RIGHT/TWO_SIDED explicitly.

Derivatives use structural rules and bounded polynomial-ring coefficient comparison.
Approved functions: SIN/COS/EXP chain rules; LOG/SQRT only where rational argument
positivity is independently established throughout the open interval. TAN is unsupported.
No arbitrary integration, simplify, solve or expression-text parsing. Nonzero symbolic
residuals containing function atoms are inconclusive; rational-polynomial nonidentity
on a nonempty open interval is an exact contradiction.
Limits use rational-polynomial orders of vanishing at the approach point; cancellation
is valid only on a proved punctured neighborhood. Poles remain non-proof outcomes.
Source/candidate obligations are checked before cancellation. Restrictions must hold
throughout the scoped interval/neighborhood, not be silently ignored.

## Numerical, resource and security policy

Limit fallback uses at most 16 deterministic points (8 per required side), binary64,
absolute/relative tolerance 1e-8, finite bounded values, and checked original domains.
Agreement and disagreement are both INCONCLUSIVE, with no proof descriptor.
The generic NUMERIC_SAMPLING capability is not activated.
All symbolic and numerical operations share the existing spawn supervisor: 3 s elapsed,
2 s CPU, 512 MiB Linux address space, two concurrent workers, no queue, 64 KiB request,
16 KiB response; AST 120 nodes/expression, depth 32, function depth 8, powers <=12,
1024-bit rational intermediates, polynomial degree <=12 and <=128 terms.
Internal token authentication, preparse limits, sanitized failures and enum metrics remain.
No paid inference, cache, training workload or new public endpoint.

## Persistence, authorization and release

No new persistence or migration. Existing immutable aggregate, export/deletion,
idempotency, source-before-lock order and stale publication checks remain unchanged.
Calculus cannot be published into the historical v1 aggregate. Production Java
capabilities continue to reject calculus. Python calculus is separately disabled by
default and can be enabled only in local/test environments for owner acceptance.
Rollback: leave calculus disabled; retain additive source/contracts; no data rewrite.

## Acceptance and deferred work

Author math/adversarial/domain, Python contract/process/security, Java typed-wire and
actual cross-runtime HTTP tests. Owner runs Linux resource tests, existing PostgreSQL
publication/privacy/concurrency tests, architecture/regression, full suites and repository
quality/immutability checks. All local A–H gates passed in owner checkpoints below.
No agent executes validation. Final evidence-documentation refresh docs-check is pending.
Deferred prerequisites: source-backed parser/canonical/candidate calculus revisions,
owned source assembly, method planning, capability acceptance and immutable publication
version extension. Sprint 5.10 composite policy and Sprint 5.11 overall arbitration remain
out of scope. Partial implementation cannot receive end-to-end Sprint 5.9 acceptance.

## Owner checkpoint — 2026-10-29

Owner Linux suite: 186 passed (A/B and Python G), including calculus/resource coverage.
Java compiled, then the first calculus HTTP contract request returned 422; fail-fast
skipped most backend tests. Repair explicitly serializes scope UUID text and other
wire fields instead of Jackson record-to-tree conversion. Java unit/contract regressions
are authored; no agent validation. Backend, Gate H and cross-runtime rerun remain
OWNER_VALIDATION_PENDING. Production source integration remains BLOCKED.

## HTTP transport repair checkpoint — 2026-10-29, 13:19 +03:00

Owner rerun confirmed shared request equality, then encountered a non-JSON HTTP parser
response. Pin both the calculus contract client and existing HttpMathematicalExecutor
to HTTP/1.1; Uvicorn does not implement Java's default h2c upgrade. Response diagnostics
now tolerate non-JSON failures and do not expose payloads. Existing transport tests
assert HTTP/1.1 and absent upgrade headers. Backend and Gate H rerun remain pending;
retain unchanged Python Linux evidence. No resource budget or mathematical meaning changed.

## Backend acceptance and lint repair — 2026-10-29, 13:24–13:25 +03:00

Owner Maven verify: BUILD SUCCESS, 566 passing tests and two intentional paid-model
skips (568 reported), zero failures/errors. Both calculus HTTP tests and all five existing
adapter tests passed; full backend regressions passed. Gate H then stopped on six import
organization findings and one nested interval guard. Manual style-only repairs preserve
short-circuit behavior. Retain Linux 186 passing and backend evidence; rerun only Gate H
using the execution report block. No agent validation. Production integration stays BLOCKED.

## Local acceptance evidence — 2026-10-29, 13:29 +03:00

Owner Gate H exited 0: lint, docs (296 files), contracts, secrets, five immutability
units, unchanged prompt/schema versions and released v1 schema diff all passed.
Retain Linux 186 passing and backend 566 passing/two intentional paid-model skips with
BUILD SUCCESS. All local A–H gates passed for the isolated executor. This documentation
refresh alone awaits docs-check; its passing owner log can close the refresh without
another document edit. End-to-end source integration BLOCKED / ROLLOUT_DISABLED /
HOSTED_CI_NOT_RUN. Sprint 5.9 remains PARTIAL. No agent validation.

## Production integration closure follow-up

The original isolated implementation and its PARTIAL owner checkpoints above remain
historical evidence. The subsequent source-authoritative integration is recorded in
[closure implementation map](SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_IMPLEMENTATION_MAP.md)
and [closure execution report](SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_EXECUTION_REPORT.md).
Closure is IMPLEMENTED_PENDING_OWNER_VALIDATION / CI_PENDING / ROLLOUT_DISABLED;
the original passing isolated tests do not establish acceptance of the new working tree.
