# Sprint 5.8 — Implementation map

## Baseline and prerequisite

Branch `codex/sprint-5-8-math-verification`, HEAD `eec8d6a2ec4ec1a1122adf0dd0111af831ba978d`.
Clean starting tree. HEAD merges PR #31; Sprint 5.7 head `ea4c996` is an ancestor.
The owner runs ALL tests, builds and runtime validation. No agent execution, commit or publication.

## Repository findings

- `VerificationPlanning` is an internal query. Canonical reads precede the short owner/source transaction; its entry point rejects caller transactions. Preserve this order.
- `canonical-capabilities-v1` advertises no executable methods. Legacy string equivalence and typed input validation are not canonical answer checking.
- POWER already uses converted `right`; no operand bug exists at this baseline. Integer-only exponent enforcement and undefined-value handling need tightening.
- Canonical numeric lexemes are exact base-ten strings; RATIONAL does not mean slash syntax. Fractions use DIVIDE nodes. Preserve existing schema versions.
- Canonical solutions have one final statement, not a general root-set representation. Completeness can prove a singleton only when the independently derived restricted solution set is exactly that singleton.
- Latest existing migration is V020. No verification evidence store exists. Existing account lifecycle interface provides export/deletion integration.
- Legacy signal timeout silently does nothing in non-main threads. A separate supervised process is necessary for expensive typed execution.

## Ownership and file map

| Slice | Files/owner | Contract |
| --- | --- | --- |
| A | This map; Sprint 5.8 execution report | Scope, invariant, owner checkpoints |
| B | Python `api/schemas/execution.py`, `verifiers/canonical_execution.py`, `policies/process_executor.py`; typed validator and execution route | `verification-execution-v1`; bounded exact rational/polynomial algorithms; no raw math parsing |
| C | Java verification domain execution/evidence records; application execution port/service; infrastructure HTTP adapter/configuration | Typed authenticated bounded HTTP; no network transaction; strict response correlation/method/version/proof validation |
| D | Verification repository port/JDBC adapter/privacy contributor; platform V021 | Atomic immutable run plus method evidence, exact owner/source/solution FK, idempotency, owner export and cascade deletion |
| E | Process budgets, request/response limits, secret validation, deployment config, bounded metrics | Fail closed on timeout/crash/admission failure; no mathematical disproof from operational failure |
| F | Shared schema/fixtures, Python/Java tests, architecture rules, canonical docs/index/manifest | Owner-only gates A–H; no capability release without acceptance and rollout approval |

## Mathematical contract

Outcomes: PROVEN proves the exact scoped claim; DISPROVEN establishes an exact contradiction;
INCONCLUSIVE finishes without sufficient proof; UNSUPPORTED is outside the bounded subset;
BLOCKED lacks required domain/representation prerequisites; EXECUTION_FAILED is operational failure.
No outcome assigns overall VERIFIED or changes a canonical solution.

Arithmetic uses exact fractions, including decimal lexemes, integral powers of magnitude <=12 and explicit zero checks.
Equivalence uses bounded rational polynomials with original AST domain obligations retained; no sampling or unrestricted simplify/solve.
Root substitution evaluates the ORIGINAL equality and all restrictions at one unambiguous exact rational assignment.
Domain checking handles rational inequalities, denominator zeros, real square-root/log domains and only independently decidable function values.
Completeness independently analyzes bounded degree <=2 rational polynomials, including degenerate cases and source restrictions; unsupported root-set representations remain non-proof outcomes.
Unknown variable domains, untyped assumptions, unmatched restrictions and unsupported transcendental values fail closed.

## Execution, persistence and release

A: owner-authorized canonical read and fresh planning, then short locked source preparation.
B: all mathematical HTTP work outside transactions. Bounded supervised processes, no queue and finite admission.
C: new short owner/source transaction rechecks every lineage field, selected parse and plan before atomic publication.
Stale computations cannot publish current evidence. Historical records are immutable and current reads always reauthorize.
Idempotency binds owner/session/solution/request key to one immutable run; concurrent publication reuses that run.
New evidence is owner data, never training data. No new AI calls, public endpoint, iOS change or billing change.
Capability activation stays disabled by default pending owner tests and explicit rollout approval; old plan identities retain their meaning.
Rollback disables execution and retains additive schema/history; no destructive down migration.

## Acceptance and deferred scope

Author mathematical/adversarial/contract/resource tests, gateway/application unit tests,
PostgreSQL authorization/history/idempotency/deletion/export/concurrency tests and architecture/regression coverage.
Gates: A Python math; B Python security/contracts; C Java units; D wire integration;
E PostgreSQL/privacy/concurrency; F architecture/regression; G full suites; H repository quality/hosted CI.
Owner evidence on 2026-10-29: all local gates A–H passed. A 58 passing; B 29 passing; C 29 distinct tests passing; D actual cross-runtime contract passing. E passed 2 export units, 10 execution and 6 planning integration tests. F passed 141/141. G passed 551 backend tests with 2 gated Qwen skips and all 92 Linux verifier tests. Lint cleanup and verifier/wire reruns passed. H lint/docs/contracts/secrets and 5 immutability units passed; existing prompt/schema versions are unchanged. Final evidence-only documentation refresh awaits docs-check. Hosted CI remains NOT RUN; rollout stays disabled.
Deferred: calculus, numerical sampling/composite policy, arbitration, overall verification policy,
general transcendental reasoning, arbitrary-degree solving and general finite/infinite root-set schemas.
