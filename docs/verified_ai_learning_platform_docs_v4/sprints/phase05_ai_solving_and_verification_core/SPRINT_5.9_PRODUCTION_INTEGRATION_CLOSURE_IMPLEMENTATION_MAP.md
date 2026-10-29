# Sprint 5.9 production integration closure — implementation map

Baseline: clean `codex/sprint-5-9-calculus`, HEAD `69f477d` (merged by PR #34).
Fetched origin/main: `9cba8c782e196aec7b3b28cf08c65e3c18c44134`, no source diff
from that HEAD. Work proceeds on `codex/sprint-5-9-production-integration` at main.
No owner changes or applicable AGENTS.md were found. No commit/push/merge authorized.

## Pre-implementation decisions

| Decision | Existing implementation | Proposed change | Reason | Compatibility effect | Risk | Required test |
| --- | --- | --- | --- | --- | --- | --- |
| Source admission | v1 text parse correction | Add authenticated typed calculus revision path, problem-parse-v2 | v1 lacks approach/domain metadata | v1 entry point unchanged; no model prompt activation | provenance substitution | correction/evidence/revision tests |
| Canonical source | JSONB with independent revision/version | canonical-problem-v2 in same aggregate | durable typed AST and explicit calculus metadata | historical v1 unchanged, no source table duplication | stale source | schema and PostgreSQL tests |
| Candidate | v1 accepted immutable artifact | solution-candidate-v2, source metadata absent from candidate | proposal cannot redefine source | parallel validation and projection | downgrade/lineage confusion | candidate/projection tests |
| Canonical solution | deterministic accepted projection v1 | canonical-solution-v2 / candidate-normalizer-v2 | preserve generation/step identity with explicit version | same repository, v1 reads retained | historical decoder | projection/retrieval tests |
| Planning | planner-v1 catalog without finite limit | calculus policy v1, explicit FINITE_LIMIT mapping | wire calculus method is a separate contract | v1 policy and methods unchanged | accidental escalation | planner/flag tests |
| Transport | bounded HTTP/1.1 executor | narrow typed calculus port, shared transport | existing worker is retained | v1 endpoint stays distinct | readiness mismatch | real HTTP and malformed result tests |
| Evidence | V021 immutable JSON aggregate | explicitly tagged typed calculus evidence variant | certificate and interval scope must survive publication | same atomic aggregate/FKs/privacy lifecycle | version confusion | immutable roundtrip/export tests |
| Transactions | read solutions before source locks; HTTP outside tx | preserve same prepare/execute/publish ordering | avoid deadlock and stale publication | old flow unchanged | correction race | concurrent correction/replay tests |
| Release | calculus rejected outside local/test | independent owner acceptance and algorithm gate, authenticated readiness | flags alone cannot prove peer readiness | production stays prohibited | asymmetric flags | readiness/rollback tests |

New API path is justified because v1 corrections force the existing schema and cannot
represent typed source metadata. No generated explanation is interpreted as math.
No real solver prompt is promoted. Offline candidates use explicit LOCAL_FIXTURE
provenance through the existing accepted artifact/job lifecycle, never invented AI output.
A new migration is justified only for v1-only candidate CHECK constraints; canonical
JSON storage already carries versions. New interfaces remain within existing named
module boundaries. No mathematical algorithm or worker budget changes are intended.

## Boundaries

No overall VERIFIED verdict, composite policy, release evaluation, paid inference or
Phase 6 UI. Production release approval remains separate. Existing Sprint 5.9 isolated
executor and historical evidence/checkpoints are retained.

Status: IMPLEMENTED_PENDING_OWNER_VALIDATION / NOT_RUN_BY_AGENT / CI_PENDING / ROLLOUT_DISABLED.
The resume pass preserved all interrupted work and completed tests, persistence review,
strict source/candidate version separation, authenticated readiness and owner handoff.

## Version and ownership decisions

- problem-parse-v2 is an additive USER correction contract with existing recognition
  evidence and review lineage. Existing AI/model parse admission stays v1.
- canonical-problem-v2 uses the existing owned table, identity and monotonically selected
  canonical revision. Its acceptedParse and calculus metadata remain source-owned.
- solution-candidate-v2 binds canonical revision and proposes only an expression.
  canonical-solution-v2 / candidate-normalizer-v2 project accepted artifacts in the same
  repository. Primary/secondary origin, parent and stable step IDs are preserved.
- verification-planner-calculus-v1 / calculus-capabilities-v1 explicitly select derivative,
  integral-representative or FINITE_LIMIT methods; generic v1 execution remains separate.
- calculus-execution-v1 / calculus-execution-result-v1 / bounded-calculus-v1 are unchanged.
  The authorized assembler binds a task-v1 wire correlation scope to actual source and
  solution identity/revisions; actual source/candidate versions persist in calculus-evidence-v1.
- The existing V021 aggregate carries a tagged typed calculus result variant. Applied
  restrictions retain bounded ASTs through explicit persistence/export serialization.
  Legacy v1 field shape is retained. V022 evolves only v1-only CHECK constraints, keeping
  immutable triggers, composite FKs, unique idempotency and whole-source deletion semantics.
- Independent source/schema validation uses a small bounded reader of exactly three bundled
  local v2 schemas. No dependency, remote schema resolver or expression-text evaluator is added.

## Transaction, release and mathematical guarantees

The owner-scoped canonical solution read completes before source locks. Authenticated peer
readiness and HTTP execution run outside transactions. Publication locks the owner/session,
rechecks source and plan, then inserts or reuses the immutable aggregate. Concurrent retries
can compute twice but publish once. Corrections produce stale publication; foreign-user or
deleted-account requests fail authorization. Tests exercise these through real PostgreSQL
and the real Python worker, rather than handcrafted evidence insertion.

Source admission requires one REAL variable, explicit open interval and method metadata;
it rejects free-text obligations and inconsistent normalized ASTs. Exact differentiation,
antiderivative representative and rational limit proofs reuse the existing bounded engine.
Numerical diagnostics remain INCONCLUSIVE without proof. No overall VERIFIED status exists.

Java calculus execution, Java owner acceptance, offline fixture admission and Python calculus
flags default false. Both runtimes retain local/test-only activation guards. Compatible
readiness is mandatory. Real-model/secondary-generation release remains separately gated.
Configuration is startup-bound: rollback applies false flags and stops/restarts enabled
instances; history remains readable and no down migration is needed. V021 schema downgrade
with v2 rows is unsupported. The evidence parser's envelope depth becomes80 to contain the
unchanged depth32 AST, while its byte and mathematical/worker limits stay unchanged.

## Test inventory and handoff

New source/candidate/planner/transport/controller tests, an actual source-to-Python-to-evidence
PostgreSQL integration suite and a V021-to-V022 migration test are authored. Existing v1,
privacy, architecture, worker/resource and all calculus-v1 fixtures remain regressions.
The closure report contains the A–H matrix, exact one-block commands, environmental
prerequisites, mathematical boundaries and pending evidence ledger. Every new test is
NOT_RUN_BY_AGENT. No compilation, build, lint, formatter, validator, Docker, application
startup, inference or test was executed by the agent. No commit/push/deployment occurred.

## Actual changed-file inventory

Owner iOS regression repair: `apps/ios/VerifiedAI/Features/ProblemHistory/Presentation/History/ProblemHistoryView.swift`
excludes the history panel's decorative border overlay from hit testing. Existing UI
coverage retains the actual history-row tap and required navigation outcome; the full owner
iOS run 20261029-190120 passed all 104 unit and 10 UI tests after this repair.

### Quality and CI

| Actual file | Purpose |
| --- | --- |
| `.github/workflows/math-verifier-ci.yml` | Trigger verifier checks for new v2 schema files. |
| `scripts/quality/check_contracts.py` | Register new source/canonical/candidate schema inventory. |

### Documentation

| Actual file | Purpose |
| --- | --- |
| `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/ai/27_VERIFICATION_ENGINE.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/security/35_SECURITY_THREAT_MODEL.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_EXECUTION_REPORT.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_IMPLEMENTATION_MAP.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_EXECUTION_REPORT.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_IMPLEMENTATION_MAP.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |
| `services/math-verifier/README.md` | Record closure decisions/pending evidence, preserve historical checkpoints and synchronize affected contracts. |

### Shared contracts and fixtures

| Actual file | Purpose |
| --- | --- |
| `packages/contracts/openapi/internal-math-verifier.yaml` | Document authenticated fixed-version calculus readiness. |
| `packages/contracts/openapi/public-api.yaml` | Additive v2 correction route and versioned parse/canonical/candidate response alternatives. |
| `packages/schemas/canonical-problem-v2.schema.json` | Strict versioned canonical source snapshot contract. |
| `packages/schemas/problem-parse-v2.schema.json` | Strict typed calculus USER parse contract. |
| `packages/schemas/solution-candidate-v2.schema.json` | Strict proposal-only calculus candidate contract. |
| `packages/test-fixtures/verification/calculus-integration-v1/cases.json` | Manual derivative, integral and finite/numerical limit source-to-worker fixtures. |

### Problem and canonicalization

| Actual file | Purpose |
| --- | --- |
| `services/api/src/main/java/com/verifiedai/problem/api/parse/ProblemParseController.java` | Add strict authenticated typed calculus revision endpoint. |
| `services/api/src/main/java/com/verifiedai/problem/application/canonicalization/CanonicalProblemApplicationService.java` | Select and persist v2 source snapshots in the existing canonical aggregate. |
| `services/api/src/main/java/com/verifiedai/problem/application/canonicalization/CanonicalProblemBuilder.java` | Build v2 only from accepted typed USER source with matching normalized AST. |
| `services/api/src/main/java/com/verifiedai/problem/application/parse/ProblemParseCorrectionApplicationService.java` | Admit v2 through owner/base/evidence/rate/idempotency lifecycle; account-before-session locking. |
| `services/api/src/main/java/com/verifiedai/problem/application/parse/ProblemParseCorrectionDiff.java` | Track calculus metadata edits as correction changes. |
| `services/api/src/main/java/com/verifiedai/problem/application/parse/ProblemParseDocumentValidator.java` | Use strict v2 validation without v1 sanitization. |
| `services/api/src/main/java/com/verifiedai/problem/application/parse/ProblemParseNormalizer.java` | Validate v2 metadata and reuse existing evidence/taxonomy/review normalization. |
| `services/api/src/main/java/com/verifiedai/problem/application/solving/CalculusSourceDocument.java` | Independent versioned calculus source semantics and authority checks. |
| `services/api/src/main/java/com/verifiedai/problem/application/solving/SolveSourceAccess.java` | Expose the selected version-matched v1/v2 owned canonical snapshot. |
| `services/api/src/main/java/com/verifiedai/problem/application/session/ProblemSessionLifecyclePolicy.java` | Permit PARSED to REVIEW_REQUIRED only through the validated user-correction transition policy. |

### Shared contract boundary

| Actual file | Purpose |
| --- | --- |
| `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java` | Bounded structural checks of three allowlisted bundled v2 schemas with local refs only. |
| `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/package-info.java` | Expose the bounded shared-kernel contracts named interface. |

### Solving and candidates

| Actual file | Purpose |
| --- | --- |
| `services/api/src/main/java/com/verifiedai/solving/application/CalculusCandidateValidator.java` | Bind bounded proposed calculus expressions, steps and references to source revisions. |
| `services/api/src/main/java/com/verifiedai/solving/application/PrimarySolveService.java` | Add disabled local/test offline fixture admission through actual job/candidate acceptance; reject unapproved v2 model dispatch. |
| `services/api/src/main/java/com/verifiedai/solving/application/SolutionCandidateValidator.java` | Dispatch v2 separately and reject mixed v1/v2 source/candidate documents. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/CanonicalCandidateMapper.java` | Project explicitly matched candidate/representation versions and retain provenance. |
| `services/api/src/main/java/com/verifiedai/solving/application/canonical/CanonicalSolutionService.java` | Version-aware materialization, recovery, current and historical reads. |
| `services/api/src/main/java/com/verifiedai/solving/domain/model/SolutionCandidate.java` | Add explicit v2 canonical revision while retaining v1 JSON and constructor compatibility. |
| `services/api/src/main/java/com/verifiedai/solving/domain/model/canonical/CanonicalSolution.java` | Version-aware v2 vocabulary/provenance, stable identity and task invariants. |
| `services/api/src/main/java/com/verifiedai/solving/infrastructure/persistence/JdbcCanonicalSolutionRepository.java` | Read v2 projections and recover only matching accepted-artifact versions. |
| `services/api/src/main/java/com/verifiedai/solving/infrastructure/persistence/SolveStore.java` | Version-tag actual fixture jobs/candidate rows in the existing lifecycle. |

### Verification planning, execution and persistence

| Actual file | Purpose |
| --- | --- |
| `services/api/src/main/java/com/verifiedai/verification/application/CalculusExecutor.java` | Narrow typed calculus execution/readiness port. |
| `services/api/src/main/java/com/verifiedai/verification/application/VerificationExecutionService.java` | Assemble authorized calculus request, execute outside transactions and recheck before immutable publication. |
| `services/api/src/main/java/com/verifiedai/verification/application/VerificationPlanningService.java` | Check peer readiness outside locks and validate version-matched parent lineage. |
| `services/api/src/main/java/com/verifiedai/verification/application/VerificationSourceReader.java` | Decode v2 calculus metadata exclusively from the authorized canonical source. |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/CalculusEvidence.java` | Tag calculus evidence and preserve interval, approach, restrictions and proof/non-proof result. |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/MathematicalExecution.java` | Reject FINITE_LIMIT in generic v1 execution DTOs. |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/PlanningSource.java` | Add explicit typed optional calculus metadata and versioned task vocabulary. |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationEvidence.java` | Accept exactly one version-consistent typed result variant and verify calculus scope binding. |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationMethod.java` | Add FINITE_LIMIT identity/purpose without enabling it in generic v1 execution. |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationRun.java` | Check method uniqueness across legacy and calculus evidence variants. |
| `services/api/src/main/java/com/verifiedai/verification/domain/policy/VerificationCapabilities.java` | Independent owner/environment/algorithm/readiness calculus gating. |
| `services/api/src/main/java/com/verifiedai/verification/domain/policy/VerificationPlanner.java` | Deterministic calculus-specific policy with explicit method mapping. |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/http/HttpMathematicalExecutor.java` | Reuse bounded HTTP/1.1 client, semaphore and subscriber for readiness and calculus. |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/persistence/EvidenceMathJson.java` | Strict bounded restriction AST persistence and legacy evidence JSON-shape mixin. |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/persistence/JdbcVerificationRunRepository.java` | Register typed restriction AST/mixin codec and bounded aggregate envelope depth. |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/privacy/VerificationLifecycleContributor.java` | Preserve textual UUIDs, typed ASTs and v1 shape during existing privacy export. |

### Migration and configuration

| Actual file | Purpose |
| --- | --- |
| `services/api/src/main/resources/application.yml` | Explicit disabled calculus, acceptance and offline fixture defaults. |
| `services/api/src/main/resources/db/migration/platform/V022__add_versioned_calculus_integration.sql` | Additive parallel-version CHECK evolution, retaining old data/FKs/triggers. |

### Tests

| Actual file | Purpose |
| --- | --- |
| `services/api/src/test/java/com/verifiedai/calculus/CalculusCandidatePlanningTest.java` | Version/role/provenance, candidate authority, planning and flag tests. |
| `services/api/src/test/java/com/verifiedai/calculus/CalculusHttpHarness.java` | Owner-opted real Python HTTP process lifecycle for integration tests. |
| `services/api/src/test/java/com/verifiedai/calculus/CalculusIntegrationData.java` | Manually authored integration source/candidate data shared by Java tests. |
| `services/api/src/test/java/com/verifiedai/integration/verification/CalculusMigrationIntegrationTest.java` | Real V021-to-V022 upgrade with unchanged old source and immutable triggers. |
| `services/api/src/test/java/com/verifiedai/integration/verification/CalculusProductionIntegrationTest.java` | Real source admission through worker/publication; exact/non-proof, races, privacy, idempotency and rollback. |
| `services/api/src/test/java/com/verifiedai/problem/api/parse/CalculusRevisionControllerTest.java` | Strict raw duplicate-key handling and authenticated command identity tests. |
| `services/api/src/test/java/com/verifiedai/problem/api/parse/ProblemParseControllerTest.java` | Add authentication regression for the new public source admission route. |
| `services/api/src/test/java/com/verifiedai/problem/application/canonicalization/CalculusSourceContractTest.java` | Canonical source construction and adversarial metadata/schema tests. |
| `services/api/src/test/java/com/verifiedai/problem/application/session/ProblemSessionLifecyclePolicyTest.java` | Correction-specific review transition, recovery and rejection of other terminal regressions. |
| `services/api/src/test/java/com/verifiedai/platform/FlywayMigrationTest.java` | Assert V022's combined versioned canonical constraint and retained revision, foreign-key and uniqueness constraints. |
| `services/api/src/test/java/com/verifiedai/verification/application/VerificationSourceReaderTest.java` | Reject calculus and unknown problem/task vocabulary in v1 sources at planning and execution reads. |
| `services/api/src/test/java/com/verifiedai/solving/application/canonical/CanonicalSolutionServiceTest.java` | Distinguish unknown candidate schema from v2-labeled legacy content and ensure neither produces a projection. |
| `services/api/src/test/java/com/verifiedai/verification/infrastructure/http/CalculusHttpExecutorTest.java` | HTTP/1.1, bounded invalid responses, forged scope and readiness/flag guards. |
| `services/api/src/test/java/com/verifiedai/verification/infrastructure/EvidenceMathJsonTest.java` | Embedded restriction AST roundtrip, sibling preservation and strict malformed/trailing-token rejection. |
| `services/math-verifier/tests/contract/test_calculus_integration_contract.py` | Independent Draft2020-12 typed source fixtures and readiness authentication/flag tests. |

### Python verifier

| Actual file | Purpose |
| --- | --- |
| `services/math-verifier/app/api/routes/verify.py` | Add internal-token authenticated actual flag/version readiness response. |
| `services/math-verifier/app/main.py` | Keep readiness operation name on the sanitized logging allowlist. |

## Remaining evidence and deferred work

LOCAL_OWNER_GATES_PASSED for automated A–H: Python 189; backend 592 plus two intentional
paid-model skips; iOS 104 unit and 10 UI tests; all Gate H checks. Evidence and exact log
paths are recorded in the execution report. The evidence-only documentation refresh passed owner docs-check
in 20261030-000148 (298 files, exit zero). CI_PENDING for the reviewed commit and RELEASE_APPROVAL_PENDING /
ROLLOUT_DISABLED remain; local passing counts do not claim hosted or production acceptance.
The existing isolated PARTIAL report is not erased. Model release, composite policy,
overall arbitration, release evaluation and Phase6 UI remain separately owned.

See [closure execution report](SPRINT_5.9_PRODUCTION_INTEGRATION_CLOSURE_EXECUTION_REPORT.md).
