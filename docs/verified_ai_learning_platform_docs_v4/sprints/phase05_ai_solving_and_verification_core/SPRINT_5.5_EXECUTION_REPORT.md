# Sprint 5.5 — Execution report and owner acceptance

Status: **SPRINT_5.5_BACKEND_FIXTURE_ACCEPTANCE_PASS**. Owner evidence accepted for Gates A–F
within the backend, repository-quality and local-fixture scope described below.
No build, test, linter, formatter, validator, evaluation runner, Docker, inference, dependency
installation, deployment or paid request was executed by the implementation agent.
After accepting the owner evidence, the owner explicitly authorized a commit and normal push of
this sprint on the existing feature branch; the scope is recorded in the handoff below.
Owner full API regression: 434 tests, **432 passed, 2 deliberately skipped**, zero failures/errors.
Gate G remains REAL_SECONDARY_MODEL_EVALUATION_BLOCKED. This is not a mathematical correctness
verdict, real-model quality/independence acceptance, Phase 5 completion or production approval.

## Baseline and protected work

2026-10-27: clean branch `feature/sprint-5-2-prompt-registry-governance`, HEAD
`62b2f7f3e31954de612773631d4d9d8e3d0468a8`. No pre-existing local edits.
Supplied reference main `904f44df4b70b407ad96e0cc6864d283f64321f8` differs; no checkout/merge/reset.
Primary source, API, iOS, Qwen transport, immutable solver/v001 and solver/v002, and V001–V018
were preserved. Historical owner API 389 PASS / 2 real-model SKIP, iOS 111 PASS, verifier 20 PASS
are baseline evidence only. Sprint 5.4-RM real acceptance remains BLOCKED; extended UI manual
acceptance remains PARTIAL. Current Sprint 5.5 owner Gates A–F evidence is recorded below;
new iOS/verifier runs and GitHub CI results are not supplied in this acceptance transcript.

## Implemented vertical slice

The owning module is `solving`. A separately switchable worker polls completed primary jobs
with immutable candidates and no decision for the current policy. PostgreSQL is the trigger
source; no in-memory callback is needed. Polling and inference are disabled by default.
`evaluate(user, session, primaryJob)` is also an internal, ownership-checked application command.
No public secondary endpoint, iOS projection, arbitration or deterministic verifier was added.

Risk evaluation precedes route lookup. The dedicated source-only builder creates AiSolveRequest;
SOLVE + SECONDARY_SOLVER selects a separately governed route, then the existing gateway reserves
usage/budget, enforces deadlines/response bounds and calls the installed adapter. The accepted
candidate uses the unchanged solution-candidate-v1 validator. Candidate, provenance and structured
blind-agreement-v1 evidence commit atomically with job completion, subject to source/deadline/state
fences. The primary job and its public GENERATED_UNVERIFIED/REVIEW_REQUIRED semantics never change.

## Policy and versioning

`secondary-escalation-v1-qcN`, where N is configured basis points (0..1000, default 0).
Material rule changes require a new base version; sampling changes alter the version automatically.
Priority: stale source -> BLOCKED; unsupported -> SKIP; missing primary evidence ->
INSUFFICIENT_EVIDENCE; no primary ambiguity/unresolved issue and not sampled -> SKIP;
disabled execution / missing approved distinct model / insufficient configured allocation -> BLOCKED;
otherwise ESCALATE with PRIMARY_UNRESOLVED or BOUNDED_QC_SAMPLE. Entitlement failures block eligible
escalations. Remaining dynamic quotas are enforced by the authoritative spend guard at dispatch,
so an initially queued job can still finish POLICY_BLOCKED/BUDGET_EXCEEDED.

Sampling hashes canonical identity + policy version with SHA-256; retries cannot redraw it.
No LLM confidence, invented difficulty, assessment risk, verification coverage or classification
fields were added. Existing source eligibility requires a supported, selected, review-free parse.
Basic-solve entitlement is rechecked using the existing billing authority; this sprint creates no
new premium entitlement. A blocked decision is terminal under that policy version: enabling a flag
does not silently requeue it. A deliberate versioned policy change may reconsider previously
unattempted candidates; a database unique index forbids a second dispatch of an attempted candidate,
including OUTCOME_UNKNOWN, cancellation after claim and policy changes. RETRYABLE_FAILURE records
a provider classification, not permission to replay. Maximum dispatches per accepted primary: one.

