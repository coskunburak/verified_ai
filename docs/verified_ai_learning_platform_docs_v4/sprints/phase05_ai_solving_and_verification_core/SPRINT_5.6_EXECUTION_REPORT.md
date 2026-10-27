# Sprint 5.6 — Execution report

Status: **SPRINT_5.6_BACKEND_FIXTURE_ACCEPTANCE_PASS** (owner Gates A–F).
Date: 2026-10-27 (Europe/Istanbul). No assistant build, test, validator, linter, scan,
Docker, migration execution, inference or dependency installation. Subsequent owner-authorized
Git delivery is described below.

## Baseline and protected changes

Branch: `feature/sprint-5-2-prompt-registry-governance`.
HEAD: `90ee3f492aa0c17d1c2d4533837a484f62ccd42c`.
Entry status/diff were empty. No pre-existing dirty paths to protect; no Git mutation during implementation.
The supplied remote main checkpoint was historical context only.

## Implemented behavior

- `CanonicalSolution` owns immutable lineage, representation/normalizer versions, provenance,
  final mathematical statement, ordered steps, restrictions and uncertainty. Sealed math
  records preserve exact numeric lexemes, operand order, declared relations and v1 functions.
  Domain collections are defensive copies; expression depth/node/function limits and graph
  invariants reject unsafe structure. No synthetic rule IDs, algebraic rewriting or verdicts.
- Materialization reads accepted primary/secondary evidence only, reuses the existing strict
  source-relative candidate validator, then maps syntax without changing mathematics.
  JSONB formatting is compacted before the original candidate wire-size gate. Known optional
  provenance fields reject incompatible types instead of silently erasing them.
- Primary canonical revision is resolved from the exact owned historical source through the
  problem application API because V018 does not store that revision. Secondary revision and
  parent primary identity must also match. Source UUIDs/revisions are never invented.
- Integration uses durable bounded polling of both existing V018/V019 candidate tables,
  following the established Sprint 5.5 pattern. The canonical worker is independently disabled
  by default. After owner acceptance, `app.solving.canonical.worker-enabled=true` enables only
  deterministic projection, never solver routing. Its interval defaults to PT5S, batch 10.
  Internal `CanonicalSolutionService.recover(1..100)` is the bounded manual/recovery entry.
- Every item runs in a separate short REQUIRES_NEW transaction. Account/session locks and
  database uniqueness protect concurrent reuse. Candidate commit precedes projection, so
  rollback never removes accepted evidence or repeats inference. No new queue/outbox.
- Invalid/unsupported historical artifacts have immutable typed failures per version. Database
  errors roll back and remain eligible for later polling. Failure and success are distinct;
  there is no model retry. Version evolution requires a governed code change, retaining old
  rows. Known v1 representation/old normalizers remain readable as HISTORICAL_VERSION;
  unknown representations return UNSUPPORTED_REPRESENTATION without fabricated decoding.
- Current internal reads use the authoritative selected parse/source, returning NOT_MATERIALIZED,
  GENERATED_UNVERIFIED, REVIEW_REQUIRED or SOURCE_UNAVAILABLE. Historical lookup separately
  identifies source/version history. No public endpoint or raw secondary reasoning projection.
- Privacy contribution exports complete historical metadata/content, steps, dependencies and
  failures. Existing account/source deletion cascades through all projection tables. Canonical
  content has the same owner-retention lifecycle and remains ineligible for training by default.
- Metrics use bounded outcome/rejection/read/recovery labels and latency. No raw math, question,
  explanation, user UUID or provider key enters metric labels/logs. Original operation/ledger
  IDs remain available as provenance references for correlation; payloads are not logged.
- Existing public primary response, OpenAPI, SwiftUI and math verifier remain unchanged.
  Canonicalization has no gateway/provider dependency and adds zero inference or model cost.
  Production SOLVE, secondary routing, paid budgets and Qwen gates are unchanged.

## Persistence and rollback

New migration: V020__create_canonical_solution_projection.sql; V001–V019 are unchanged.
Tables: canonical_solutions, canonical_solution_steps, canonical_solution_dependencies,
canonical_materialization_failures. Relational columns hold owner/source/role/version/order.
Bounded JSONB holds typed math and solution metadata; explanation has a separate bounded column.

