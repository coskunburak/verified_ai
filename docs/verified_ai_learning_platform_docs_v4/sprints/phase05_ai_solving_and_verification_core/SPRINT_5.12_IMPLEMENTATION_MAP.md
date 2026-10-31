# Sprint 5.12 — implementation map

Status: OWNER_ACCEPTANCE_PASSED for local implementation scope; production promotion BLOCKED.

## Baseline and ownership

Initial clean branch: codex/sprint-5-11-verification-arbitration at
0b0b21adaefacd222da7c98dbf3cbc8ad3229ccb. Remote main was verified by read-only
remote lookup and fetched: dd3d4829da650dcd9bedec4f834226a5b102ab79 (PR #36).
Its tracked tree equals the initial checkout. Implementation branch:
codex/sprint-5-12-evaluation-release-gate. No owner changes were present.
No applicable AGENTS.md was found. Sprint 5.11 acceptance is historical only.

## Implementation boundaries

- AI quality owns evaluations/golden-datasets/solving/solver-v1, versioned schemas,
  deterministic observations, bounded loader, scorer, economics and release comparator.
- Local observations are synthetic regression inputs, not live Java or provider output.
  Full backend/verifier regression remains mandatory in owner acceptance.
- Dataset partitions distinguish development, regression, hard tail, protected holdout
  and production corrections; restricted partitions remain unavailable. All examples
  are project-authored synthetic engineering labels, NOT_ELIGIBLE for training, with
  no independent expert approval. This is not the proposed 240-case benchmark.
- Runner modes: validate, local, connected, holdout. External modes fail closed without
  inference. No credentials or provider adapter are activated.
- Metrics record numerator, denominator, missing count and unavailable state. Solver
  correctness, schema validity, policy honesty and proof scope remain separate.
  Fixture economic observations never represent measured provider cost or latency.
- Release comparator enforces hard safety gates, compatible pinned datasets, artifact
  integrity, approved thresholds and reviewed baselines. Missing evidence blocks.
  No production threshold or approved baseline is fabricated.
- Existing AI module registry gains content-bound release evidence checks for SOLVE;
  existing prompt stages and solver production prohibition remain intact.
- No public API, persistence migration, iOS runtime, verification policy, training,
  paid-model enablement, or production-route change is planned.
- Existing ingestion evaluation remains unchanged; CI adds a separate solver job.
- Owner entry point scripts/quality/sprint_5_12_owner_acceptance.sh collects A–J evidence,
  reusing the full Sprint 5.11 backend/verifier/iOS/quality acceptance once.

## Tests and operational dependencies

Dataset integrity, partition leakage, bounded loading, adversarial mathematical claims,
missing outputs, economics, incompatible baselines, tampering, missing approvals,
immutable promotion and rollback receive authored tests. Java tests cover registry
content integrity and default denial. No agent executes any test, build or validator.

Artifacts are minimized to synthetic IDs, aggregates, hashes and controlled reason codes.
Reports belong under .generated; CI retention is bounded. Approved baseline promotion
is explicit, create-only, human-reviewed; rollback revalidates the prior exact artifacts.
No CI job rewrites approved baselines. Branch/environment protections require owner setup.

## External blockers

Independent gold review, representative approved corpus, protected holdout access,
approved immutable provider deployment, observed cost/latency, approved quantitative
thresholds, approved comparable baseline, hosted CI and stage-specific approvals remain
external evidence. Local acceptance cannot authorize production promotion.

## Authored implementation details

The corpus has 28 cases. Case and report contracts are solver-evaluation-case-v1 and
solver-evaluation-report-v1; metrics are solver-metrics-v1. Local config pins fixture
observation bytes; reports pin evaluator/comparator code bytes as well as Git HEAD.
The manifest pins schema and case bytes. The report SHA excludes its own hash field.
The scorer never emits raw problem/answer/scope-detail payloads. The owner final audit
recomputes report hashes and verifies Phase 4 comparator binding.

The release module uses explicit reviewed create-only LOCAL baselines, not fabricated
historical success. Pure economics kernels are extension points for future governed
observations and approvals, not activated external integrations. Production attestation
schemas solver-release-authorization-v1 and solver-production-evidence-v1 are consumed
by the existing Java registry validator; they are never generated from local reports.
The Java resolver is bounded/classpath-only/default-deny and binds the complete route.

Python suites: DatasetTests, RunnerTests, HonestyTests, EconomicsTests, ReleaseTests.
Java suites: SolverReleaseRegistryTest, SolverReleaseEvidenceTest. CI adds the separate
deterministic-solver-regression job, with no secrets and 14-day safe artifact retention.
The new owner runner executes A–E, reuses Sprint 5.11 once for F–I, executes Phase 4
regression, and reconciles actual stage exits, nonempty test evidence and report hashes
in J. Full iOS regression is inherited unchanged. No test/build/validator was executed
by the agent; all authored behavior remains pending owner validation.

Coverage also exposes locale, modality, difficulty, partition, canonical support, verification
scope and Turkish decimal notation. Report validation recomputes slice aggregates against
the matching pinned corpus, preventing rehashed slice substitution.

## Owner-run repair checkpoint — 2026-10-31

Run 20261031-120947-i2Hxbh exposed a schema-error expectation mismatch and absent
Jackson Duration support. The release test now checks the earlier strict schema denial;
the Java adapter defines explicit ISO-8601 Duration serialization with nanosecond-preserving
binding tests. No dependencies, release permissions or fixture approval scopes changed.
Repair acceptance is pending owner rerun; no agent validation was executed.

## Complete local owner acceptance — 2026-10-31

Owner run 20261031-121345-aTjFqW completed A–J PASS, exit 0. Evaluation suites executed
76 tests with no failures/skips; fresh inherited platform backend/Linux/iOS/quality and
Phase 4 regression evidence passed. This supersedes prior local pending/repair status.
See the execution report for exact evidence paths and counts. External model/holdout/
production evidence remains BLOCKED; hosted CI NOT_RUN. Only acceptance documentation
and manifest changed after the run; those metadata edits await separate validation.
No agent test, build or validator execution, commit, push or deployment occurred.