## Blindness and model independence

BlindSecondaryRequestBuilder.build accepts only SolveSource, not SolveView, SolutionCandidate,
transcript, unrestricted client JSON or comparison output. It has no store dependency, validates
canonical lineage and rejects non-canonical top-level fields. Canonical source fields alone cross
the execution boundary. Primary artifacts are read for policy signals and **after** secondary
execution for comparison; they are never added to the request. Execution metadata contains IDs,
route role and parent operation linkage, with no primary mathematical content. Locale comes from
server-owned app.solving.language (tr/en). Student source text remains untrusted data.

The only implemented route approval is `local-fixture-secondary-solver-v1` under explicit
app.ai.secondary-fixture-enabled, local/test environment and durable ledger. It uses a distinct
prompt and synthetic generator defined in test sources, which reads only canonical input.
Production artifacts contain no solver fixture implementation. Fixture evidence is labeled
SYNTHETIC_SOURCE_BLIND_ONLY: it proves neither model-level/statistical independence nor quality.

No real secondary model is approved or configurable through this fixture flag. A future approved
AiSecondaryRoute implementation can use the existing AiRoutePlan, adapter and spend ports without
rewriting the solving workflow. It must supply exact weights/model revision, tokenizer/runtime,
provider/runtime separation, privacy/release/health approval, bounded pricing/allocation and
independence evidence. Different names, prompts or seeds do not establish different weights;
shared training/failure-mode risk must remain explicit. Promotion requires a new governed prompt
release/version; v001 is immutable LOCAL_ONLY and cannot authorize a real provider.

## Comparison contract and truth boundary

`exact-visible-answer-v1` produces AGREEMENT, DISAGREEMENT, INDETERMINATE,
INCOMPATIBLE_ASSUMPTIONS or STALE_LINEAGE. Same-source identical structured answers are labeled
structural agreement. Bounded decimal literals and division-only exact rational trees compare
using BigInteger fractions, including 0.5 = 1/2; no float tolerance or general symbolic engine exists.
Different exact numeric expression results, or different exact RHS values for the same isolated
variable/equality, provide disagreement evidence. Different inequality thresholds, unsupported
numeric operations, different symbolic forms, unresolved issues or unknown equivalence remain
indeterminate. No claim about equation solution-set completeness is made.

Assumption, constraint and derived restriction IDs must agree; stale lineage is rejected.
Method equality and step counts are descriptive metadata, not proof-step validation. Even identical
wrong answers produce only agreement evidence. mathematicalVerificationPerformed is always false;
there is no verification verdict field or VERIFIED assignment. Sprint 5.6 can consume this internal
lineage-bound evidence, 5.7+ supplies deterministic verification, 5.11 owns future arbitration.

## Persistence, recovery and privacy

V019 was the next unused local migration. It adds secondary_solve_jobs and immutable
secondary_solution_evidence, a composite ownership/primary-source FK, unique primary/policy identity,
one-dispatch partial uniqueness, due/recovery/owner indexes, bounded JSON, and pinned job identity /
state-transition guards. Primary candidate linkage is immutable; canonical data is referenced,
not duplicated in job diagnostics. Actual secondary provenance and ledger IDs are retained.

Lifecycle: decision -> NOT_REQUIRED / INSUFFICIENT_EVIDENCE / POLICY_BLOCKED / STALE / QUEUED;
QUEUED -> RUNNING (attempt 1) -> GENERATED_UNVERIFIED / POLICY_BLOCKED / RETRYABLE_FAILURE /
TERMINAL_FAILURE / OUTCOME_UNKNOWN / STALE. QUEUED or RUNNING can be CANCELLED; expired queued
jobs become TERMINAL_FAILURE. Expired running jobs become OUTCOME_UNKNOWN and are never replayed.
The provider allowance is 30 s, queue/job deadline 60 s, output 65536 bytes, one attempt, no fallback.
A PostgreSQL advisory transaction lock plus conditional update enforces global concurrency (default 2,
configurable 1..10) and one running job per user. The sole approved fixture provider therefore also
shares that global provider bound. No database transaction spans provider execution.