Composite FKs bind canonical source/revisions, primary candidate/owner and secondary
candidate/parent/owner. Exact role/job/representation/normalizer uniqueness prevents duplicates.
Step UUID/ordinal/candidate-ID uniqueness, earlier-target composite FKs and ordered reference
uniqueness protect graph structure. Deferred completeness triggers reject partial graphs,
noncontiguous orders and partial append/delete mutations while allowing complete cascaded
privacy deletion. Update triggers protect immutable solution/step/dependency/failure content; delete guards
reject delete/reinsert rewrites unless the owning source is being cascaded away.
V020 also freezes primary job identity, preserving the existing nullable retry-parent deletion.

Full AST/symbol/source restriction checks remain application responsibilities; SQL checks the
bounded envelopes and graph, not mathematical truth. Ledger IDs are historical references;
projection does not require mutable ledger retention to preserve provenance identity.

Rollback: disable the canonical worker, retain additive tables/history and originals. A database
correction uses a new forward migration. No destructive down migration or inference replay.

## Tests authored (executed by owner)

| Class | Coverage |
| --- | --- |
| CanonicalSolutionTest | Stable role/version IDs, immutable collections, contiguous graph, missing/self/forward/cyclic/duplicate dependencies, lineage and explanations; no verdict field. |
| MathStatementTest | Exact numbers, invalid lexemes/symbols, arity, depth/node/function bounds, operand order and unsimplified syntax. |
| CanonicalMathCodecTest | Every v1 operator/function/relation, exact numeric round-trip, duplicate/trailing/oversized input and Jackson 2/3 string bridge. |
| CanonicalCandidateMapperTest | Both roles, original claims/explanations/provenance, restrictions/uncertainty, source mismatch, unsupported versions, malformed/prompt-injection text and faithful wrong answer. |
| CanonicalCandidateContractTest | All existing accepted solver contract fixtures retain exact final/step AST and explanation. |
| CanonicalSolutionServiceTest | Owner-first access, fixture mapping/read/reuse, typed rejection, transient recovery, bounded batches and metric privacy. |
| CanonicalMigrationIntegrationTest | Isolated actual V019 schema upgrade to V020, tables/indexes and repeat migration. |
| CanonicalSolutionIntegrationTest | Real PostgreSQL graph/read, concurrency, atomic rollback/recovery, malformed historical evidence, immutability, version coexistence, stale reads, authorization/FKs, export serialization/deletion and retry history. |
| CanonicalSolutionFixtureE2ETest | Actual fixture primary/secondary pipelines, separate projections/parent/provenance, unchanged agreement and unverified response, no extra provider/ledger cost, stale degraded path and deleted source. |
| CanonicalSolutionArchitectureTest | No canonical gateway/provider/presentation dependency, no domain Jackson/JDBC dependency, no problem infrastructure bypass. |

The persistence integration fixture deliberately seeds accepted rows without a model. The E2E
fixture separately exercises the real primary/secondary application pipelines through the existing
zero-cost synthetic adapters. Neither establishes real-model quality or mathematical correctness.

## Owner evidence

2026-10-27 13:52:13 +03:00 — owner attempted Gate A with
`mvn -Dtest=CanonicalSolutionTest,MathStatementTest test` under JDK21. Production compilation
completed (468 sources); test compilation failed before any selected tests ran. The six reported
errors share one cause: CanonicalMigrationIntegrationTest accessed the package-private POSTGRES
field of its superclass from another package. Both Flyway configurations now use the existing
injected JdbcTemplate DataSource instead; superclass visibility and production code are unchanged.
2026-10-27 13:53:19 +03:00 — owner Gate A rerun: **PASS**, 8 tests, 0 failures,
0 errors, 0 skipped (CanonicalSolutionTest: 5; MathStatementTest: 3), BUILD SUCCESS.

2026-10-27 13:53:32 +03:00 — owner Gate B: 25 tests, 0 failures, 2 errors,
0 skipped (23 passed). CanonicalMathCodecTest: 3/3; CanonicalCandidateMapperTest: 5/5;
CanonicalCandidateContractTest: 9/9. CanonicalSolutionServiceTest: 8 total, 6 passed,
2 setup errors. Both failures occurred while replacing the existing insert mock answer via
`when(repository.insert(any()))`: Mockito invoked the previous answer with the matcher's null
argument, causing a NullPointerException before exercising the intended recovery scenarios.
Insert stubbing now uses doAnswer/doThrow so replacement does not execute the previous answer.
Production code and assertions were unchanged; only CanonicalSolutionServiceTest required rerunning.

