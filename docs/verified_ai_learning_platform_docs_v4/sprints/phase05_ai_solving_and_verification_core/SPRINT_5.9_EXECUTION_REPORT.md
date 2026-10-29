# Sprint 5.9 — Execution report

## A. Baseline and protected work

Date: 2026-10-29 (Europe/Istanbul). Repository root:
`/Users/burakcoskun/Desktop/ai_verification_tutor`.
Initial branch `codex/fix-ios-ci-simulator`, HEAD `be0a37e30829dfc316be9ed4f0d044e08cd8ea1f`.
Initial tracked/index/untracked state was clean; no owner modifications required preservation.
No applicable AGENTS.md or preexisting Sprint 5.9 implementation/map was found.
Read-only remote discovery established actual main as
`a4e1070cfdbdda0a1025cf022c022119399c46e1`; fetch and a new
`codex/sprint-5-9-calculus` branch use that exact baseline. No commit, push, PR or deployment.
Sprint 5.8 implementation and merged PR #33 simulator repair are present.
Historical owner acceptance is not Sprint 5.9 validation evidence.

## B. Architecture and actual implementation

PARTIAL: the source-backed calculus canonical prerequisite is absent. ProblemParse v1
recognizes task labels but has no typed approach point/direction; canonical-problem-v1
and solution-candidate-v1 accept only non-calculus tasks. Inferring metadata from text
would violate the requested trust boundary. The explicitly authorized safe fallback
is implemented: an isolated versioned calculus executor and Java/Python contract seam.
No production planner, source reader, executor bean or calculus publication is added.

New internal contracts: calculus-task-v1 scope, calculus-execution-v1 request,
calculus-execution-result-v1 result, method v1 and bounded-calculus-v1 algorithm.
All published v1 canonical/candidate/execution schemas and meanings remain unchanged.
There is no canonical representation upgrade in persistence. Opaque scope UUID plus
source/candidate revisions/versions bind returned evidence; they are not authorization.
Missing metadata and incompatible representations fail schema/Java validation. Worker
scope mismatch is EXECUTION_FAILED/INVALID_RESPONSE. Existing source mismatch/staleness,
authorization and publication behavior is preserved in the unchanged application pipeline.

The authenticated internal calculus HTTP route shares the existing spawn supervisor and
finite admission pool. Calculus defaults disabled; enabling outside local/test is rejected.
The Java wire codec is intentionally not installed as an application executor.

## C. Actual file inventory

### Java production