Source ownership/entitlement are checked at decision and dispatch; source lineage is checked again
at completion and read. Route changes before execution block dispatch. Late/stale/cancelled output
cannot promote current evidence. Persistence failure after a provider response leaves RUNNING for
unknown-outcome recovery. Cancellation/deletion fences persistence, not remote billing or refunds.
A complete process restart needs the secondary worker re-enabled to recover expired jobs. Disabling
only secondary.enabled stops inference while an enabled worker may still recover/record decisions.

Account export includes owned secondary decisions, candidates, provenance and comparison. Existing
solution deletion cascades to all new tables, including source/session deletion. Data follows the
same account/source retention as primary candidates; no new indefinite training/audit copy is made.
Internal get/cancel require matching user/session and active account; stale reads hide agreement.
No endpoint exposes secondary reasoning. Logs contain no raw question/output/student identifiers;
metrics have only bounded policy/reason/status/outcome dimensions.

## Cost, telemetry and operations

Defaults: secondary enabled=false, worker-enabled=false, sample=0, max-cost-micros=0;
AI secondary fixture flag=false; real providers unchanged and disabled by default.
Free fixtures still create separate usage-ledger operations, route/prompt/model attribution and
correlation IDs, with no spend rows. Paid calls require the existing per-request, tier, user,
session, provider and global reservations. V019 also persists original operation ceilings.
Future paid secondary reservations must reference a successful same-user/session primary ledger
operation and consume its remaining original OPERATION quota as well as their own quota.
Old paid operations lacking a persisted ceiling fail closed; a free primary creates no paid ceiling.
Reported/unknown usage never automatically releases committed ceilings. No production allocation
or provider approval was fabricated. A configuration template is not spending authorization.

Only primary issues plus the configured 0..10% QC sample can trigger; the total escalation fraction
is unknown until owner measurements, and may exceed QC percentage when issue rates are high.
There is no inference cache: the durable primary/policy decision and one-dispatch constraint prevent
redundant work without reusing evidence across sources. Existing health/attempt guards protect the
gateway; the sole fixture needs no network circuit breaker. Real circuit/health/model gates remain
a promotion prerequisite, not an implied approval from adapter existence.

Counters cover decisions/reasons, execution outcomes including recovered unknowns, agreement outcomes,
blocked budgets and worker failures. Secondary latency publishes histogram buckets for p50/p95;
secondary input/output tokens and committed ceilings are recorded when available. Gateway metrics
and ai_usage_records/ai_spend_attempts retain route/provider/usage details and authoritative financial
commitments. Logical cost joins primary_job_id to both operation IDs; reported estimates must not
be summed with reservation ceilings as if both were separate charges.

Rollback: disable secondary worker and execution flags, leave V019/evidence intact, reconcile
RUNNING/OUTCOME_UNKNOWN from the ledger before any further operator action. Old primary binaries
can ignore the additive tables/nullable ceiling column; reverting loses aggregate-cap enforcement,
so all secondary dispatch must remain disabled. Never reverse this migration by dropping evidence
or resetting a production database. Roll back on unexpected dispatch volume/spend, primary leakage,
false-verification presentation, repeated schema failures, excess unknown outcomes, privacy failure,
or owner-defined p95/error thresholds. Quality/latency thresholds need measured acceptance data.

## Tests authored and owner acceptance

