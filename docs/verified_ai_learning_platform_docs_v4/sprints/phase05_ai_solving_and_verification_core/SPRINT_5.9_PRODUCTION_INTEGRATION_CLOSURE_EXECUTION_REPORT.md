# Sprint 5.9 production integration closure — execution report

## Baseline and preservation

Repository: coskunburak/verified_ai. Starting branch codex/sprint-5-9-calculus at
69f477d; working tree/index clean. Fetch confirmed origin/main at
9cba8c782e196aec7b3b28cf08c65e3c18c44134 (PR #34), with no source diff from the
merged feature. Integration branch: codex/sprint-5-9-production-integration at that
baseline. No applicable AGENTS.md was found. No commit, push, merge, reset, stash,
rebase or deployment was performed. Interrupted work was resumed in place; all existing
changes, untracked source, tests and migrations were preserved. The staged diff is empty.

The isolated executor's historical owner results belong to PR #34. They are preserved
in the original Sprint 5.9 report and are not acceptance evidence for this new working tree.

## Implemented application path

1. Authenticated POST /api/v1/problem-sessions/{sessionId}/calculus-parse-revisions
   admits a strict problem-parse-v2 USER correction against the selected base revision.
   Raw duplicate keys and extra envelope fields are rejected before map conversion.
   Active ownership, entitlement, existing recognition evidence and its revision/block IDs,
   correction rate limits, review policy and idempotency remain in the original lifecycle.
2. Explicit canonicalization writes canonical-problem-v2 in canonical_problems. The typed
   AST must match the existing bounded parser's normalized expression AST. The source
   interval, variable, approach, direction and representative scope come exclusively from
   the accepted source. Canonical identity/revision remain authoritative relational columns.
3. PrimarySolveService.acceptCalculusFixture provides an explicitly enabled local/test
   offline path through solve_jobs and solution_candidates. The caller supplies an
   untrusted, validated fixture proposal, not an execution request. Actual LOCAL_FIXTURE
   provenance, a new operation ID and immutable job lineage are recorded. No AI gateway,
   model inference, usage charge or invented model/ledger evidence is involved.
4. CanonicalSolutions.materialize projects the accepted candidate to canonical-solution-v2
   with deterministic identity, ordered steps and original provenance. It remains
   GENERATED_UNVERIFIED. Both primary and secondary accepted-artifact roles have versioned
   projection validation; no real-model or secondary-generation release is activated.
5. VerificationPlanning reads the owned solution before owner/source locks, checks authenticated
   peer readiness outside transactions and selects one explicit calculus method under
   verification-planner-calculus-v1 and calculus-capabilities-v1. No mathematics is executed
   by the planner. Unknown/historical/reviewed/mixed-version sources cannot become executable.
6. VerificationExecution prepares the current source and idempotency entry, releases its
   transaction, calls the shared bounded HTTP/1.1 adapter through CalculusExecutor, validates
   the complete response against the server-assembled request, then reauthorizes/locks,
   rereads the current source and recomputes the plan before immutable publication.
7. VerificationRun retains its existing aggregate and foreign keys. A tagged calculus-evidence-v1
   variant stores actual source/candidate versions, interval/approach/representative scope,
   typed applied restrictions, method-specific result, exact proof or non-proof diagnostic.
   A wrong result identity becomes EXECUTION_FAILED/INVALID_RESPONSE with no certificate.

## Version compatibility

| Boundary | Historical accepted version | New path | Compatibility strategy |
| --- | --- | --- | --- |
| ProblemParse | problem-parse-v1 | problem-parse-v2 | Additive authenticated USER correction; existing model worker/prompt admission remains v1 |
| CanonicalProblem | canonical-problem-v1 / verifier-input-v1 | canonical-problem-v2 / calculus-source-normalizer-v1 | Same durable aggregate/revisions; v2 verifier_input_jsonb retains the versioned source snapshot, not an executable request |
| SolutionCandidate | solution-candidate-v1 / candidateVersion 1 | solution-candidate-v2 / candidateVersion 2 | Strict separate validation, explicit canonical revision and unchanged v1 serialization |
| CanonicalSolution | canonical-solution-v1 / candidate-normalizer-v1 | canonical-solution-v2 / candidate-normalizer-v2 | Parallel deterministic projection, recovery and historical readers in the same repository |
| VerificationPlan | verification-planner-v1 / canonical capabilities | verification-planner-calculus-v1 / calculus-capabilities-v1 | Explicit DERIVATIVE, INTEGRAL and FINITE_LIMIT mappings; generic v1 execution rejects FINITE_LIMIT |
| VerificationExecution | verification-execution-v1 and existing calculus-execution-v1 | Existing calculus request/result contracts unchanged | Internal task-v1 correlation scope is assembled from authorized v2 source/candidate data; it does not acquire source authority |
| VerificationEvidence | V021 mathematical result variant | calculus-evidence-v1 tagged variant in V021 aggregate | Separate typed result; no reinterpretation of old certificates; legacy null calculus property is omitted on persistence/export |

The wire scope identity binds owner/session/canonical/parse identities, parse/canonical
revisions and deterministic solution identity. sourceRevision is the canonical revision;
candidateRevision is 1 for the immutable accepted candidate. Both wire version aliases
remain calculus-task-v1 exactly as published. Actual canonical-problem-v2 and
solution-candidate-v2 identities are retained in durable evidence. Candidate schema
version 2 is distinct from immutable candidate revision 1.

No new canonical-solution JSON Schema is needed: it is a private typed relational
projection with explicit representation/normalizer columns and domain invariants.
No new AI prompt/schema registry activation is needed: the source path is USER correction
and the candidate path is an explicit offline fixture admission. New real-model generation
requires separate schema/prompt/model approval; ordinary v1 solve dispatch rejects v2 sources.

## Mathematics and admission limits

| Method | Supported subset/proof basis | Conservative boundary | Authored evidence |
| --- | --- | --- | --- |
| Derivative backcheck | Existing bounded structural differentiation and exact rational/formal-polynomial identity | Nonzero transcendental residual is inconclusive; supported rational nonidentity can disprove | Polynomial correct/wrong derivative in actual source-to-worker publication tests; existing full calculus fixtures |
| Integral differentiation backcheck | Independently differentiate proposed representative, including additive constants | Certifies the representative on the explicit open interval, not a full family, definite or improper integral | Additive-constant representative through normal candidate/canonical/publication lifecycle |
| Finite limit | Existing rational numerator/denominator orders at rational target with explicit side(s) and punctured neighborhood | No infinite-target, divergence or DNE certificate; original singularities/restrictions retained | Removable discontinuity through real HTTP and immutable evidence |
| Numerical limit diagnostic | Existing eight samples/side, binary64, 1e-8 tolerances and domain checks | Always INCONCLUSIVE, no certificate, no overall VERIFIED | sin(x)^12/100 sampled agreement with 0 remains NUMERICAL_DIAGNOSTIC in durable evidence |

Exactly one declared REAL variable is required. Unknown/integer domains, empty intervals,
missing/mismatched approach metadata, unsupported integral scope, undeclared variables,
noncanonical exact rationals, excessive AST structure and obvious contradictory typed
relations are rejected. A source expression must match its independently parsed normalized
AST; source metadata is never inferred from a display explanation. Free-text assumptions
or constraints are rejected in this minimal v2 path; mathematical restrictions must use the
typed restriction list and source evidence references. More complex domain obstructions
are decided conservatively by the existing worker and cannot produce proof when unresolved.

Worker limits remain unchanged: 3 seconds elapsed, 2 seconds CPU, 512 MiB Linux address
space, two workers/no queue; 64 KiB request, 16 KiB response; existing AST/rational/
polynomial/function budgets. The Java transport shares the existing client, semaphore,
response subscriber, endpoint/token validation and no-redirect HTTP/1.1 policy.

## Database, security, privacy and concurrency review

V022 is additive. It replaces named v1-only canonical kind/version and candidate/job
CHECK constraints with explicit parallel version branches. Old migrations are unchanged;
no old data is updated or reinterpreted. The secondary v1 artifact's 131072-byte storage
limit is retained; v2 uses 65536 bytes. Existing composite solution/source/owner FKs,
immutable triggers, idempotency keys and cascade deletion remain in place. V021 already
supports typed bounded JSON evidence, so no alternate evidence table/repository is added.

EvidenceMathJson explicitly encodes/decodes restriction ASTs and rejects unknown fields.
The evidence parser nesting bound becomes 80 to accommodate the existing depth-32 AST
inside aggregate wrappers; AST validation still caps expression depth at 32, and the
aggregate remains bounded at 64 KiB. This is a persistence-envelope compatibility change,
not a worker/math/resource-limit increase. JSON serialization before privacy-map conversion
preserves UUID strings and avoids native-binary token-buffer conversion. Legacy v1 evidence
keeps its old JSON field count and meaning through a persistence/export mixin.

Network/readiness and mathematical execution occur outside database transactions. Canonical
solution reads finish before source locks. Publication holds short owner/source locks,
rechecks source/plan identity and atomically inserts/reuses one aggregate. Concurrent retries
may compute more than once; they publish one owner/session/key result. A correction during
execution produces STALE_SOURCE; account deletion or foreign-user replay fails authorization.
No source/solution is upgraded to overall VERIFIED. Account export and deletion use the
existing lifecycle contributors and FK cascades, including historical evidence when flags are off.

Readiness is internal-token authenticated and returns fixed contract/algorithm versions and
the actual Python enabled flag. Java additionally requires its own enabled and owner-accepted
flags plus exact supported algorithm and local/test environment. A missing/disabled/incompatible
peer remains unavailable. Production activation is rejected by both runtimes. No public
readiness endpoint, arbitrary expression evaluator or unrestricted SymPy operation is added.
Finite readiness/algorithm/capability/method/outcome/reason metrics contain no raw math,
credentials, arbitrary exception text, owner IDs or candidate bodies.

## Rollout and rollback

Defaults: VERIFICATION_CALCULUS_ENABLED=false, VERIFICATION_CALCULUS_OWNER_ACCEPTED=false,
CALCULUS_FIXTURE_ENABLED=false, MATH_VERIFIER_CALCULUS_ENABLED=false. The exact Java
algorithm remains bounded-calculus-v1. Explicit owner local/test configuration enables only
the controlled validation slice. Production environment guards remain in force even if
flags are mistakenly set. The integration harness sets the scoped test properties and
launches the existing Python app with APP_ENV=test and its calculus flag true.

Configuration is startup-bound, not a new hot-reload system. Rollback sets the Java
calculus flag and Python calculus flag false and restarts/stops all old enabled instances.
Once the disabled configuration is applied, new execution is blocked;
source/candidate/canonical/evidence history remains readable. No down migration, source
rewrite or deletion is required. Existing v1 arithmetic/algebra execution retains its
independent flags. Applying V022 is required before starting this code; schema rollback to
V021 after v2 rows exist is unsupported. Real model/secondary-generation and production
release approvals remain separate decisions.

Sprint 5.9 production integration acceptance does not imply production rollout approval or
Phase 5 completion. Sprint 5.10 composite policy, 5.11 arbitration and 5.12 release evaluation
remain separate delivery units.

## Tests authored and owner evidence ledger

All new/changed tests are NOT_RUN_BY_AGENT. No agent-executed compilation, builds, tests,
linters, formatters, Docker, validators, application startup, secret scans or inference occurred.
Standard-library scripts authored schemas, fixtures, source and manifest metadata only;
expected mathematics was specified manually. Source review is not a passing validation result.

| Gate | Actual coverage | Environment/expected result | Current evidence |
| --- | --- | --- | --- |
| A | CalculusSourceContractTest, CalculusRevisionControllerTest, ProblemSessionLifecyclePolicyTest, new auth test in ProblemParseControllerTest, admission assertions in CalculusProductionIntegrationTest; test_calculus_integration_contract.py | Java 21, PostgreSQL, bundled schemas and Linux verifier; valid typed sources accepted, malformed/stale/foreign/review sources rejected | Owner run 20261029-182828: full backend passed, 592 tests plus two paid-model skips; NOT_RUN_BY_AGENT |
| B | CalculusCandidatePlanningTest; existing SolutionCandidateValidatorTest, CanonicalCandidateMapperTest, CanonicalSolutionServiceTest, CanonicalSolutionIntegrationTest | Java 21/PostgreSQL; both role projections retain lineage, step identity and v1/v2 separation | Owner run 20261029-182828: full backend passed, 592 tests plus two paid-model skips; NOT_RUN_BY_AGENT |
| C | CalculusCandidatePlanningTest, existing VerificationCapabilitiesTest/VerificationPlannerTest/VerificationPlanningIntegrationTest | Explicit approved local flag + compatible authenticated readiness required; production/unknown algorithm rejected | Owner run 20261029-182828: full backend passed, 592 tests plus two paid-model skips; NOT_RUN_BY_AGENT |
| D | CalculusHttpExecutorTest, CalculusProductionIntegrationTest, existing CalculusExecutionContractTest/VerificationExecutionContractTest/CalculusWireCodecTest | VERIFIEDAI_MATH_WIRE=true, provisioned host Python; actual authenticated HTTP, bounded worker, invalid response rejection | Owner run 20261029-182828: full backend passed, 592 tests plus two paid-model skips; NOT_RUN_BY_AGENT |
| E | CalculusProductionIntegrationTest, CalculusMigrationIntegrationTest, FlywayMigrationTest, EvidenceMathJsonTest, existing VerificationExecutionIntegrationTest/VerificationLifecycleContributorTest | Docker PostgreSQL16, migrations through22; immutable roundtrip, correction race, retries, cross-user rejection, export/delete, rollback history | Owner run 20261029-182828: full backend passed, 592 tests plus two paid-model skips; NOT_RUN_BY_AGENT |
| F | Full Linux Python tests, existing calculus-v1 mathematical/adversarial/security fixtures; new manual source-to-worker fixtures | Docker/Linux required for CPU/address-space isolation; exact proofs and non-proof diagnostics remain distinct | Owner run 20261029-175452: 189 passed, 1 dependency deprecation warning, 9.87s; NOT_RUN_BY_AGENT |
| G | Full Maven clean verify, full Linux verifier suite and full iOS regression | Java21/Maven/Docker/provisioned Python/Xcode + available iOS17-or-newer simulator; no failures, only existing intentionally gated paid-model skips | OWNER_PASS: Python 189; backend 592 plus two paid-model skips; iOS18.4 104 unit and 10 UI tests passed |
| H | Existing lint/docs/contracts/secret/immutability commands and released schema diff | Provisioned tools; 298 documentation files, old version bytes unchanged | OWNER_PASS in 20261029-190120; final evidence refresh docs-check passed in 20261030-000148; NOT_RUN_BY_AGENT |

Ledger revision: baseline HEAD 9cba8c782e196aec7b3b28cf08c65e3c18c44134 plus the
uncommitted integration working tree described in the implementation map. The owner block
captures HEAD/status, tracked patch, untracked hashes and stage logs. Except for the
owner observations recorded below, acceptance evidence remains PENDING.
Hosted CI for this working tree is CI_PENDING and has not been dispatched. Earlier PR #34
results cannot be relabeled as this integration's passing evidence.

### Owner run 2026-10-29 17:54:52 Europe/Istanbul

Owner-supplied logs: `.generated/sprint-5-9-integration-owner/20261029-175452/`.
The full Linux verifier suite passed: 189 tests, one Starlette/httpx deprecation warning,
9.87 seconds. Backend production compilation completed (498 source files); test
compilation failed before test execution because wildcard Spring annotation imports made
`Role.PRIMARY` ambiguous with CanonicalSolution.Role. The repair replaces that wildcard
with the three annotations actually used: Bean, Import and Primary. Only the Java test
imports and this evidence documentation changed after that run, so the Python result is
retained. No validation was executed by the agent. The next owner run resumes at full
backend verification, then iOS and Gate H; the original complete block below remains
available for a fresh run. Backend test results, iOS and Gate H remain pending.

### Owner run 2026-10-29 18:08:29 Europe/Istanbul

Owner-supplied logs: `.generated/sprint-5-9-integration-owner/20261029-180829/`.
Java production and test compilation passed. Maven failed after 49.037 seconds:
556 reported tests, zero assertion failures, one error, 508 skipped under fail-fast
(47 passed). The typed restriction integration test reached real worker execution and
publication but failed while reading the stored VerificationRun. Existing v1 execution
and planning integration suites passed in this run; overall backend acceptance is pending.

Source review identified a nested-reader defect: MathExpression's deserializer used
JsonParser.readValueAsTree(), re-entering the ObjectMapper with FAIL_ON_TRAILING_TOKENS
while still inside a restriction object. Sibling fields were treated as trailing data.
It now uses DeserializationContext.readTree(parser), leaving document-level trailing-token
validation enabled on the enclosing mapper. Strict AST fields, duplicates, enums and
complexity limits remain enforced. EvidenceMathJsonTest adds nested restriction roundtrip,
sibling preservation, document trailing-token rejection and malformed-field cases. These
tests are authored, NOT_RUN_BY_AGENT. The owner will rerun the full backend suite, iOS and
Gate H. Python source/contracts/fixtures remain unchanged since its 189-test passing run.

### Owner run 2026-10-29 18:13:35 Europe/Istanbul

Owner-supplied logs: `.generated/sprint-5-9-integration-owner/20261029-181335/`.
Maven failed after 49.666 seconds: 559 reported tests, zero assertion failures, one error,
508 skipped under fail-fast (50 passed). The calculus restriction AST persistence/history/
privacy-export test now passed. The five real-worker mathematical cases and the correction-
during-HTTP stale-publication/cross-user test also passed. Admission testing then exposed
`Illegal problem session transition PARSED -> REVIEW_REQUIRED` when a selected user
correction introduced uncertainty. No iOS or Gate H commands were reached.

The repair adds a user-correction-specific lifecycle guard permitting exactly that extra
transition after existing ownership, base-revision, evidence and normalization checks.
The generic lifecycle transition table remains unchanged; background processing cannot
regress PARSED, and other terminal regressions remain forbidden. Correction selection and
status change stay in the same transaction. Unit tests cover this boundary; the integration
test now checks REVIEW_REQUIRED persistence, rejection of current canonical/source access
even when an older canonical exists, absence of verification evidence, and recovery through
a later valid correction back to PARSED. These additions await owner execution. The
three standalone EvidenceMathJsonTest cases were skipped by fail-fast, so their pass is
not claimed. Python's previous 189-test result remains applicable.

### Owner run 2026-10-29 18:18:14 Europe/Istanbul

Owner-supplied logs: `.generated/sprint-5-9-integration-owner/20261029-181814/`.
Maven failed after 54.579 seconds: 561 reported tests, one assertion failure, zero errors,
452 skipped under fail-fast (108 passed). All six CalculusProductionIntegrationTest cases
passed in 5.113 seconds, including review-required correction/recovery, real-worker math,
typed restriction persistence/export, concurrency, stale/foreign access, malformed response,
account deletion and feature rollback history. CalculusMigrationIntegrationTest passed in
0.367 seconds, exercising the real V021-to-V022 upgrade, retained legacy parse bytes and
immutable triggers, and a no-op repeat migration.

The failing FlywayMigrationTest still required the old separate v1 canonical schema and
problem-type constraint names. V022 deliberately replaces those three constraints with
ck_canonical_problem_versioned_kind, which keeps explicit v1 and v2 branches. The test now
requires that combined constraint, checks removal of the old names and retains foreign-key,
JSON-object and index assertions, with additional revision and session-revision uniqueness
checks. This repair changes test expectations and documentation only; no migration or
production source change was needed. Full backend, iOS and Gate H remain pending; the
earlier passing suites do not establish full acceptance. NOT_RUN_BY_AGENT remains true.

### Owner run 2026-10-29 18:21:27 Europe/Istanbul

Owner-supplied logs: `.generated/sprint-5-9-integration-owner/20261029-182127/`.
Maven failed after a reported 01:07 minutes: 561 reported tests, one assertion failure,
zero errors, 288 skipped under fail-fast (272 passed). All six FlywayMigrationTest cases,
all four lifecycle policy tests and all six calculus production integration cases passed.
The next failure was VerificationSourceReaderTest.unknownTaskHasExplicitOutcome: the v1
reader accepted DIFFERENTIATE after the shared PlanningSource enum was extended for v2.

The repair explicitly restricts the v1 reader to its four released problem types and four
released task types before math decoding. Unknown and calculus-only values retain the
existing UNSUPPORTED_PROBLEM_TYPE/UNSUPPORTED_TASK_TYPE outcomes. The v2 source branch
continues through CalculusSourceDocument. Regression coverage now checks all three calculus
types/tasks and unknown values through both planning and execution reads, with matching
relational/JSON metadata so lineage rejection cannot mask a version-boundary failure.
No published schema was changed. This repair and the remaining full backend/iOS/Gate H
checks await the owner; no validation was executed by the agent.

### Owner run 2026-10-29 18:24:52 Europe/Istanbul

Owner-supplied logs: `.generated/sprint-5-9-integration-owner/20261029-182452/`.
Maven failed after a reported 01:30 minutes: 585 reported tests, one assertion failure,
zero errors, 67 skipped (517 passed). Execution progressed beyond the repaired v1 reader;
all three EvidenceMathJsonTest regressions and ModularityTest passed. The failure was
CanonicalSolutionServiceTest.unsupportedHistoricalCandidateGetsTypedFailureAndNoSuccess:
its old fixture used solution-candidate-v2, which is now supported, while retaining v1
content. INVALID_CANDIDATE is the correct outcome for that mismatch.

The unsupported-schema test now uses unknown solution-candidate-v99 and retains its
UNSUPPORTED_SCHEMA/no-projection assertion. A separate test retains the v2-labeled v1
fixture and asserts INVALID_CANDIDATE, rejection under Version.CALCULUS and no projection
insert. This is a test-only repair; production version dispatch and validation stay intact.
The owner must rerun the full backend suite and then iOS/Gate H. No agent validation ran.

### Owner run 2026-10-29 18:28:28 Europe/Istanbul

Owner-supplied logs: `.generated/sprint-5-9-integration-owner/20261029-182828/`.
Full Maven clean verify completed with BUILD SUCCESS in a reported 01:27 minutes:
594 reported tests, zero failures, zero errors, 592 passed, two intentional paid-model
skips because QWEN_OWNER_RUN was absent. All backend suites, including the new schema
rejection tests, architecture, real HTTP, PostgreSQL and calculus lifecycle coverage passed.
The retained Linux verifier result is 189 passed, one dependency warning.

The block then stopped before iOS test execution: the hardcoded iOS-18-5 runtime was
unavailable (exit 145). No simulator was created and Gate H was not reached. The owner
command now discovers an available iPhone simulator's runtime/device type at iOS 17 or
newer (the project's deployment minimum), then creates an isolated temporary simulator
with that pair. It records the selected runtime and does not install runtimes or treat
missing prerequisites as a pass. The next owner block retains backend/Python evidence and
runs iOS plus Gate H. This correction changes documentation/commands only. Hosted CI
retains its independently configured runtime; no hosted execution or approval is claimed.

### Owner run 2026-10-29 18:32:35 Europe/Istanbul

Owner-supplied logs and xcresult: `.generated/sprint-5-9-integration-owner/20261029-183235/`.
Runtime discovery selected iOS18.4 / iPhone16e. All 104 unit tests passed; the ten UI tests
had nine passes and one failure. PrimarySolveUITests.testHistoryQueuedRunningUnverifiedStepsAndReopen
failed before starting a solve because history.solve did not appear after the history-row
tap. xcodebuild exited 65; Gate H was not reached. Backend/Python evidence is retained.

Read-only extraction of the owner's saved xcresult showed the app still on the dashboard,
with the history row at y=712.7..782.4 and the synthesized tap at (195,747.5), inside the
row. No new test or app was run by the agent. Source inspection found the decorative
history-panel border overlay still participating in hit testing. The targeted repair adds
allowsHitTesting(false) to that border only; the existing UI test keeps its real tap and
required navigation assertion. This is a candidate fix pending the full owner iOS rerun,
not a confirmed diagnosis or passing test. No calculus UI feature was introduced.

### Owner run 2026-10-29 19:01:20 Europe/Istanbul

Owner-supplied logs and xcresult: `.generated/sprint-5-9-integration-owner/20261029-190120/`.
On iOS18.4 / iPhone16e, all 104 unit tests and all ten UI tests passed, including the
previously failing history queued/running/reopen scenario. xcodebuild reported TEST
SUCCEEDED, with 209.342 seconds elapsed for testing. The border hit-testing repair is
covered by this successful owner run. Gate H passed: lint, docs-check (298 Markdown files),
contracts, secret scan, five prompt-immutability unit tests, base-ref immutability check,
git diff whitespace check and unchanged released schemas. The block exited zero.

Together with retained Linux Python evidence (189 passed) and full backend evidence
(592 passed, two intentionally gated paid-model skips, BUILD SUCCESS), all requested
local automated A–H gates have owner passing evidence. Only this final evidence report,
implementation-map status and documentation manifest were refreshed afterward; their
docs-check passed in owner run 20261030-000148 (298 files, exit zero). No test/build/validator was executed by the agent. Hosted CI on
the reviewed commit, release approval and production activation remain outstanding.

## Owner validation

Prerequisites: macOS, Java21 (JAVA_HOME_21 may override the Homebrew default), Maven,
ripgrep, running Docker Desktop, existing services/math-verifier/.venv with dev dependencies,
Xcode and an available iPhone simulator with an installed iOS17-or-newer runtime.
No credentials or paid model access are needed.
The Docker owner-tests target installs only the existing declared test dependencies.
The full Python suite runs once on Linux; full backend runs once with actual wire tests;
repository lint's existing compile step is retained. The full iOS regression covers the
public API's versioned alternatives and the owner-observed history-row hit-testing repair.
The temporary simulator belongs to this validation run and is removed by the exit trap.
No failure is suppressed. If a prerequisite is missing, that stage remains pending.

```bash
bash <<'OWNER_VALIDATION'
set -euo pipefail
verification_root="$(git rev-parse --show-toplevel)"
cd "$verification_root"
verification_output="$verification_root/.generated/sprint-5-9-integration-owner/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$verification_output"
exec > >(tee "$verification_output/acceptance.log") 2>&1
verification_stage='Prerequisites and working-tree evidence'
verification_java_started=false
verification_simulator=''
verification_simulator_booted=false
verification_finish() {
verification_status=$?
trap - EXIT
if [ "$verification_java_started" = true ] && [ -d "$verification_root/services/api/target/surefire-reports" ]; then
if ! cp -R "$verification_root/services/api/target/surefire-reports" "$verification_output/java-reports"; then
verification_status=1
fi
fi
if [ -n "$verification_simulator" ]; then
if [ "$verification_simulator_booted" = true ]; then
if ! xcrun simctl shutdown "$verification_simulator"; then
verification_status=1
fi
fi
if ! xcrun simctl delete "$verification_simulator"; then
verification_status=1
fi
fi
printf '\nStage: %s\nExit status: %s\nLogs: %s\n' "$verification_stage" "$verification_status" "$verification_output"
exit "$verification_status"
}
trap verification_finish EXIT
export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:/usr/local/bin:$PATH:/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path"
export VERIFIEDAI_VERIFIER_PYTHON="$verification_root/services/math-verifier/.venv/bin/python"
export VERIFIER_PYTHON="$VERIFIEDAI_VERIFIER_PYTHON"
export VERIFIEDAI_MATH_WIRE=true
export APP_ENV=local
export VERIFICATION_EXECUTION_ENABLED=false
export VERIFICATION_CALCULUS_ENABLED=false
export VERIFICATION_CALCULUS_OWNER_ACCEPTED=false
export CALCULUS_FIXTURE_ENABLED=false
export MATH_VERIFIER_CALCULUS_ENABLED=false
unset QWEN_OWNER_RUN
command -v rg
command -v docker
command -v mvn
command -v python3
command -v xcrun
command -v xcodebuild
test -x "$VERIFIER_PYTHON"
java -version
mvn -version
"$VERIFIER_PYTHON" --version
docker info
xcodebuild -version
git rev-parse HEAD
git status --short | tee "$verification_output/working-tree.txt"
git diff HEAD --binary > "$verification_output/tracked-changes.patch"
git ls-files --others --exclude-standard | while IFS= read -r verification_path; do shasum -a 256 "$verification_path"; done > "$verification_output/untracked-files.sha256"
verification_stage='A, D, F, G: full Linux verifier, schema, math, security and worker/resource suite'
printf '\n%s\n' "$verification_stage"
docker build --target owner-tests -t verified-ai/math-verifier-owner:5.9-integration services/math-verifier
docker run --rm --init --memory=768m --cpus=2 --pids-limit=64 --mount "type=bind,src=$verification_root,dst=/workspace,readonly" --workdir /workspace/services/math-verifier verified-ai/math-verifier-owner:5.9-integration python -m pytest -x -ra -vv -p no:cacheprovider 2>&1 | tee "$verification_output/python.log"
verification_stage='A, B, C, D, E, G: full backend, real calculus HTTP, PostgreSQL, concurrency, lifecycle, v1 and architecture'
printf '\n%s\n' "$verification_stage"
make test-minio-image
verification_java_started=true
(cd services/api && mvn -Dsurefire.skipAfterFailureCount=1 -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 clean verify) 2>&1 | tee "$verification_output/backend.log"
verification_stage='G: full iOS regression with an isolated available simulator'
printf '\n%s\n' "$verification_stage"
xcrun simctl list --json > "$verification_output/simulators.json"
python3 - "$verification_output/simulators.json" > "$verification_output/simulator-selection.txt" <<'SELECT_SIMULATOR'
import json
import sys
from pathlib import Path
inventory = json.loads(Path(sys.argv[1]).read_text())
choices = []
for runtime in inventory["runtimes"]:
    if not runtime.get("isAvailable") or not runtime["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-"):
        continue
    version = tuple(int(part) for part in runtime["version"].split("."))
    if version < (17,):
        continue
    for device in inventory["devices"].get(runtime["identifier"], []):
        kind = device.get("deviceTypeIdentifier", "")
        if device.get("isAvailable") and kind.startswith("com.apple.CoreSimulator.SimDeviceType.iPhone-"):
            choices.append((version, kind, runtime["identifier"]))
if not choices:
    sys.exit("No available iPhone simulator with iOS 17 or newer. Install/select a compatible runtime in Xcode; iOS validation remains pending.")
_, kind, runtime = max(choices)
print(runtime, kind)
SELECT_SIMULATOR
read -r verification_runtime verification_device_type < "$verification_output/simulator-selection.txt"
printf '\nSelected runtime: %s\nDevice type: %s\n' "$verification_runtime" "$verification_device_type"
verification_simulator="$(xcrun simctl create "VerifiedAI-Sprint59-$(date +%H%M%S)" "$verification_device_type" "$verification_runtime")"
xcrun simctl boot "$verification_simulator"
verification_simulator_booted=true
xcrun simctl bootstatus "$verification_simulator" -b
NSUnbufferedIO=YES xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination "platform=iOS Simulator,id=$verification_simulator" -destination-timeout 120 -parallel-testing-enabled NO -derivedDataPath "$verification_output/DerivedData" -resultBundlePath "$verification_output/VerifiedAI.xcresult" 2>&1 | tee "$verification_output/ios.log"
verification_stage='H: lint, documentation, contracts, security and immutable released versions'
printf '\n%s\n' "$verification_stage"
make JAVA21_HOME="$JAVA_HOME" VERIFIER_PYTHON="$VERIFIER_PYTHON" lint docs-check contracts-check secret-scan
python3 -m unittest discover -s scripts/quality -p test_prompt_immutability.py
python3 scripts/quality/check_prompt_immutability.py --base-ref 9cba8c782e196aec7b3b28cf08c65e3c18c44134
git diff --check
git diff --exit-code 9cba8c782e196aec7b3b28cf08c65e3c18c44134 -- packages/schemas/problem-parse.schema.json packages/schemas/canonical-problem.schema.json packages/schemas/solution-candidate.schema.json packages/schemas/verifier-input.schema.json packages/schemas/verification-execution.schema.json packages/schemas/verification-execution-result.schema.json packages/schemas/calculus-execution.schema.json packages/schemas/calculus-execution-result.schema.json
verification_stage='All local commands completed; hosted CI and release approval remain separate'
printf '\n%s\nCalculus rollout remains disabled. No overall VERIFIED verdict is assigned.\n' "$verification_stage"
OWNER_VALIDATION
```

## Open items and final status

- LOCAL_OWNER_GATES_PASSED: Python 189, backend 592 plus two paid-model skips, iOS 104 unit and 10 UI tests, and Gate H passed. Final evidence-only documentation refresh passed owner docs-check in 20261030-000148.
- CI_PENDING: applicable hosted checks must pass on the reviewed commit.
- RELEASE_APPROVAL_PENDING / ROLLOUT_DISABLED: production guards stay active.
- Separate approval: any real-model prompt/schema route and secondary-generation activation.
- Unsupported mathematics remains the existing bounded executor's documented boundary.
- Deferred units: Sprint5.10 composite policy, Sprint5.11 overall arbitration, Sprint5.12 release evaluation.

Final status: LOCAL_OWNER_GATES_PASSED / CI_PENDING /
ROLLOUT_DISABLED. Local automated success does not establish hosted acceptance or production
release approval. No commit/push/deployment was performed.
