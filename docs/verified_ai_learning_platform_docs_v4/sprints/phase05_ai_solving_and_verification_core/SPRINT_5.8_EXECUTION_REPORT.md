# Sprint 5.8 — Execution report

## A. Baseline

Branch: `codex/sprint-5-8-math-verification`.
HEAD: `eec8d6a2ec4ec1a1122adf0dd0111af831ba978d` (merge of PR #31).
Sprint 5.7 head `ea4c996` is an ancestor. Starting working tree and difference from origin/main were clean.
No owner dirty files existed. No tests, compilation, runtime probes, commits, pushes or PR creation were performed by the agent.
Targeted source/contract review and source-only edits were performed. Owner validation remains mandatory.

## B. Implementation summary

Authored: exact rational arithmetic; one-variable bounded rational-polynomial equivalence;
original equation rational-root substitution; independent domain checks; degree <=2 rational-root singleton completeness.
Original AST denominator/negative-power obligations survive cancellation. Unknown domains and unsupported claims fail closed.
The POWER operand bug was absent at baseline. Parameter/unknown variable roles are blocked at execution preparation; planning contracts remain unchanged. Conversion retains the converted exponent and now constructs unevaluated syntax;
integer-only exponents, malformed INTEGER lexemes and explicit zero singularities are guarded.

Java uses owner-authorized planning and canonical reads, a separate typed HTTP port, short source preparation/publication transactions,
strict response provenance/proof validation and immutable V021 run aggregates. There is no overall verification policy or solution status assignment.
Configured installed-but-disabled executors use `canonical-capabilities-v2-disabled`; approved method sets get distinct v2 identities.
Sprint 5.7 plan references retain their original v1 meaning. No activation was approved or enabled.

## C. File inventory

The following inventory records implementation ownership and changed files. All local owner acceptance gates A–H have passing evidence. Hosted CI on a final reviewed commit remains pending.

| File | Change | Owner/purpose |
| --- | --- | --- |
| `.github/workflows/backend-ci.yml` | Modified | Deployment/quality configuration |
| `.github/workflows/math-verifier-ci.yml` | Modified | Deployment/quality configuration |
| `docker-compose.yml` | Modified | Deployment/quality configuration |
| `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/ai/27_VERIFICATION_ENGINE.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/security/35_SECURITY_THREAT_MODEL.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md` | Modified | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.8_EXECUTION_REPORT.md` | Created | Canonical contract/evidence documentation |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.8_IMPLEMENTATION_MAP.md` | Created | Canonical contract/evidence documentation |
| `packages/contracts/openapi/internal-math-verifier.yaml` | Modified | Internal contracts/shared mathematical fixtures |
| `packages/schemas/README.md` | Modified | Internal contracts/shared mathematical fixtures |
| `packages/schemas/verification-execution-result.schema.json` | Created | Internal contracts/shared mathematical fixtures |
| `packages/schemas/verification-execution.schema.json` | Created | Internal contracts/shared mathematical fixtures |
| `packages/test-fixtures/verification/execution-v1/cases.json` | Created | Internal contracts/shared mathematical fixtures |
| `scripts/quality/check_contracts.py` | Modified | Deployment/quality configuration |
| `services/api/src/main/java/com/verifiedai/verification/application/MathematicalExecutor.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/application/VerificationExecution.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/application/VerificationExecutionService.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/application/VerificationSourceReader.java` | Modified | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/application/package-info.java` | Modified | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/MathematicalExecution.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationEvidence.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationPlan.java` | Modified | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationRun.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/package-info.java` | Modified | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/domain/policy/VerificationCapabilities.java` | Modified | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/domain/repository/VerificationRunRepository.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/http/ExecutionWireCodec.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/http/HttpMathematicalExecutor.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/persistence/JdbcVerificationRunRepository.java` | Created | Verification model/application/adapter |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/privacy/VerificationLifecycleContributor.java` | Created | Verification model/application/adapter |
| `services/api/src/main/resources/application.yml` | Modified | Deployment/quality configuration |
| `services/api/src/main/resources/db/migration/platform/V021__create_immutable_verification_runs.sql` | Created | Immutable evidence migration |
| `services/api/src/test/java/com/verifiedai/architecture/VerificationPlanningArchitectureTest.java` | Modified | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/contract/VerificationExecutionContractTest.java` | Created | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/integration/verification/VerificationExecutionIntegrationTest.java` | Created | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/verification/application/VerificationExecutionServiceTest.java` | Created | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/verification/application/VerificationSourceReaderTest.java` | Modified | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/verification/domain/MathematicalExecutionTest.java` | Created | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/verification/domain/VerificationCapabilitiesTest.java` | Modified | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/verification/infrastructure/ExecutionWireCodecTest.java` | Created | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/verification/infrastructure/HttpMathematicalExecutorTest.java` | Created | Backend test/architecture |
| `services/api/src/test/java/com/verifiedai/verification/infrastructure/VerificationLifecycleContributorTest.java` | Created | JSON export and provenance regression |
| `services/math-verifier/Dockerfile` | Modified | Deployment/quality configuration |
| `services/math-verifier/README.md` | Modified | Deployment/quality configuration |
| `services/math-verifier/app/api/routes/metrics.py` | Modified | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/api/routes/verify.py` | Modified | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/api/schemas/execution.py` | Created | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/api/schemas/verifier_input.py` | Modified | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/config.py` | Modified | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/main.py` | Modified | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/observability/metrics.py` | Modified | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/parsing/verifier_input_validator.py` | Modified | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/policies/process_executor.py` | Created | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/security/request_limits.py` | Created | Typed mathematics/resource/security runtime |
| `services/math-verifier/app/verifiers/canonical_execution.py` | Created | Typed mathematics/resource/security runtime |
| `services/math-verifier/pyproject.toml` | Modified | Deployment/quality configuration |
| `services/math-verifier/tests/contract/test_execution_contract.py` | Created | Verifier correctness/security/contract tests |
| `services/math-verifier/tests/security/test_execution_security.py` | Created | Verifier correctness/security/contract tests |
| `services/math-verifier/tests/unit/test_canonical_execution.py` | Created | Verifier correctness/security/contract tests |
| `services/math-verifier/tests/unit/test_verifier_input_validator.py` | Modified | Verifier correctness/security/contract tests |

## D. Mathematical evidence matrix

| Method | Availability | Proof condition | Non-proof/failure boundary | Authored coverage |
| --- | --- | --- | --- | --- |
| Arithmetic | Installed, release gated | Exact rational equality/inequality | Undefined values, noninteger/large powers and bit limits | Shared golden corpus; Python units; wire tests |
| Equivalence | Installed, release gated | Bounded polynomial zero difference under nonempty supported source domain | Original domain obligations, unsupported functions/constraints; restricted nonidentity inconclusive | Identity/nonidentity, cancellation holes, unrelated/contradictory restrictions |
| Substitution | Installed, release gated | Exact candidate assignment satisfies ORIGINAL equality and all domain checks | Ambiguous assignment, unknown domains, undefined roots, unsupported exact function values | Correct/wrong/extraneous/forbidden roots |
| Domain | Installed, release gated | Candidate/original are defined and source constraints hold | Unknown/singular transcendental values cannot be guessed | INTEGER/REAL, sqrt/log, conservative tangent, source restrictions |
| Completeness | Installed, release gated | Independently derived, domain-filtered degree <=2 rational-root set equals candidate singleton | Multiple/empty/infinite set syntax absent; irrational roots/higher degrees unsupported | Unique/repeated/missing roots; degenerate cases; rational denominator filtering |

PROVEN and DISPROVEN require typed method-specific descriptors. Exact rational witnesses must agree with their outcomes.
INCONCLUSIVE, UNSUPPORTED, BLOCKED and EXECUTION_FAILED contain no proof. Root soundness never implies completeness.
The corpus uses human-reviewable arithmetic assertions, without model-produced ground truth or numerical sampling.

## E. Security and operational impact

- Per operation: 3 s elapsed; 2 s POSIX CPU; 512 MiB Linux address space; two concurrent spawned workers, no queue.
- Requests <=65536 bytes before parsing; responses <=16384 bytes; 120 AST nodes/statement, depth 32, function depth 8, 1200 total nodes.
- Numeric digits <=64, intermediates <=1024 bits; polynomial degree <=12 and <=128 terms; completeness degree <=2.
- Non-root single-worker Uvicorn deployment has bounded connection/backlog counts; Compose adds CPU/memory/PID restrictions, read-only root and dropped capabilities.
- Linux resource validation is mandatory; macOS cannot establish RLIMIT_AS acceptance. Container OOM/worker crash remains non-proof failure.
- Production startup rejects development secrets; enabled production execution requires Linux. Legacy string equivalence is local/test-only.
- Java has four transport admission slots, one-second connection timeout, four-second request timeout and 4.5-second complete-response deadline. No retries.
- Execution IDs are opaque; verifier payloads exclude student identity, images, explanations and provider data. Logs/metrics use finite labels and bounded correlation IDs.
- V021 uses exact composite ownership/source/version FKs, immutable update/delete rules and one owner/session/request-key publication. Same-key retry reuses history; a fresh attempt uses a new key.
- Export includes all owner run aggregates. Canonical solution/job/session/account deletion cascades. No training eligibility or AI costs are added.
- New test-only dependency: jsonschema (MIT), independently checks published JSON contracts. No new production dependency.

## F. Test matrix

| Gate | Owner command group below | Expected behavior | Actual result |
| --- | --- | --- | --- |
| A | Focused Python mathematical units | Exact correct/incorrect/undefined/unsupported outcomes | OWNER-TESTED PASS: 58 tests, 0.46 s |
| B | Linux Docker security and contracts | Actual CPU/memory/deadline termination, auth, body limits, shared schemas | OWNER-TESTED PASS: 29 tests, 4.35 s; one deprecation warning |
| C | Java domain/application/gateway units | Strict evidence semantics, feature gating, transaction order and failures | OWNER-TESTED PASS: 29 distinct tests across accepted class runs (22 unchanged + repaired service class 7/7) |
| D | Java/Python process contract | Java wire requests and actual isolated Python results agree across shared corpus | OWNER-TESTED PASS after lint cleanup: 1 cross-runtime contract test, 10.43 s |
| E | PostgreSQL integration and export regression | Immutable publication, duplicate/concurrent requests, stale source, foreign ownership, export/delete and rollback | OWNER-TESTED PASS: 2 export units and 10 execution integration tests; 6 planning integration tests passed again in F/G |
| F | Architecture and prior sprint regression | Preserve dependency, source ownership and canonical solving contracts | OWNER-TESTED PASS: 141 tests, no failures/errors/skips |
| G | Complete backend and verifier suites | No regressions; existing real-Qwen conditional skips explicitly identified | OWNER-TESTED PASS: unchanged backend 553 run, 551 passed, 2 gated Qwen skips; Linux verifier rerun after cleanup 92 passed, 1 warning |
| H | Quality/docs/contracts/secrets/immutability | Source and documentation remain coherent | OWNER-TESTED PASS (local): lint, docs (294 Markdown files), contracts, secret scan, 5 immutability unit tests and unchanged existing prompt/schema versions |
| Hosted CI | Requires later authorized publication | Checks on exact final reviewed commit | NOT RUN |

All local owner acceptance gates A–H have passing evidence, including the full verifier and actual cross-runtime reruns after lint cleanup. This is working-tree owner acceptance, not final-commit CI verification or production approval. Hosted CI is separately NOT RUN. New wire test is explicitly enabled in the commands and backend CI;
backend-only invocations without VERIFIEDAI_MATH_WIRE=true report its conditional skip. Linux-only resource tests must run in Docker/CI.

### Owner checkpoint — 2026-10-29

Owner terminal output (Gate C ended at 10:37:41 +03:00) establishes Gate A: 58/58 passing on macOS/Python 3.11.10,
and Gate B: 29/29 passing on Linux/Python 3.11.18, with no reported failures or skips.
Gate B includes actual process CPU/memory limits, ASGI timeout/cleanup, authentication and execution contracts.
The single Starlette TestClient/httpx deprecation warning is non-fatal; no dependency change is included in this repair.

Gate C compiled 489 production sources, then failed while compiling test sources before any selected Java tests ran.
Classification: test defect. A wildcard Spring annotation import brought `org.springframework.context.annotation.Role`
into scope alongside the canonical solution's `Role` enum. Replaced that wildcard with explicit Bean, Import and Primary imports.
Production behavior, ownership/transaction rules and mathematical assertions are unchanged. Repair is authored, not agent-tested.
Resume at Gate C; D–H and hosted CI have not run. A/B evidence remains applicable because this repair changes no Python code,
mathematical fixtures, dependencies or deployment configuration. It is working-tree evidence, not exact-commit CI acceptance.

### Owner checkpoint — 2026-10-29, 10:41:55 +03:00

The owner rerun confirms that the Role import collision is resolved: test compilation succeeded and Gate C executed 29 tests,
with zero assertion failures, three errors and zero skips. VerificationSourceReaderTest (7), ExecutionWireCodecTest (3),
HttpMathematicalExecutorTest (5), MathematicalExecutionTest (4) and VerificationCapabilitiesTest (3) all passed: 22 tests across unchanged classes.
VerificationExecutionServiceTest ran seven tests: four passed; three errored during Mockito restubbing, before exercising their scenarios.

Classification: test defect. `when(executor.execute(any()))` invoked the existing default Answer with the matcher's null request,
which correctly failed in Result.failed. Replaced the three overrides with doAnswer/doReturn; nested source failure injection uses
doReturn/doThrow so an earlier throwing stub cannot execute during restubbing. Production null rejection, stale-source fencing,
account authorization, failure outcomes and all test assertions remain unchanged. No agent validation was executed.
Rerun only VerificationExecutionServiceTest for the seven affected cases, retaining the 22 passing class results above; then continue D–H.
Gate C remains failed/pending until that owner rerun passes. A/B remain owner-passing; no broader acceptance or CI claim is added.

### Owner checkpoint — 2026-10-29, 10:45–10:46 +03:00

VerificationExecutionServiceTest rerun passed 7/7 with zero failures/errors/skips at 10:45:40, completing Gate C across
the previously accepted 22 unchanged tests plus these seven tests. Gate D then passed VerificationExecutionContractTest (1/1,
10.09 s, no skips) at 10:45:55, exercising the actual Java-serialized/Python-worker contract over the shared corpus.

Gate E ran 16 tests: VerificationPlanningIntegrationTest passed 6/6; VerificationExecutionIntegrationTest passed eight of ten
with two setup/scenario errors and no assertion failures or skips. Classification: test fixture defects.
The second accepted-job fixture reused attempt number 1 for the same canonical source, correctly rejected by uq_solve_source_attempt.
The account-deletion fixture attempted a direct SQL user delete, correctly rejected by the existing problem-session owner FK.

Repair: allocate the next bounded source attempt and its predecessor under the existing owner/source lock; preserve the original
idempotency assertion and additionally verify that the second current solution is distinct and the original key binding remains intact.
Use the actual AccountPrivacyApplicationService request/confirm deletion flow, verifying evidence/session removal and the retained DELETED
user tombstone. Only synthetic object storage is mocked; its delete calls are verified. PostgreSQL, lifecycle contributors, account service
and immutable-evidence constraints remain real. Full account export creation/download is also asserted to contain the run, beyond contributor-only export.
No production code, migration, FK, authorization rule or mathematical assertion is weakened or changed. No agent validation was run.
Rerun all ten execution integration tests because their shared fixture/configuration changed, retaining the six passing planning tests; then F–H.

### Owner checkpoint — 2026-10-29, 10:53:49 +03:00

The owner reran VerificationExecutionIntegrationTest: 10 tests, nine passing, one error, no assertion failures or skips.
Both earlier fixture defects are resolved, including the actual account deletion flow. The remaining full account export fails
because the verification contributor returns domain records containing Instant values to the shared Jackson 2 account serializer.
Classification: product defect at the verification privacy export boundary, not an environment or mathematical failure.

Repair: the contributor produces JSON-native snapshots with an explicitly scoped Instant-to-ISO-string serializer for run and
evidence timestamps. All record fields, exact witnesses and provenance remain present; persisted immutable records are not changed.
The generic account serializer and dependencies are unchanged. New VerificationLifecycleContributorTest authors two cases for a
plain account serializer, nanosecond timestamp precision, complete provenance, rational witnesses, failure-without-proof and empty history.
The real account export integration assertion is retained and strengthened to cover every evidence timestamp and failed outcome.
No agent tests, builds or runtime probes were executed. Retain A–D and six passing planning tests; rerun the new export unit class
and all ten execution integration cases, then F–H. Full acceptance, hosted CI and rollout approval remain pending.

### Owner checkpoint — 2026-10-29, 11:00–11:02 +03:00

VerificationLifecycleContributorTest passed 2/2 at 11:00:28; VerificationExecutionIntegrationTest passed 10/10 at 11:00:43.
The full account export repair is owner-tested. Gate F passed 141/141, with no failures/errors/skips, at 11:01:09.
Gate G backend passed at 11:02:13: 553 run, 551 passing, zero failures/errors and two explicitly gated QwenRealModelEvaluationTest skips.
The actual cross-runtime contract ran and passed (1/1, 10.06 s). Linux/Python 3.11.18 verifier passed 92/92 in 3.42 s.
The Starlette/httpx deprecation warning persists. Netty listener/event-executor shutdown errors appear in the backend log, but no test
failure/error was reported; they are recorded without changing unrelated infrastructure or claiming a diagnosis.

Gate H stopped in Ruff with 11 findings; later lint steps and docs/contracts/secrets/immutability commands did not run.
Classification: source quality defects, not an environment failure or failed mathematical assertion. Six Python files are repaired:
five import blocks, a named frozen default ProcessBudget, a combined tangent guard, explicitly unused polynomial numerators and an
equivalent coefficient generator. The isolated worker deliberately retains its BaseException sanitizer, including process-exit exceptions,
with one documented BLE001 line exception; narrowing it could expose raw input through an unhandled child traceback. No lint rule is
disabled globally. No mathematical outcome, resource budget, test assertion, dependency, Java source or persistence contract is changed.
No new tests are needed for these lint-only edits; existing full verifier and cross-runtime suites cover the touched execution path.
No agent validation was executed. Resume with owner lint, full Linux verifier and the actual cross-runtime test, then the remaining H checks.
Preserve passing Java E/F/G evidence. Hosted CI, full acceptance and rollout approval remain pending.

### Owner checkpoint — 2026-10-29, 11:23:55 +03:00

Owner lint passed, including Ruff and backend compile. The full Linux verifier rerun passed 92/92 in 3.42 s with the same single
Starlette/httpx deprecation warning. VerificationExecutionContractTest passed 1/1 in 10.43 s, with no failures/errors/skips.
Documentation passed for 294 Markdown files and contracts passed. The secret scan exited at its prerequisite check because rg was
absent from the owner shell PATH; no secret scan result exists. The subsequent immutability unit and baseline checks did not run.
Classification: environment prerequisite failure. Read-only executable lookup found the bundled rg under
`/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path/`; the owner resume block adds that directory to PATH for its session.
The scanner remains fail-closed. No implementation, test or scanner change is required, and no agent validation was executed.
Only checkpoint documentation/manifest are refreshed. Resume with docs-check for that refresh, secret-scan and both immutability checks;
retain all passing code, lint and contract evidence. Hosted CI and rollout approval remain pending.

### Final local owner checkpoint — 2026-10-29

The owner resolved rg through the bundled tool directory. Documentation passed for 294 Markdown files; secret scan passed;
all five prompt-immutability unit tests passed (0.008 s); the trusted-baseline check confirmed unchanged existing prompt/schema versions.
All required local owner checks now have passing evidence. No code, test, dependency, deployment or scanner change followed those results.
Only this acceptance record, its summary matrices/map and manifest are refreshed; a final docs-check covers that evidence-only update.
No agent tests, builds or runtime probes were executed. Branch and HEAD remain at the Sprint 5.7 merge baseline with Sprint 5.8 work uncommitted.
No commit, push or PR is authorized. Hosted CI must run on the later owner-authorized final reviewed commit before CI verification is claimed.

## G. Owner commands

OWNER-EXECUTED VALIDATION REQUIRED

Remaining local check after this evidence-only refresh: `make docs-check` from the repository root. No code-suite rerun is required.
The block below is the complete reproducibility reference for A–H, not a request to repeat already passing checks.
Hosted CI awaits explicitly authorized commit/publication; no publication command is included.

Prerequisites: Java 21, Maven, Python 3.11+, Docker Desktop, ripgrep (rg) on PATH and dependency-download access.
The block stops at the first failing command and preserves complete output under `.generated/sprint-5-8-owner/`.
Return that complete output before advancing past a failing gate.

```bash
bash <<'OWNER_VALIDATION'
set -euo pipefail
cd /Users/burakcoskun/Desktop/ai_verification_tutor
mkdir -p .generated/sprint-5-8-owner
verification_stamp="$(date +%Y%m%d-%H%M%S)"
exec > >(tee ".generated/sprint-5-8-owner/acceptance-${verification_stamp}.log") 2>&1
trap 'verification_status=$?; if [ "$verification_status" -ne 0 ]; then printf "\nSTOPPED: return the complete output for the first failing gate.\n"; fi' EXIT

# Prerequisites: Java 21, Maven, Python 3.11+, Docker Desktop, ripgrep, network for dependencies.
export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:/usr/local/bin:$PATH:/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path"
command -v rg
export VERIFIEDAI_VERIFIER_PYTHON="$PWD/services/math-verifier/.venv/bin/python"
export VERIFIER_PYTHON="$VERIFIEDAI_VERIFIER_PYTHON"
unset QWEN_OWNER_RUN
java -version
if [ ! -x "$VERIFIEDAI_VERIFIER_PYTHON" ]; then python3.11 -m venv services/math-verifier/.venv; fi
"$VERIFIEDAI_VERIFIER_PYTHON" -m pip install -e 'services/math-verifier[dev]'

# Gate A — exact mathematics, typed conversion, and deterministic golden assertions.
(cd services/math-verifier && "$VERIFIEDAI_VERIFIER_PYTHON" -m pytest tests/unit/test_canonical_execution.py tests/unit/test_verifier_input_validator.py)

# Gate B — Docker REQUIRED: real Linux CPU/memory limits, ASGI deadlines, auth and schemas.
docker info >/dev/null
docker build --target owner-tests -t verified-ai/math-verifier-owner:5.8 services/math-verifier
docker run --rm --init --memory=768m --cpus=2 --pids-limit=64 \
  --mount "type=bind,src=$PWD,dst=/workspace,readonly" \
  --workdir /workspace/services/math-verifier \
  verified-ai/math-verifier-owner:5.8 \
  python -m pytest -p no:cacheprovider tests/security tests/contract

# Gate C — Java method semantics, capability gating, transport and orchestration units.
(cd services/api && mvn -Dtest=MathematicalExecutionTest,VerificationCapabilitiesTest,ExecutionWireCodecTest,HttpMathematicalExecutorTest,VerificationExecutionServiceTest,VerificationSourceReaderTest test)

# Gate D — actual Java-serialized requests through the isolated Python worker.
export VERIFIEDAI_MATH_WIRE=true
(cd services/api && mvn -Dtest=VerificationExecutionContractTest test)

# Gate E — Docker REQUIRED: PostgreSQL, publication races, history, export/deletion and FKs.
(cd services/api && mvn -Dtest=VerificationLifecycleContributorTest test)
make test-minio-image
(cd services/api && mvn -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 -Dtest=VerificationExecutionIntegrationTest,VerificationPlanningIntegrationTest test)

# Gate F — architecture and Sprint 4.6 / 5.4–5.7 regression coverage (Docker REQUIRED).
(cd services/api && mvn -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 '-Dtest=*ArchitectureTest,ModularityTest,Canonical*Test,PrimarySolve*Test,Secondary*Test,VerificationPlanning*Test,VerificationSourceReaderTest,VerificationPlannerTest' test)

# Gate G — full backend and full verifier suites (Docker REQUIRED).
# Real-Qwen evaluation remains deliberately gated; cross-runtime execution is enabled above.
(cd services/api && mvn -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 test)
docker run --rm --init --memory=768m --cpus=2 --pids-limit=64 \
  --mount "type=bind,src=$PWD,dst=/workspace,readonly" \
  --workdir /workspace/services/math-verifier \
  verified-ai/math-verifier-owner:5.8 python -m pytest -p no:cacheprovider

# Gate H — repository quality, documentation, contracts and immutable existing schemas.
make JAVA21_HOME="$JAVA_HOME" VERIFIER_PYTHON="$VERIFIEDAI_VERIFIER_PYTHON" lint docs-check contracts-check secret-scan
python3 -m unittest discover -s scripts/quality -p test_prompt_immutability.py
python3 scripts/quality/check_prompt_immutability.py --base-ref eec8d6a2ec4ec1a1122adf0dd0111af831ba978d
# Hosted CI remains NOT RUN until a separately authorized commit/publication exists.
OWNER_VALIDATION
```

## H. Documentation

Updated verification engine, domain invariants/math representation, module contracts, data model/retention, threat/privacy documentation,
CAP-VERIFY-001 / REQ-VERIFY-001 contribution notes, index/manifest and this implementation map/report.
Capability/requirement coverage remains Partial because later verification policy is deferred. Local owner acceptance is established;
exact-commit hosted CI and production rollout approval remain pending.
Deferred: calculus, numeric/composite policy, arbitration, overall VERIFIED policy, unrestricted solving and general solution-set representation.
Unsupported: global function identities, arbitrary nonlinear restrictions, irrational candidate roots and most transcendental exact evaluations.
Tangent singularities requiring a pi constant cannot be expressed in canonical v1; nonzero tangent inputs remain unsupported rather than guessed.

## I. Rollout and rollback

All execution flags remain false. No production rollout is approved.
After owner acceptance, deploy additive V021, compatible Java/Python versions and private networking with injected secrets.
Any rollout must separately approve methods/subsets and configure Java execution-enabled/owner-accepted/method allowlist plus Python execution-enabled.
Runtime outage is a method execution failure, independent of static capability support.
Rollback disables both execution boundaries, retaining immutable history and the additive schema. Do not drop V021 or rewrite historical evidence.
At most five sequential method operations occur per run; worst-case transport budget is bounded but latency/capacity require owner measurement.

## J. Final verdict

SPRINT_5.8_OWNER_ACCEPTANCE_PASS

Applies to the owner-tested implementation and local gates. The evidence-only documentation refresh awaits docs-check;
hosted CI on the final reviewed commit and production rollout are not approved or verified.