| Test | Coverage |
| --- | --- |
| SecondaryEscalationPolicyTest | Priority, skip, unresolved escalation, unavailable model/budget, disabled/stale/unsupported/missing evidence, stable bounded sampling/version. |
| BlindSecondaryRequestBuilderTest | Canonical-only input, primary secret exclusion, contaminated root rejection, dependency/signature boundary, review block. |
| BlindAgreementAnalyzerTest | Exact/decimal/rational agreement, incompatible exact results, no invented tolerance, symbolic/inequality indeterminate, assumptions/lineage/schema, no correctness verdict. |
| SecondaryOfflineEvaluationTest | Executes versioned synthetic comparison/blindness fixture cases and checks prompt digest linkage; no inference. |
| SecondaryRouteEligibilityTest | Explicit fixture approval, environment/ledger gate, distinct identity, zero cost, no fallback, wrong role block. |
| SecondaryPromptArtifactTest | Actual classpath prompt/schema resolution, SHA-256 integrity and release/training restrictions. |
| SecondaryBlindnessArchitectureTest | Builder cannot depend on primary candidate, persistence or API. |
| SecondarySolveIntegrationTest | Durable discovery, fixture E2E and ledger separation, Boot-managed route JSON round-trip including Duration, skip/issues, duplicate triggers/workers, concurrency limits, timeout/restart/cancel/staleness, DB-failure recovery, immutability/ownership/export/deletion, no transaction across inference. |
| SecondarySolveBlockedIntegrationTest | Existing primary adapter never implies approved secondary; no extra gateway call. |
| AiSpendGuardIntegrationTest (extended) | Shared original operation quota, separate stage ledger attribution, unknown spend retained and cross-owner parent quota rejected. |

Owner Gate A: **24/24 passed** (SecondaryRouteEligibilityTest 4, BlindAgreementAnalyzerTest 9,
BlindSecondaryRequestBuilderTest 4, SecondaryEscalationPolicyTest 6, SecondaryOfflineEvaluationTest 1).
Corrected full Gate B: **24/24 passed**. Gate C: **54/54 passed**. Gate D: **2/2 passed**.
Gate E: **98/98 passed**. Full API regression: **434 total, 432 passed, 2 skipped**, zero
failures/errors. Runs overlap; their counts must not be summed as unique tests.
Agent-executed tests: **none**. Real-model evidence: **none**.
The two full-API skips are QwenRealModelEvaluationTest real-model gates, disabled without explicit
QWEN_OWNER_RUN approval. Unchanged iOS/verifier baselines remain historical (111/20 passing);
no new iOS/verifier output is present. Gate G remains blocked, not reported as a pass.

## Reproducible owner commands — accepted A–F runs

The commands below are retained for reproducibility. Owner results now establish A–F acceptance
for the stated scope; no redundant rerun is requested. Gate G is still blocked. The dated feedback
entries preserve the original failures and corrections; the final acceptance record supersedes
their at-time pending statuses.

Prerequisites: existing dependencies and Maven installed, JDK 21. The Homebrew path below follows
the repository Makefile; substitute your installed JDK 21 path if different. Do not enable a real
model or any paid provider. Gate A/C require no Docker/GPU/provider. Maven still compiles test sources;
that compilation is owner execution. Send the first failing log before broadening scope.

### Gate A — focused, offline unit tests

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd services/api
mvn -Dtest=SecondaryEscalationPolicyTest,BlindSecondaryRequestBuilderTest,BlindAgreementAnalyzerTest,SecondaryRouteEligibilityTest,SecondaryOfflineEvaluationTest test
```

### Gate B — PostgreSQL persistence and spend

Prerequisite: Docker Desktop is already running; Testcontainers starts isolated postgres:16-alpine.
No GPU, model weights, paid API, deployment or application Docker Compose stack is needed.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
mvn -Dtest=SecondarySolveIntegrationTest,SecondarySolveBlockedIntegrationTest,AiSpendGuardIntegrationTest test
```

### Gate C — contracts, prompt integrity, architecture and ownership

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
mvn -Dtest=SecondaryPromptArtifactTest,PromptRegistryValidatorTest,PromptReleasePolicyTest,SolutionCandidateContractTest,SecondaryBlindnessArchitectureTest,AiProviderBoundaryTest,ModularityTest test
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 scripts/quality/check_prompt_immutability.py --base-ref 62b2f7f3e31954de612773631d4d9d8e3d0468a8
make contracts-check docs-check
```

### Gate D — deterministic end-to-end and one degraded demonstration

Prerequisite: Gate B Docker/Testcontainers. The synthetic scenario explicitly completes primary,
checks durable discovery, evaluates policy, executes independent canonical-only secondary output,
persists agreement, checks separate ledgers and unchanged public unverified state. Timeout is the
degraded scenario. These selected methods already run in Gate B; this command is only for separate
review/demo evidence if requested, not a mandatory redundant rerun.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
mvn '-Dtest=SecondarySolveIntegrationTest#fixtureEndToEndPersistsAgreementAndSeparateLedgerWhileClientRemainsUnverified+timeoutRetainsLedgerAndUnknownOutcomeWithoutRetry' test
```