2026-10-27 13:54:48 +03:00 — owner focused CanonicalSolutionServiceTest rerun: **PASS**,
8 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS. Gate B is now **PASS** across the
accepted codec (3), mapper (5), contract (9) and service (8) results: 25 distinct tests,
no outstanding failures/errors/skips. This combines the passing class results from the initial
Gate B run and the focused rerun; it is not a claim of a new full Gate B execution.
Gate A remains accepted at 8 tests. The subsequent C–F owner results are recorded below.

### Owner Gates C–F — 2026-10-27

| Gate | Completion time (+03:00) | Owner evidence |
| --- | --- | --- |
| C | 13:56:53 | BUILD SUCCESS; 12 tests, 0 failures/errors/skips. CanonicalMigrationIntegrationTest: 1; CanonicalSolutionIntegrationTest: 11. Actual V019-to-V020 upgrade, persistence, concurrency, immutability, recovery, version history and privacy passed. |
| D | 13:58:06 | BUILD SUCCESS; CanonicalSolutionFixtureE2ETest: 3 tests, 0 failures/errors/skips. Primary/secondary fixture projections, stale/degraded path, ownership and source deletion passed. |
| E | 13:58:30 | BUILD SUCCESS; 80 tests, 0 failures/errors/skips. Candidate/API regressions, account privacy, architecture/Modulith and migration checks passed. |
| F — API | 13:59:22 | BUILD SUCCESS; 483 total, 481 passed, 0 failures/errors, 2 skipped. Both skips are QwenRealModelEvaluationTest gates; they are not model acceptance. All new canonical test classes also passed in this full run. |
| F — quality/evaluation | Following the API run | Contract check passed; documentation check passed (290 Markdown files); secret scan passed; lint completed; local ingestion dataset/evaluation/baseline comparison passed. |

Local evaluation: 18 cases, 13 critical; dataset SHA-256
769befe32df0f8646aec4c5fa49c777bb4b39b990e92fd70af18811f6270a50f.
Report SHA-256: a397c830a49acdc1f9b7b7f9ea514d9f4e7be535a90342c8af42d8f56d733918.
Comparison baseline: production-ingestion-v1; decision PASS, exitCode 0, no reported reasons
or regressions. Existing near-duplicate fixture warnings were non-blocking. The local JSON
artifact is .generated/evaluations/latest/ingestion-evaluation-report.json; generated Markdown
summaries are local artifacts and are not repository documentation links.

Evidence: owner-pasted Maven transcripts; local artifacts are in services/api/target/surefire-reports.
The assistant executed no tests/builds/validators/migrations. These are owner-run local results,
not hosted GitHub CI evidence. Only acceptance documentation and manifest metadata were updated
after this transcript; production source, tests, migration and feature flags were unchanged.
The latest documentation-only edits have not been rerun by the assistant. No real-model
acceptance, production rollout approval or new iOS/verifier result is claimed.

## Owner commands — ordered gates

These commands reproduce the accepted gates; no additional full backend rerun is requested.

Use an installed JDK21 and Maven. Gates A/B need no Docker at execution time; Maven still compiles
all main/test sources before selecting tests, so an unrelated compiler failure can stop a focused
gate. Gates C/D/E and full API need a running Docker daemon for PostgreSQL Testcontainers.
Do not enable real-model flags. Full repository quality also needs the repository's existing
Python/ruff and evaluation prerequisites. No dependency installation command is prescribed here.