| File | Actual purpose |
| --- | --- |
| `services/api/src/main/java/com/verifiedai/verification/domain/model/CalculusExecution.java` | Typed internal scope, interval, method-specific proof and non-proof invariants. |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/http/CalculusWireCodec.java` | Strict allowlisted request/result bytes, limits, explicit UUID text and scope correlation; no production bean. |
| `services/api/src/main/java/com/verifiedai/verification/infrastructure/http/HttpMathematicalExecutor.java` | Pins existing verifier transport to HTTP/1.1 to avoid unsupported h2c POST upgrades. |

### Python production

| File | Actual purpose |
| --- | --- |
| `services/math-verifier/app/api/schemas/calculus.py` | Typed calculus task/result validation, versions and proof/outcome/reason consistency. |
| `services/math-verifier/app/verifiers/calculus.py` | Structural differentiation, antiderivative backchecks, rational finite limits and bounded non-proof diagnostics. |
| `services/math-verifier/app/verifiers/calculus_domains.py` | Original AST obligations, open interval domain proof and punctured-neighborhood restrictions. |
| `services/math-verifier/app/policies/process_executor.py` | Version-specific dispatch and validated result decoding within the existing supervisor/pool. |
| `services/math-verifier/app/api/routes/verify.py` | Authenticated separately gated internal calculus route. |
| `services/math-verifier/app/config.py` | Disabled default and explicit prohibition on production calculus activation. |
| `services/math-verifier/app/observability/metrics.py` | Bounded calculus method/reason labels alongside unchanged v1 labels. |
| `services/math-verifier/app/main.py` | Allowlisted calculus route name in sanitized request metrics/logging. |

### Shared contracts and fixtures

| File | Actual purpose |
| --- | --- |
| `packages/schemas/calculus-execution.schema.json` | Additive strict AST task schema with required approach/integral metadata. |
| `packages/schemas/calculus-execution-result.schema.json` | Additive method/outcome-specific proof schema and non-proof diagnostic branches. |
| `packages/contracts/openapi/internal-math-verifier.yaml` | Documents the internal calculus path; existing operations retain semantics. |
| `packages/test-fixtures/verification/calculus-v1/cases.json` | 43 hand-authored positive/adversarial cases shared by Java and Python; no generated mathematical oracles. |

### Tests

| File | Actual purpose |
| --- | --- |
| `services/api/src/test/java/com/verifiedai/verification/domain/CalculusExecutionTest.java` | Capability prohibition, domain scope, exact witness and diagnostic invariants. |
| `services/api/src/test/java/com/verifiedai/verification/infrastructure/CalculusWireCodecTest.java` | Shared request roundtrips, explicit UUID regression and malicious/incompatible result rejection. |
| `services/api/src/test/java/com/verifiedai/verification/infrastructure/HttpMathematicalExecutorTest.java` | Verifies HTTP/1.1 requests omit Upgrade and HTTP2-Settings headers, alongside existing transport safeguards. |
| `services/api/src/test/java/com/verifiedai/contract/CalculusExecutionContractTest.java` | Actual Java HTTP bytes through Python ASGI and a supervised worker for every shared fixture. |
| `services/math-verifier/tests/unit/test_calculus.py` | Mathematics, constants, domain restrictions, metadata, budgets and proof semantics. |
| `services/math-verifier/tests/contract/test_calculus_contract.py` | JSON Schema/Pydantic parity, real authenticated worker HTTP and old v1 rejection. |
| `services/math-verifier/tests/contract/calculus_http_server.py` | Owner-only ephemeral loopback ASGI harness used by the Java contract test. |
| `services/math-verifier/tests/security/test_calculus_security.py` | Authentication, disabled/production guards, process reaping, CPU/memory, admission, invalid responses and sanitized input failures. |

### Configuration and quality

| File | Actual purpose |
| --- | --- |
| `.github/workflows/math-verifier-ci.yml` | Adds calculus schema path triggers; no workflow dispatched. |
| `scripts/quality/check_contracts.py` | Registers both new calculus contract files with the repository contract inventory. |

### Documentation

| File | Actual purpose |
| --- | --- |
| `services/math-verifier/README.md` | Supported subset, numerical policy, disabled operation and owner handoff. |
| `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Registers the Sprint 5.9 map and report. |
| `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Refreshes changed/new document metadata. |
| `docs/verified_ai_learning_platform_docs_v4/ai/27_VERIFICATION_ENGINE.md` | Records scoped proof semantics and blocked production integration. |
| `docs/verified_ai_learning_platform_docs_v4/domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md` | Distinguishes internal task syntax from accepted canonical source authority. |
| `docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md` | Records internal Java ownership and absence of application activation. |
| `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md` | Records ephemeral calculus data and unchanged persistent lifecycle. |
| `docs/verified_ai_learning_platform_docs_v4/security/35_SECURITY_THREAT_MODEL.md` | Documents calculus-specific proof, isolation and release controls. |
| `docs/verified_ai_learning_platform_docs_v4/roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` | Keeps calculus capability partial and disabled. |
| `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md` | Maps authored tests to trust and mathematical resource requirements. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_IMPLEMENTATION_MAP.md` | Baseline, implementation decision, ownership, release and remediation map. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.9_EXECUTION_REPORT.md` | This evidence inventory, owner commands and honest partial verdict. |

## D. Mathematical evidence matrix

| Method | Supported subset | Proof basis | Failure boundary | Authored coverage |
| --- | --- | --- | --- | --- |
| Derivative | Single REAL variable; rational polynomial expressions, product/quotient/integer powers; SIN/COS/EXP chains; LOG/SQRT on proved strict-positive argument domains | Structural differentiation and bounded formal polynomial coefficient identity | Domain holes/boundaries block; TAN unsupported; nonzero function residual inconclusive; rational nonidentity on an open interval disproves | Constants, wrong coefficients, product, quotient, chains, log/sqrt, integer/unknown domain, holes, complexity |
| Integral | Independently differentiated antiderivative representative on one explicit connected open interval | Same exact differentiation/identity rules, applied to candidate | Does not certify C, complete family or disconnected components; wrong rational candidate disproves | 2x/x², 3x²/x³, additive constants, nonconstant changes, positive log(x), negative log(-x), domain absence |
| Finite limit | Rational-polynomial expression at rational finite point with explicit left/right/two-sided direction | Orders of vanishing; exact leading derivative ratio; original restrictions hold in a punctured neighborhood | Poles remain NONFINITE_LIMIT non-proof; excluded side/domain blocks; no infinity/nonexistence proof | Polynomials, removable holes, wrong target, both directions, poles, undefined arithmetic, excluded neighborhood |
| Limit numerical diagnostic | Bounded allowlisted AST evaluation at up to 8 points per required side | No proof; binary64 policy calculus-binary64-v1, 1e-8 absolute/relative tolerance | Always INCONCLUSIVE, even when every sample agrees/disagrees; skip invalid/unrepresentable values | Side counts, reproducibility, overflow, agreement/mismatch never proof |

Exact proof descriptors certify relationships on the declared scope, not an overall
VERIFIED result. Function atom arguments must match structurally; unsupported alternative
identities can be inconclusive even if mathematically true. Derivatives are first order;
there is no multivariable, definite/improper integration, general piecewise or series proof.
The open-interval contract conservatively rejects closed/singleton/disconnected domains.

## E. Security, persistence, performance and cost

No migration, persistent column/table, new durable owner data or training eligibility.
No account lifecycle, idempotency or historical evidence mutation. Existing PostgreSQL
FK/immutability/concurrency/export/deletion and source-before-lock rules are unchanged.
Calculus evidence cannot enter V021 through this slice. Future publication must extend
owned canonical/candidate contracts and persistence decoding without rewriting history.

Existing budgets remain: 3 s elapsed, 2 s CPU, Linux 512 MiB address space, two workers,
no queue, 64 KiB request/16 KiB result. AST depth 32, 120 nodes/expression, 1200 total,
function depth 8, exponent magnitude 12, 1024-bit guarded rational coefficients,
degree 12/128 terms; up to 16 formal function atoms. Exact Sturm root counting and
all other symbolic work run inside the supervised budget. Inspected installed SymPy
metadata is 1.14.0; no dependency version was changed or dependency installed.

No eval/exec/math-text parsing, unrestricted simplify/solve/integrate, model/provider
route or inference charge. Expected incremental paid inference cost is zero.
Runtime latency and throughput remain unmeasured. Owner Linux resource tests passed
in the 186-test checkpoint; production release acceptance is still pending. Finite method/outcome/reason
metrics include disabled, unsupported, timeout, admission and numerical fallback events.
Invalid responses and worker failures remain distinct machine-readable non-proof failures.
No raw AST, student data, arbitrary metric labels or exception text is logged by the new path.

## F. Owner validation plan and commands

All local owner gates A–H passed for the isolated implementation. The agent executed no tests, compilation, builds, linters,
formatters, schema/contract/doc validation, security scans, Docker workloads or application
startup. Source reading and manual review are not passing validation evidence.
No validation dependency was installed. Fixture/schema file authoring used standard-library
serialization only; the mathematics was not executed to produce expected answers.

Owner evidence is complete for A–H: Linux verifier 186 passed; backend 566 passed
with two intentional paid-model skips (568 reported), actual Java-to-Python HTTP and
Maven BUILD SUCCESS; lint, documentation, contracts, secrets and immutability passed.
The evidence-only documentation refresh after the 13:29 checkpoint needs one final
owner docs-check. The block below runs that remaining check and retains all passing
evidence. No implementation, test, contract, configuration or dependency changed in
this refresh. No paid Qwen evaluation is activated.

| Gate | Reproducible command coverage | Evidence status |
| --- | --- | --- |
| A | Full Linux verifier suite, including test_calculus.py and existing arithmetic/regression cases | OWNER-TESTED PASS: included in 186/186 Linux tests |
| B | Full Linux verifier security/contract suite including CPU/memory enforcement | OWNER-TESTED PASS: included in 186/186 Linux tests |
| C | Full backend suite including CalculusExecutionTest and CalculusWireCodecTest | OWNER-TESTED PASS: included in 566 backend passes |
| D | CalculusExecutionContractTest actual authenticated HTTP for shared fixtures; VerificationExecutionContractTest v1 regression | OWNER-TESTED PASS: both calculus HTTP tests and v1 regression passed |
| E | Existing VerificationExecutionIntegrationTest, VerificationPlanningIntegrationTest, VerificationLifecycleContributorTest in full backend suite | OWNER-TESTED PASS: included in 566 backend passes |
| F | Existing architecture, modularity, canonical source and Sprint 5.7/5.8 tests in full backend suite | OWNER-TESTED PASS: included in 566 backend passes |
| G | Full Linux verifier and full backend suites, with no duplicate test commands | OWNER-TESTED PASS: Python 186; backend 566 with two intentional paid-model skips |
| H | Maven verify, lint/docs/contracts/secrets, immutability units/base check and unchanged released schema diff | OWNER-TESTED PASS: build, lint, docs (296 files), contracts, secrets, five immutability units and unchanged released versions |

```bash
bash <<'OWNER_VALIDATION'
set -euo pipefail
verification_root="$(git rev-parse --show-toplevel)"
cd "$verification_root"
verification_output="$verification_root/.generated/sprint-5-9-owner/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$verification_output"
exec > >(tee "$verification_output/acceptance.log") 2>&1
verification_finish() {
verification_status=$?
trap - EXIT
printf '\nValidation exit status: %s\nLogs: %s\n' "$verification_status" "$verification_output"
exit "$verification_status"
}
trap verification_finish EXIT
printf '\nRetaining all owner A-H evidence. Checking the final evidence-only documentation refresh.\n'
make docs-check
printf '\nDocumentation refresh checked. Sprint 5.9 remains PARTIAL; production source integration BLOCKED, rollout disabled, hosted CI unrun.\n'
OWNER_VALIDATION
```


### Owner checkpoint — 2026-10-29, 13:11–13:12 +03:00

Owner log: `.generated/sprint-5-9-owner/20261029-131107/acceptance.log`.
Linux Python: 186 passed, one Starlette/httpx deprecation warning, 8.72 seconds.
This includes calculus mathematics, typed schemas, authentication and CPU/memory/process
acceptance. The owner also compiled Java production and test source successfully.

The Java run stopped at CalculusExecutionContractTest: the first derivative-constant
HTTP request returned 422. Summary: 535 reported tests, one failure, 528 skipped by
fail-fast (six passed). No passing full backend, cross-runtime, persistence, architecture,
package/build or Gate H result is inferred from the compilation or skipped tests.

Source diagnosis: CalculusWireCodec used Jackson valueToTree on the scope record.
UUID serialization through a native-binary TokenBuffer can create a binary tree node,
which becomes base64 JSON instead of the UUID text required by Python. Scope identity
is now written explicitly with UUID.toString(); interval exact values, approach and
integral scope also use explicit wire construction. No mathematical schema is relaxed.
A focused UUID regression and shared-fixture request equality before HTTP are authored.
Unexpected HTTP results now include the sanitized verifier error code in test diagnostics.

Only Java codec/tests and documentation changed for this repair. Retain the owner 186-test
Linux evidence; rerun the complete backend suite and Gate H using the block above.
No repair tests, compilation, lint or runtime probes were executed by the agent.

### Owner checkpoint — 2026-10-29, 13:19 +03:00

Owner log: `.generated/sprint-5-9-owner/20261029-131942/acceptance.log`.
Java recompiled successfully. The new serialized-request equality check passed for the
first shared fixture; the UUID correction alone did not resolve the observed failure.
The server again logged HTTP 422, but the Java client received a non-JSON body starting
with Invalid. The diagnostic added in the previous repair incorrectly parsed every
response as JSON and raised JsonParseException, masking the original HTTP status.
Summary: 536 reported tests, one error, 529 skipped (six passed). Remaining backend
and Gate H results are still unestablished.

Source trace: Java HttpClient defaults to HTTP/2 preference and can send cleartext h2c
upgrade headers. The installed httptools parser returns skip-body for upgrade requests;
Uvicorn supports WebSocket upgrades, not h2c, and does not resume the skipped POST body
as ordinary JSON. This accounts for an empty-body 422 plus the plain HTTP parser error.
Both the actual calculus contract client and the existing production verifier adapter
now explicitly select HTTP/1.1. Tests assert the wire protocol and absence of Upgrade /
HTTP2-Settings headers. Response diagnostics handle plain-text, oversized and malformed
JSON bodies and expose only allowlisted verifier codes, without echoing payloads.

The earlier UUID diagnosis was incomplete: this checkpoint establishes request-shape
parity, not a successful HTTP or mathematical result. All fixes remain owner-test pending.
Python source/tests/shared contracts are unchanged, so retain the 186-test Linux evidence.
No tests, builds, compilation, linters or runtime probes were executed by the agent.

### Owner checkpoint — 2026-10-29, 13:24–13:25 +03:00

Owner log: `.generated/sprint-5-9-owner/20261029-132401/acceptance.log`.
Maven verify completed with BUILD SUCCESS: 568 reported tests, zero failures/errors,
two intentional QwenRealModelEvaluationTest skips, hence 566 passing tests.
CalculusExecutionContractTest passed both tests, including all shared fixtures over real
HTTP; HttpMathematicalExecutorTest passed all five tests. Full backend persistence,
authorization, lifecycle, architecture and prior-version regressions passed.

Gate H stopped at Ruff: six I001 import organization findings and one SIM102 nested
interval condition. These seven findings were repaired manually without running a
formatter or validator. Imports are ordered/formatted and the interval guard uses the
same short-circuit checks in one condition. No schema, fixture, mathematical behavior,
Java code or dependency changed. Retain the passing Linux and backend evidence; the
block above reruns only Gate H, including the compile step required by repository lint.
Documentation, contract, secret and immutability checks have no passing evidence yet.
No validation was executed by the agent.

### Owner checkpoint — 2026-10-29, 13:29 +03:00

Owner log: `.generated/sprint-5-9-owner/20261029-132903/acceptance.log`.
Validation exit status 0. Ruff and repository compile lint passed; docs-check passed
for 296 Markdown files; contract check and secret scan passed. Five prompt immutability
unit tests passed; the baseline prompt/schema version gate reported unchanged existing
versions; the released v1 schema diff was empty. Retain the earlier Linux 186 passing
and backend 566 passing/two intentional paid-model skips with BUILD SUCCESS.
All local A–H gates are now OWNER-TESTED PASS for the isolated scope.

Only documentation was updated to record this checkpoint. Final evidence-documentation
refresh awaits the owner's docs-check using the block above; no code or suite rerun is
required. The next passing docs-check can be recorded by its owner log without another
document edit. Hosted CI and end-to-end source-backed calculus remain outside this
passing local evidence. The agent executed no validation.

## G. Rollout, rollback and unresolved blockers

- ROLLOUT_DISABLED: keep MATH_VERIFIER_CALCULUS_ENABLED false. Production enabling is rejected.
- HOSTED_CI_NOT_RUN: workflow trigger support is authored; no workflow was dispatched or status claimed.
- BLOCKED: typed source-backed calculus metadata, versioned canonical/candidate acquisition,
  owned source reader, planner eligibility and immutable calculus evidence publication.
- OWNER-TESTED PASS: all local A–H gates for the isolated executor.
- OWNER_VALIDATION_PENDING: only the final evidence-documentation refresh docs-check.

Rollback leaves the calculus flag false and retains additive contracts; no down migration,
source rewrite or historical evidence change is required. Existing arithmetic capability
flags retain their prior meaning. Future source integration must retain canonical-read-before-lock
ordering, no network in transactions, owner reauthorization and stale publication rejection.

## H. Final verdict

PARTIAL — isolated executor and typed contract slice have OWNER-TESTED PASS for all
local A–H gates. Only final evidence-documentation refresh docs-check remains pending.
Sprint 5.9 end-to-end acceptance is BLOCKED on canonical source integration.
ROLLOUT_DISABLED / HOSTED_CI_NOT_RUN. Sprint 5.9 is not complete.