### Gate E — affected regression after focused gates are green

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
mvn -Dtest=PrimarySolveIntegrationTest,PrimarySolveControllerTest,SolutionCandidateValidatorTest,SolverRouteConfigurationTest,SolverModelEligibilityTest,SolverFixtureAdapterTest,VllmSolveProviderAdapterTest,VllmSolvePipelineIntegrationTest,AiRouteSelectorTest,ConfiguredAiRoutePlannerTest,ConfiguredAiModelGatewayTest,AiUsageLedgerIntegrationTest test
```

### Gate F — full regression only after A–E are accepted

Prerequisites: Docker Desktop/Testcontainers for the full API; existing local verifier Python
venv/dependencies; Xcode and the repository iPhone 16 Pro simulator for optional full iOS baseline.
These full gates are owner-controlled. Verifier and iOS source were untouched, so their commands
are optional baseline confirmation rather than a claim they were affected by this change.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
make test-api
make contracts-check docs-check secret-scan lint
AI_EVAL_MODE=local make eval-ai
```

The ingestion evaluation command is the existing ingestion regression, not a secondary-model
quality evaluation; SecondaryOfflineEvaluationTest covers the new deterministic fixture artifact.
Optional unaffected baseline suites:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
make test-verifier
make test-ios
```

### Gate G — optional real secondary model evaluation

**REAL_SECONDARY_MODEL_EVALUATION_BLOCKED**. No activation command is provided because Sprint 5.4-RM
primary acceptance, separate approved secondary identity/runtime, privacy/release/security evidence,
owner-authorized budget and approved evaluation dataset are absent. Owner approval and measured
quality/latency/cost/independence evidence must precede a governed real-route/prompt promotion.
Fixture acceptance must not be promoted into real-model acceptance.

## Owner feedback — Gate A test compilation

2026-10-27 12:22:42 Europe/Istanbul: owner ran the documented Gate A Maven command.
JDK 21 enforcement and main-source compilation completed. Test compilation failed before any
tests executed: five `assertThat(tx.execute(...))` expressions in SecondarySolveIntegrationTest
made Java generic return-type inference ambiguous across AssertJ assertion overloads (10 diagnostics
from five call sites). Maven compiles all test sources even when only unit tests are selected.

Correction: assign each transaction result to an explicit Boolean local before asserting. The
claim/recovery/concurrency assertions and production behavior are unchanged. Only the affected test
source was changed, with this evidence record and required manifest metadata. No agent build, test
or validator was executed. The owner subsequently confirmed the correction with Gate A PASS,
recorded in the next feedback entry.

## Owner feedback — Gate A PASS, Gate B context startup

2026-10-27 12:24:07 Europe/Istanbul: Gate A BUILD SUCCESS, 24 tests, zero failures/errors/skips.
Main and test-source compilation completed. Preserve this acceptance; no Gate A rerun is requested.

2026-10-27 12:24:25 Europe/Istanbul: Gate B BUILD FAILURE, 24 errors across
SecondarySolveBlockedIntegrationTest (1), SecondarySolveIntegrationTest (14) and
AiSpendGuardIntegrationTest (9). The first meaningful failure is SecondarySolveService constructor
parameter 10 requiring a com.fasterxml.jackson.databind.ObjectMapper bean that does not exist.
The owner condition report shows Boot 4 JacksonAutoConfiguration provides jacksonJsonMapper of type
tools.jackson.databind.json.JsonMapper. Repeated context-failure-threshold errors are consequences
of that shared startup failure, not independent domain-test failures. The owner log also records
successful application of all 19 migrations through V019; this does not establish persistence or
ledger behavioral acceptance.

Correction: inject the existing Boot-managed Jackson 3 JsonMapper for typed AiRoutePlan JSON
encoding/decoding. Existing Jackson 2 JsonNode candidate/source validation remains unchanged; no
global mapper bean, new dependency or serializer-policy override was added. Extend the existing E2E
test to read the persisted route with that managed mapper and assert the secondary model, prompt
and 30-second Duration round-trip. No agent build, test, validator, Docker or inference was run.

Focused next command (Docker Desktop/Testcontainers prerequisite):

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=SecondarySolveBlockedIntegrationTest test
```