Initial shell setup (JDK path follows this repository's Makefile; adjust if installed elsewhere):

```bash
cd /Users/burakcoskun/Desktop/ai_verification_tutor
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
cd services/api
```

Gate A — pure domain/AST/graph:

```bash
mvn -Dtest=CanonicalSolutionTest,MathStatementTest test
```

Gate B — mapper/codec/contract and isolated service (no Docker):

```bash
mvn -Dtest=CanonicalMathCodecTest,CanonicalCandidateMapperTest,CanonicalCandidateContractTest,CanonicalSolutionServiceTest test
```

Gate C — PostgreSQL migration/persistence (Docker):

```bash
mvn -Dtest=CanonicalMigrationIntegrationTest,CanonicalSolutionIntegrationTest test
```

Gate D — deterministic primary/secondary fixture E2E, including stale/degraded case (Docker):

```bash
mvn -Dtest=CanonicalSolutionFixtureE2ETest test
```

Gate E — affected regressions, privacy, architecture and migrations (Docker):

```bash
mvn -Dtest=SolutionCandidateValidatorTest,SolutionCandidateContractTest,PrimarySolveIntegrationTest,SecondarySolveIntegrationTest,PrimarySolveControllerTest,AccountPrivacyApplicationServiceTest,CanonicalSolutionArchitectureTest,SecondaryBlindnessArchitectureTest,ModularityTest,FlywayMigrationTest test
```

Gate F — only after A–E pass, full backend and repository quality from project root (Docker;
`make test-api` builds the existing local MinIO test image):

```bash
cd /Users/burakcoskun/Desktop/ai_verification_tutor
make test-api
make contracts-check docs-check secret-scan lint
AI_EVAL_MODE=local make eval-ai
```

Unchanged iOS/verifier release confirmation is optional if required by release policy:

```bash
make test-verifier
make test-ios
```

For each failed gate, share the first causal compiler/test error and its stack trace, failing
class/method and final Maven totals; include the relevant services/api/target/surefire-reports
artifact. Stop on that gate; focused root-cause rerun comes before later/full regressions.
Exact date, counts, skips and artifacts are recorded above. Gated real Qwen tests
may remain skipped in the full API suite; a skip is never a model acceptance pass.

## Open limitations and release boundary

Owner Gates A–F accept the deterministic backend/fixture scope. Production rollout remains a
separate decision. The worker stays disabled until that decision;
materialization is available through the internal service and recovery entry in the meantime.
There is no startup-wide backfill, public canonical endpoint, verifier/arbitration or iOS redesign.
Permanent historical rejection is scoped to the exact normalizer/representation; remediation
uses a governed new version instead of updating accepted artifacts. Repeated infrastructure
failures defer the same bounded batch until storage recovers; no inference fallback is attempted.
Real-model primary/secondary acceptance remains a separate blocked, owner-authorized activity.

Downstream Sprint 5.7 consumes CanonicalSolutions plus the named canonical-model contract,
checking read state/version/source before planning verification. Model agreement remains separate
non-authoritative evidence. No VERIFIED field/value/status was added.

## Implementation checkpoint

Completed: domain/codec/mapping, historical source authority, V020 and JDBC persistence, bounded
poller, owned read API, privacy/metrics, unit/contract/DB/fixture/architecture test sources and docs.
Owner Gates A–F are passed: A 8, B 25 distinct, C 12, D 3, E 80; full API 481 passed / 2
gated skips, repository quality and local evaluation passed. No runtime changes or extra full
regression are required by these results. Preserve acceptance; production activation and real-model
acceptance remain separate owner decisions. Static review also corrected caught-unsupported-version
transaction semantics and blocked delete/reinsert rewrites. File inventory follows.

## Git delivery authorization

On 2026-10-27, after owner A–F acceptance, the owner explicitly requested finishing the reports
and committing/pushing the sprint. Delivery targets the existing branch
feature/sprint-5-2-prompt-registry-governance on origin (coskunburak/verified_ai).
Only the 35 inventoried sprint files belong in this commit; generated evaluation artifacts,
build outputs and local attachments are excluded. No branch switch, main merge, force push,
production activation or additional test/build execution is part of this delivery.
The final owner-facing Git receipt records the actual commit and remote push result after execution.

## Actual file inventory

| Repository path | Purpose |
| --- | --- |
| `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Index the new sprint map and report. |
| `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Update canonical document paths and exact line/byte metadata. |
| `docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md` | Document actual V020 schema and enforcement boundaries. |
| `docs/verified_ai_learning_platform_docs_v4/domain/06_UBIQUITOUS_LANGUAGE_AND_GLOSSARY.md` | Define canonical solution and step vocabulary without correctness claims. |
| `docs/verified_ai_learning_platform_docs_v4/domain/07_DOMAIN_MODEL_AND_AGGREGATES.md` | Document concrete immutable solution/AST ownership. |
| `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Record role, history, graph and trust invariants. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.6_EXECUTION_REPORT.md` | Record implementation, inventory, owner commands and dated A–F acceptance evidence. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.6_IMPLEMENTATION_MAP.md` | Record architecture, source lineage, transactions, rollback and gates. |
| `services/api/src/main/java/com/verifiedai/problem/application/solving/SolveSourceAccess.java` | Add owned explicit historical source lookup within the problem authority. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/CanonicalCandidateMapper.java` | Revalidate accepted artifacts and map faithful typed primary/secondary projections. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/CanonicalMathCodec.java` | Bounded explicit Jackson 2 typed math persistence codec. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/CanonicalSolutionService.java` | Own materialization, short transactions, bounded recovery and current/history reads. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/CanonicalSolutionWorker.java` | Schedule bounded accepted-candidate polling behind a disabled default flag. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/CanonicalSolutions.java` | Expose typed owner-scoped internal application results. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/package-info.java` | Expose the named internal canonical application/model contract through Spring Modulith. |
| `services/api/src/main/java/com/verifiedai/solving/domain/model/canonical/CanonicalSolution.java` | Immutable aggregate, role/version/lineage/provenance and ordered step graph. |
| `services/api/src/main/java/com/verifiedai/solving/domain/model/canonical/MathExpression.java` | Sealed bounded exact numeric/variable/unary/binary/function syntax. |
| `services/api/src/main/java/com/verifiedai/solving/domain/model/canonical/MathStatement.java` | Separate expression and relation roots with resource bounds. |
| `services/api/src/main/java/com/verifiedai/solving/domain/model/canonical/package-info.java` | Expose the named internal canonical application/model contract through Spring Modulith. |
| `services/api/src/main/java/com/verifiedai/solving/domain/repository/CanonicalSolutionRepository.java` | Define solving-owned accepted-artifact/projection/failure persistence port. |
| `services/api/src/main/java/com/verifiedai/solving/infrastructure/persistence/JdbcCanonicalSolutionRepository.java` | Implement atomic relational graphs, idempotent origin keys, reads and exports. |
| `services/api/src/main/java/com/verifiedai/solving/infrastructure/privacy/SolvingLifecycleContributor.java` | Integrate canonical graph/failure export and transactional deletion cascades. |
| `services/api/src/main/resources/application.yml` | Keep deterministic canonical worker disabled by default with bounded interval. |
| `services/api/src/main/resources/db/migration/platform/V020__create_canonical_solution_projection.sql` | Add durable immutable projection/failure schema, ownership FKs and graph constraints. |
| `services/api/src/test/java/com/verifiedai/architecture/CanonicalSolutionArchitectureTest.java` | Guard inference/presentation/domain serialization and problem-module boundaries. |
| `services/api/src/test/java/com/verifiedai/integration/solving/CanonicalMigrationIntegrationTest.java` | Author isolated V019-to-V020 upgrade and schema/index tests. |
| `services/api/src/test/java/com/verifiedai/integration/solving/CanonicalSolutionFixtureE2ETest.java` | Author actual primary/secondary synthetic pipeline and degraded/source-deletion E2E. |
| `services/api/src/test/java/com/verifiedai/integration/solving/CanonicalSolutionIntegrationTest.java` | Author PostgreSQL graph/concurrency/recovery/privacy/version/security tests. |
| `services/api/src/test/java/com/verifiedai/solving/CanonicalTestData.java` | Supply deterministic test-only accepted-artifact/aggregate builders. |
| `services/api/src/test/java/com/verifiedai/solving/application/canonical/CanonicalCandidateContractTest.java` | Preserve math/explanations across all existing accepted contract fixtures. |
| `services/api/src/test/java/com/verifiedai/solving/application/canonical/CanonicalCandidateMapperTest.java` | Cover source/role/provenance/restrictions, hostile input and faithful wrong answers. |
| `services/api/src/test/java/com/verifiedai/solving/application/canonical/CanonicalMathCodecTest.java` | Cover exact v1 AST round-trip, bounded parsing and Jackson 2/3 boundary. |
| `services/api/src/test/java/com/verifiedai/solving/application/canonical/CanonicalSolutionServiceTest.java` | Cover authorization, reuse, typed rejection, review state, recovery and metrics. |
| `services/api/src/test/java/com/verifiedai/solving/domain/model/canonical/CanonicalSolutionTest.java` | Cover immutable aggregate, graph and role/version/source invariants. |
| `services/api/src/test/java/com/verifiedai/solving/domain/model/canonical/MathStatementTest.java` | Cover lexemes, bounds, arity, symbols and unchanged mathematical structure. |
