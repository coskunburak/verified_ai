#!/usr/bin/env bash
# Owner-only acceptance script for Sprint 6.1.
# Never invokes paid models, promotes AI baselines, deploys, or mutates Git state.
set -Eeuo pipefail

s61_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s61_root"
mkdir -p .generated/sprint-6-1-owner
s61_output="$(mktemp -d "$s61_root/.generated/sprint-6-1-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
ln -sfn "$s61_output" "$s61_root/.generated/sprint-6-1-owner/latest"
exec > >(tee "$s61_output/acceptance.log") 2>&1

s61_stage="prerequisites"
s61_failure="FAIL"
s61_overall=0
s61_simulator=""
s61_java_started=false

for gate in prerequisites A B C D; do
    printf 'NOT_RUN\n' > "$s61_output/$gate.status"
done
printf 'SKIPPED_BY_POLICY\n' > "$s61_output/paid_models.status"

finish() {
    local exit_code=$?
    trap - EXIT ERR
    set +e
    if [[ "$s61_java_started" == true ]]; then
        mkdir -p "$s61_output/maven-reports"
        for reports in surefire-reports failsafe-reports; do
            if [[ -d "services/api/target/$reports" ]]; then
                cp -R "services/api/target/$reports" "$s61_output/maven-reports/"
            fi
        done
    fi
    if [[ -n "$s61_simulator" ]]; then
        xcrun simctl shutdown "$s61_simulator" >> "$s61_output/cleanup.log" 2>&1
        xcrun simctl delete "$s61_simulator" >> "$s61_output/cleanup.log" 2>&1
        if [[ $? -ne 0 && "$exit_code" -eq 0 ]]; then
            exit_code=1
        fi
    fi
    if [[ "$exit_code" -ne 0 ]]; then
        s61_overall="$exit_code"
        printf '%s\n' "$s61_failure" > "$s61_output/$s61_stage.status"
        if [[ "$s61_stage" == "prerequisites" ]]; then
            for gate in A B C D; do
                printf 'BLOCKED\n' > "$s61_output/$gate.status"
            done
        fi
    fi
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$s61_output" "$s61_overall" <<'SUMMARY'
import json
import sys
from pathlib import Path

out = Path(sys.argv[1])
exit_code = int(sys.argv[2])
gates = {p.stem: p.read_text().strip() for p in sorted(out.glob("*.status"))}
required = ["prerequisites", "A", "B", "C", "D"]
passed = exit_code == 0 and all(gates.get(k) == "PASS" for k in required)
status = "PASS" if passed else ("BLOCKED" if "BLOCKED" in gates.values() else "FAIL")
focused_ios = json.loads((out / "ios-focused-summary.json").read_text()) if (out / "ios-focused-summary.json").is_file() else None
backend_reg = json.loads((out / "backend-summary.json").read_text()) if (out / "backend-summary.json").is_file() else None

summary = {
    "version": "sprint-6-1-owner-v1",
    "sprint": "6.1",
    "localAcceptance": "LOCAL_ACCEPTANCE_PASSED" if passed else status,
    "overall": status,
    "exitCode": 0 if passed else (exit_code if exit_code != 0 else 1),
    "gates": gates,
    "iosFocusedEvidence": focused_ios,
    "backendAndEvaluationEvidence": backend_reg,
    "hostedCI": "NOT_RUN",
    "paidModels": "SKIPPED_BY_POLICY",
    "realModelEvaluation": "BLOCKED",
    "protectedHoldout": "BLOCKED",
    "productionPromotion": "BLOCKED",
    "productionReleaseApproved": "BLOCKED",
    "productionRollout": "DISABLED",
    "limitations": [
        "Local owner acceptance validates deterministic contracts, iOS unit/UI solution results behavior, and backend/evaluation non-regression.",
        "Hosted CI, real-provider evaluation, protected holdout, and production solver release approval remain separate external gates."
    ]
}
(out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
sys.exit(summary["exitCode"])
SUMMARY
        s61_overall=$?
    else
        s61_overall=2
        printf '{"overall":"BLOCKED","localAcceptance":"BLOCKED","reason":"python3 missing","exitCode":2}\n' > "$s61_output/summary.json"
    fi
    printf '\nEvidence directory: %s\nSummary JSON: %s/summary.json\nExit code: %s\n' "$s61_output" "$s61_output" "$s61_overall"
    exit "$s61_overall"
}
trap finish EXIT
trap 'printf "Failure: stage=%s line=%s status=%s\n" "$s61_stage" "$LINENO" "$?" >&2' ERR

block() {
    s61_failure="BLOCKED"
    printf '%s\n' "$1" >&2
    exit 2
}

run_gate() {
    s61_stage="$1"
    shift
    local stage_exit
    set +e
    (set -Eeuo pipefail; "$@") > >(tee "$s61_output/$s61_stage.log") 2>&1
    stage_exit=$?
    set -e
    printf '%s\n' "$stage_exit" > "$s61_output/$s61_stage.exit"
    if [[ "$stage_exit" -eq 0 ]]; then
        printf 'PASS\n' > "$s61_output/$s61_stage.status"
    else
        printf '%s\n' "$s61_failure" > "$s61_output/$s61_stage.status"
        s61_overall="$stage_exit"
        return "$stage_exit"
    fi
}

export PATH="/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path:/opt/homebrew/bin:/usr/local/bin:$PATH"
export JAVA_HOME="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
export VERIFIER_PYTHON="${VERIFIER_PYTHON:-$s61_root/services/math-verifier/.venv/bin/python}"
export VERIFIEDAI_VERIFIER_PYTHON="$VERIFIER_PYTHON"
export VERIFIEDAI_MATH_WIRE=true
export VERIFICATION_ARBITRATION_ENABLED=false
export VERIFICATION_ARBITRATION_OWNER_ACCEPTED=false
export VERIFICATION_CALCULUS_ENABLED=false
export VERIFICATION_CALCULUS_OWNER_ACCEPTED=false
export MATH_VERIFIER_CALCULUS_ENABLED=false
export VERIFICATION_NUMERIC_ENABLED=false
export CALCULUS_FIXTURE_ENABLED=false
export PYTHONDONTWRITEBYTECODE=1
unset QWEN_OWNER_RUN

for executable in git java mvn python3 docker xcrun xcodebuild rg make; do
    command -v "$executable" >/dev/null 2>&1 || block "Required tool missing: $executable"
done
[[ -x "$VERIFIER_PYTHON" ]] || block "Provision the repository verifier Python 3.11/3.12 environment first: $VERIFIER_PYTHON"
java -version 2>&1 | tee "$s61_output/java-version.log"
java -XshowSettings:properties -version 2>&1 | rg 'java.specification.version = 21$' >/dev/null || block "Java 21 is required."
"$VERIFIER_PYTHON" -c 'import sys; assert sys.version_info[:2] in {(3,11),(3,12)}; import fastapi, uvicorn, pydantic, sympy, numpy, httpx, jsonschema, pytest, ruff' || block "Verifier Python environment lacks a supported interpreter or declared dependency."
mvn -version | tee "$s61_output/maven-version.log"
"$VERIFIER_PYTHON" --version | tee "$s61_output/python-version.log"
docker info > "$s61_output/docker-info.log" || block "A running Docker engine is required."
xcodebuild -version | tee "$s61_output/xcode-version.log"
xcrun simctl list --json > "$s61_output/simulators.json"

s61_failure="BLOCKED"
python3 - "$s61_output/simulators.json" > "$s61_output/simulator-selection.txt" <<'SIMULATOR'
import json
import sys
from pathlib import Path

inventory = json.loads(Path(sys.argv[1]).read_text())
choices = []
for runtime in inventory.get("runtimes", []):
    if not runtime.get("isAvailable") or not runtime.get("identifier", "").startswith("com.apple.CoreSimulator.SimRuntime.iOS-"):
        continue
    version = tuple(int(x) for x in runtime["version"].split("."))
    if version < (17,):
        continue
    for device in inventory.get("devices", {}).get(runtime["identifier"], []):
        kind = device.get("deviceTypeIdentifier", "")
        if device.get("isAvailable") and kind.startswith("com.apple.CoreSimulator.SimDeviceType.iPhone-"):
            choices.append((version, kind, runtime["identifier"]))
if not choices:
    sys.exit("BLOCKED: install an available iPhone simulator with iOS 17 or newer in Xcode.")
_, kind, runtime = max(choices)
print(runtime, kind)
SIMULATOR
read -r s61_runtime s61_device_type < "$s61_output/simulator-selection.txt"
s61_failure="FAIL"

git rev-parse HEAD > "$s61_output/head.txt"
git branch --show-current > "$s61_output/branch.txt"
git status --short > "$s61_output/working-tree.txt"
printf 'PASS\n' > "$s61_output/prerequisites.status"

gate_a_governance_and_contracts() {
    find "$s61_root" -name .DS_Store -delete
    python3 scripts/quality/docs_check.py
    python3 scripts/quality/check_contracts.py
    scripts/security/secret_scan.sh
    python3 -m unittest discover -s scripts/quality -p test_prompt_immutability.py
    python3 -m unittest discover -s scripts/security -p test_secret_scan.py
    python3 scripts/quality/check_prompt_immutability.py --base-ref 66f00200be48f24f5f690ff32fc0bd376ad952e2
    python3 - <<'IMMUTABLE'
import subprocess
from pathlib import Path

base = "66f00200be48f24f5f690ff32fc0bd376ad952e2"
paths = subprocess.check_output(
    ["git", "ls-tree", "-r", "--name-only", base, "--",
     "packages/schemas", "services/api/src/main/resources/db/migration/platform", "packages/test-fixtures/verification"],
    text=True
).splitlines()
for path in paths:
    previous = subprocess.check_output(["git", "show", f"{base}:{path}"])
    if not Path(path).is_file() or Path(path).read_bytes() != previous:
        raise SystemExit(f"Released artifact changed: {path}")
head_policy = subprocess.check_output(["git", "show", "HEAD:evaluations/baselines/solver-release-policy-v1.json"])
if Path("evaluations/baselines/solver-release-policy-v1.json").read_bytes() != head_policy:
    raise SystemExit("Solver release policy baseline changed unexpectedly")
print("Released schemas, migrations, verification fixtures, and solver release policy retain exact bytes.")
IMMUTABLE
    git diff --check
}
run_gate A gate_a_governance_and_contracts

gate_b_ios_acceptance() {
    s61_simulator="$(xcrun simctl create "VerifiedAI-Sprint61-$(basename "$s61_output")" "$s61_device_type" "$s61_runtime")"
    xcrun simctl boot "$s61_simulator"
    xcrun simctl bootstatus "$s61_simulator" -b
    NSUnbufferedIO=YES xcodebuild test \
        -project apps/ios/VerifiedAI.xcodeproj \
        -scheme VerifiedAI \
        -destination "platform=iOS Simulator,id=$s61_simulator" \
        -destination-timeout 120 \
        -parallel-testing-enabled NO \
        SWIFT_EMIT_LOC_STRINGS=NO \
        -derivedDataPath "$s61_output/DerivedData" \
        -resultBundlePath "$s61_output/VerifiedAI.xcresult"

    python3 - "$s61_output/B.log" "$s61_output/ios-focused-summary.json" <<'IOS_AUDIT'
import json
import re
import sys
from pathlib import Path

log_text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
pattern = re.compile(r"Test Case '-\[(?P<target>[^.\s]+)\.(?P<suite>[^\s]+)\s+(?P<test>[^\]]+)\]' (?P<outcome>passed|failed|skipped)")
records = {}
for match in pattern.finditer(log_text):
    key = (match.group("suite"), match.group("test"))
    records[key] = match.group("outcome")

required_suites = {
    "PrimarySolveContractTests": {
        "testAllLifecycleStatesDecodeAndOnlyCandidateStatesExposeAnswers",
        "testMissingFieldsUnknownStateAndUntrustedVerificationNoticeFailClosed",
        "testUnsupportedCandidateSchemaV2FailsClosedAsUnsupportedRatherThanNetworkOrVerified",
        "testVerificationFixturesHaveExplicitScopeAndNeverChangeGenerationState",
    },
    "PrimarySolveViewModelTests": {
        "testVerificationClearsOnOfflineSuspendAndNeverEnablesSolveRetry",
        "testUnsupportedCandidateVersionAndPollingLineageMismatchFailClosed",
        "testVerificationContradictionExposesProblemReviewAndJobChangeClearsVerdict",
        "testControlledFixturePreservesEveryPhaseUntilAcknowledgedAndPollsTheSameJob",
        "testControlledFixtureCancellationAndRelaunchResumeRunningWithoutAnotherStart",
    },
    "MathPresentationTests": {
        "testGroupingNegativeBasesAndNegativeExponentsRemainDistinct",
        "testRelationsFunctionsAndExactNumericStrings",
        "testSpokenRepresentationPreservesSemanticDistinctions",
    },
    "SolveRecoveryStoreTests": {
        "testRelaunchAccountIsolationScopeIsolationAndDeletion",
    },
    "PrimarySolveUITests": {
        "testServerVerificationStatesAndConflictHaveAccessibleHonestPresentation",
        "testHistoryQueuedRunningUnverifiedStepsAndReopen",
        "testCalculusProofScopesRemainExplicitAndSeparateFromGeneration",
        "testReviewRequiredDisplaysUncertainty",
        "testRetryRequiresUserAction",
        "testStaleHidesAnswerAndUnknownOutcomeHasNoRetry",
        "testRelaunchRecoversJobWithoutNewStart",
        "testMalformedResponseHasNoFakeAnswer",
        "testTurkishLargeTextKeepsNoticeAndControlsAccessible",
    },
}

suite_counts = {}
for (suite, test), outcome in records.items():
    if outcome != "passed":
        raise SystemExit(f"iOS test did not pass: {suite}.{test} -> {outcome}")
    suite_counts[suite] = suite_counts.get(suite, 0) + 1

for suite, tests in required_suites.items():
    if suite_counts.get(suite, 0) == 0:
        raise SystemExit(f"Missing required iOS test suite execution: {suite}")
    for test in sorted(tests):
        if records.get((suite, test)) != "passed":
            raise SystemExit(f"Missing required Sprint 6.1 test case: {suite}.{test}")

summary = {
    "totalExecutedCases": len(records),
    "focusedSuiteCounts": {k: suite_counts.get(k, 0) for k in sorted(required_suites)},
    "status": "PASS",
}
Path(sys.argv[2]).write_text(json.dumps(summary, indent=2) + "\n")
print(f"Audited {len(records)} passing iOS test cases across all unit and UI suites without skips.")
IOS_AUDIT
}
run_gate B gate_b_ios_acceptance

gate_c_backend_and_eval_regression() {
    "$VERIFIER_PYTHON" -m unittest evaluations/runners/test_solver_evaluation.py evaluations/runners/test_solver_release.py
    "$VERIFIER_PYTHON" evaluations/runners/solver_evaluation.py --mode local --output "$s61_output/solver-eval"
    s61_java_started=true
    (
        cd services/api
        mvn -Dsurefire.skipAfterFailureCount=1 \
            -Dtest="PrimarySolveControllerTest,PrimarySolveIntegrationTest,SolutionCandidateValidatorTest,CanonicalSolutionServiceTest,VerificationPolicyTest,VerificationArbitrationServiceTest,VerificationDecisionControllerTest,SolverReleaseRegistryTest,SolverReleaseEvidenceTest" \
            test
    )
    python3 - "$s61_output/solver-eval/solver-evaluation-report.json" "$s61_output/backend-summary.json" <<'BACKEND_AUDIT'
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

report = json.loads(Path(sys.argv[1]).read_text())
if report.get("gate", {}).get("decision") != "PASS" or report.get("productionPromotion", {}).get("decision") != "BLOCKED":
    raise SystemExit("Solver evaluation regression failed or unexpectedly unblocked production promotion")

required = {
    "PrimarySolveControllerTest",
    "PrimarySolveIntegrationTest",
    "SolutionCandidateValidatorTest",
    "CanonicalSolutionServiceTest",
    "VerificationPolicyTest",
    "VerificationArbitrationServiceTest",
    "VerificationDecisionControllerTest",
    "SolverReleaseRegistryTest",
    "SolverReleaseEvidenceTest",
}
seen = {}
for xml_path in Path("services/api/target/surefire-reports").glob("TEST-*.xml"):
    root = ET.parse(xml_path).getroot()
    name = root.attrib.get("name", "").split(".")[-1]
    if name not in required:
        continue
    cases = root.findall("testcase")
    if not cases or any(c.find(tag) is not None for c in cases for tag in ("skipped", "failure", "error")):
        raise SystemExit(f"Backend regression suite failed or skipped cases: {name}")
    seen[name] = len(cases)

missing = required - set(seen)
if missing:
    raise SystemExit(f"Missing required backend regression suites: {sorted(missing)}")

summary = {
    "backendSuites": seen,
    "solverEvaluationGate": report["gate"]["decision"],
    "productionPromotion": report["productionPromotion"]["decision"],
    "status": "PASS",
}
Path(sys.argv[2]).write_text(json.dumps(summary, indent=2) + "\n")
print("Backend solving/verification contract suites and Sprint 5.12 evaluation checks passed.")
BACKEND_AUDIT
}
run_gate C gate_c_backend_and_eval_regression

gate_d_final_audit() {
    python3 - "$s61_output" <<'FINAL_AUDIT'
import json
import sys
from pathlib import Path

out = Path(sys.argv[1])
for gate in ("prerequisites", "A", "B", "C"):
    status_file = out / f"{gate}.status"
    if not status_file.is_file() or status_file.read_text().strip() != "PASS":
        raise SystemExit(f"Required gate did not pass: {gate}")
ios_summary = json.loads((out / "ios-focused-summary.json").read_text())
backend_summary = json.loads((out / "backend-summary.json").read_text())
if ios_summary.get("status") != "PASS" or backend_summary.get("status") != "PASS":
    raise SystemExit("Focused evidence summaries incomplete")
print("Sprint 6.1 owner acceptance gates A-D reconciled successfully.")
FINAL_AUDIT
}
run_gate D gate_d_final_audit