The owner subsequently passed this startup/blocked-route test and reran Gate B, recorded below.

## Owner feedback — Gate B trigger assertion

2026-10-27 12:26:39 Europe/Istanbul: focused SecondarySolveBlockedIntegrationTest passed,
1 test, zero failures/errors/skips. The mapper/context correction is owner-confirmed.

2026-10-27 12:27:01 Europe/Istanbul: full Gate B ran 24 tests, 1 failure, zero errors/skips:
SecondarySolveBlockedIntegrationTest 1/1 passed; AiSpendGuardIntegrationTest 9/9 passed;
SecondarySolveIntegrationTest 13/14 passed. Gate A remains accepted at 24/24 and is not rerun.
The single failure is databaseConstraintsOwnershipImmutabilityExportAndDeletion expecting
DataIntegrityViolationException when changing a pinned user_id. PostgreSQL's BEFORE UPDATE
protect_secondary_job_identity trigger intentionally rejects that mutation first with SQLSTATE
P0001, translated by Spring as UncategorizedSQLException. The following attempt_number mutation
has the same trigger-before-CHECK ordering and its assertion needed the same correction.

Correction is test-only: assert the actual trigger exception and specific rejection message for
both mutations, then read back the job and assert its original owner and attempt count are intact.
No migration or production protection was weakened. The export/deletion assertions that follow
the previous failure still require owner execution. No agent test/build/validator was run.

Focused next command (Docker Desktop/Testcontainers prerequisite):

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn '-Dtest=SecondarySolveIntegrationTest#databaseConstraintsOwnershipImmutabilityExportAndDeletion' test
```

The owner subsequently passed this focused test, recorded below. Gate C and broader regression
remain pending full Gate B acceptance.

## Owner feedback — Gate B focused correction PASS

2026-10-27 12:28:54 Europe/Istanbul: owner ran
SecondarySolveIntegrationTest#databaseConstraintsOwnershipImmutabilityExportAndDeletion.
BUILD SUCCESS: 1 test, zero failures/errors/skips. This confirms the trigger assertion correction
and the following owner/attempt preservation, export and cascade-deletion assertions in that method.
Gate A remains accepted at 24/24 and is not rerun. No additional source correction is needed from
this output. The agent only recorded owner evidence; no build/test/validator was executed.

Next owner gate (Docker Desktop/Testcontainers prerequisite):

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=SecondarySolveIntegrationTest,SecondarySolveBlockedIntegrationTest,AiSpendGuardIntegrationTest test
```

Only a passing complete rerun accepts Gate B; the prior 23 passes and focused pass are recorded
separately rather than presented as a single completed gate. Gate C awaits that full output.

## Final owner acceptance — Gates A–F PASS

Evidence date: 2026-10-27, Europe/Istanbul. Latest source is owner transcript attachment
`2bdd8a18-d2cf-49ee-81ea-bcfdb8459556/Yapıştırılan metin.txt` (3375 lines); earlier Gate A
evidence is retained in the dated entry above. Only existing owner logs and generated artifacts
were read by the agent. No build, test, validator, evaluator, Docker or inference was rerun.

| Gate | Owner completion time | Evidence | Accepted scope |
| --- | --- | --- | --- |
| A | 12:24:07 | 24 tests, 0 failures/errors/skips | Deterministic policy, blindness, comparison, route eligibility and offline cases. |
| B | 12:30:00 | 24 tests, 0 failures/errors/skips | SecondarySolveIntegrationTest 14, SecondarySolveBlockedIntegrationTest 1, AiSpendGuardIntegrationTest 9; PostgreSQL lifecycle, ledger, ownership/deletion and corrected assertions. |
| C | 12:30:21 | 54 tests, 0 failures/errors/skips; prompt immutability, contract and documentation checks passed | Prompt/schema integrity, Spring Modulith/provider/blindness boundaries, contract compatibility. |
| D | 12:30:46 | 2 tests, 0 failures/errors/skips | Fixture E2E with durable agreement and unchanged unverified client result; timeout/unknown outcome. |
| E | 12:31:06 | 98 tests, 0 failures/errors/skips | Affected primary solver, controller, candidate, Qwen adapter/fake pipeline, routing, gateway and usage-ledger regression. |
| F — API | 12:31:57 | BUILD SUCCESS; 434 total, 432 passed, 2 skipped, 0 failures/errors | Complete API run; all 71 logged per-suite summaries also total 434 tests and 2 skips. |
| F — repository quality | After API run | contract check passed; documentation check passed (288 Markdown files); secret scan passed; lint completed | Owner-executed repository checks against the tested worktree. |
| F — local AI evaluation | After quality checks | Dataset validation PASS; release decision PASS, exitCode 0, no reasons/regressions | LOCAL_FIXTURE_REGRESSION ingestion evaluation: 18 cases, 13 critical. |
| G | Not activated | REAL_SECONDARY_MODEL_EVALUATION_BLOCKED | No real primary/secondary-model acceptance or deployment approval. |

The latest transcript contains no BUILD FAILURE and no test failures/errors. Expected gateway
failure statuses occur in negative tests; the enclosing test summaries pass. All two skipped
full-regression tests belong to QwenRealModelEvaluationTest and remain explicitly gated.
Existing Surefire report directories contain reports from other runs; their directory-wide totals
are not the authoritative result for this run. The recorded total comes from the owner's complete
API command summary, cross-checked against its 71 per-suite summaries, without rerunning tests.

Owner-generated local evaluation artifacts were also inspected:
the local-only ingestion evaluation Markdown summary,
`.generated/evaluations/latest/ingestion-evaluation-report.json` and
`.generated/evaluations/latest/ingestion-release-comparison.json`.
The summary/comparison agree with the owner transcript: baseline production-ingestion-v1,
report SHA-256 `1f088322cd797b7d87f21482d6747e3f80017df31f13f7b44f80ca2fa7bf671b`,
dataset SHA-256 `769befe32df0f8646aec4c5fa49c777bb4b39b990e92fd70af18811f6270a50f`,
decision PASS, no regressions. Connected provider is NOT_REQUESTED; protected holdout remains
BLOCKED_PROTECTED_HOLDOUT. Four critical false-accept/reference counters in the summary are zero.
Near-duplicate synthetic-text warnings were non-blocking in the owner run; this is fixture
regression evidence, not real-model mathematical quality or independence evidence.

Acceptance disposition: backend implementation, migration, deterministic fixtures, architecture,
contracts and local repository gates are owner-passed. Production dispatch remains disabled/zero
budget; no model approval or VERIFIED authority changed. Optional unchanged iOS/verifier runs,
extended UI manual acceptance, real-model evaluation, deployment and remote GitHub CI are outside
the new evidence. No Phase 5 completion or real-model release is inferred.

This acceptance update changes documentation/index/manifest only. The owner's documentation check
passed before this evidence update; manifest metadata has been authored for the updated documents,
but no post-update validator pass is claimed by the agent.

## Handoff/checkpoint

Last completed workstreams: policy, blind input, separate gateway route, immutable prompt/fixtures,
durable lifecycle/migration, comparison, budget/ownership/privacy/observability, tests and owner runbook.
Owner A–F backend/fixture acceptance is complete; no additional test rerun is requested for this
handoff. Sprint 5.6 can consume the internal, owned, lineage-bound non-authoritative evidence.
Any real-model activation or deployment needs its separately approved evidence and budget.
2026-10-27: owner requested pushing the project, authorizing a commit and normal push to
origin/feature/sprint-5-2-prompt-registry-governance. Before committing, read-only remote inspection
confirmed the feature branch at 62b2f7f3e31954de612773631d4d9d8e3d0468a8 and main at
904f44df4b70b407ad96e0cc6864d283f64321f8. Only the 34 sprint implementation/test/document files
are in scope. No branch switch, force push, merge, deployment or main update is authorized by
this handoff. Final commit/remote confirmation belongs to the Git operation result, not a test PASS.
Mathematical correctness, model promotion and Phase 5 completion are not asserted.

## Actual file inventory

The inventory below records only new/modified paths in this sprint. N = new, M = modified.

| State | Path | Responsibility |
| --- | --- | --- |
| M | `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Sprint navigation. |
| M | `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Required documentation inventory metadata. |
| N | `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.5_EXECUTION_REPORT.md` | Owner gates, acceptance, blockers and inventory. |
| N | `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.5_IMPLEMENTATION_MAP.md` | Implementation boundaries and decisions. |
| N | `evaluations/fixtures/secondary-solver/blind-v1.json` | Versioned synthetic offline cases; no model acceptance. |
| M | `services/api/src/main/java/com/verifiedai/ai/application/AiRouteSelector.java` | Explicit provider-neutral secondary route/eligibility boundary. |
| N | `services/api/src/main/java/com/verifiedai/ai/application/AiSecondaryRoute.java` | Explicit provider-neutral secondary route/eligibility boundary. |
| M | `services/api/src/main/java/com/verifiedai/ai/application/PromptRegistry.java` | Immutable secondary prompt registration. |
| M | `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/ConfiguredAiRoutePlanner.java` | Explicit provider-neutral secondary route/eligibility boundary. |
| N | `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/FixtureSecondaryRoute.java` | Explicit provider-neutral secondary route/eligibility boundary. |
| M | `services/api/src/main/java/com/verifiedai/ai/infrastructure/persistence/PostgresAiSpendGuard.java` | Authoritative original-operation aggregate spending cap. |
| N | `services/api/src/main/java/com/verifiedai/solving/application/secondary/BlindAgreementAnalyzer.java` | Conservative versioned agreement evidence. |
| N | `services/api/src/main/java/com/verifiedai/solving/application/secondary/BlindSecondaryRequestBuilder.java` | Canonical-only blind execution input. |
| N | `services/api/src/main/java/com/verifiedai/solving/application/secondary/SecondaryEscalationPolicy.java` | Deterministic conditional escalation. |
| N | `services/api/src/main/java/com/verifiedai/solving/application/secondary/SecondarySolveService.java` | Owned secondary orchestration and safe execution lifecycle. |
| N | `services/api/src/main/java/com/verifiedai/solving/application/secondary/SecondarySolveWorker.java` | Bounded durable primary/secondary polling. |
| N | `services/api/src/main/java/com/verifiedai/solving/domain/model/secondary/SecondaryJob.java` | Typed secondary lifecycle/source/policy identity. |
| N | `services/api/src/main/java/com/verifiedai/solving/domain/model/secondary/SecondaryState.java` | Typed secondary lifecycle/source/policy identity. |
| N | `services/api/src/main/java/com/verifiedai/solving/infrastructure/persistence/SecondarySolveStore.java` | Durable decisions, claims, completion, recovery and evidence. |
| M | `services/api/src/main/java/com/verifiedai/solving/infrastructure/privacy/SolvingLifecycleContributor.java` | Owned evidence export and deletion integration. |
| M | `services/api/src/main/resources/application.yml` | Disabled, zero-budget rollout defaults. |
| N | `services/api/src/main/resources/db/migration/platform/V019__create_secondary_solve_evidence.sql` | Additive lifecycle/evidence and operation-ceiling persistence. |
| N | `services/api/src/main/resources/prompts/secondary-solver/v001.md` | Immutable blind secondary prompt. |
| N | `services/api/src/test/java/com/verifiedai/ai/infrastructure/configuration/SecondaryPromptArtifactTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/ai/infrastructure/configuration/SecondaryRouteEligibilityTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| M | `services/api/src/test/java/com/verifiedai/ai/infrastructure/persistence/AiSpendGuardIntegrationTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/architecture/SecondaryBlindnessArchitectureTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/integration/solving/SecondaryFixtureConfiguration.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/integration/solving/SecondarySolveBlockedIntegrationTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/integration/solving/SecondarySolveIntegrationTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/solving/application/secondary/BlindAgreementAnalyzerTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/solving/application/secondary/BlindSecondaryRequestBuilderTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/solving/application/secondary/SecondaryEscalationPolicyTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
| N | `services/api/src/test/java/com/verifiedai/solving/application/secondary/SecondaryOfflineEvaluationTest.java` | Synthetic/offline test or integration fixture; owner execution only. |
