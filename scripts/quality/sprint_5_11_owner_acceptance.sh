#!/usr/bin/env bash
# Human-owner execution only. Creating this script does not authorize an agent to run it.
set -Eeuo pipefail
verification_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$verification_root"
mkdir -p .generated/sprint-5-11-owner
verification_output="$(mktemp -d "$verification_root/.generated/sprint-5-11-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
exec > >(tee "$verification_output/acceptance.log") 2>&1
verification_stage=prerequisites
verification_simulator=''
verification_container=''
verification_failure=FAIL
verification_java_started=false
for gate in A B C D E F G H; do printf 'NOT_RUN\n' > "$verification_output/$gate.status"; done
printf 'NOT_RUN\n' > "$verification_output/prerequisites.status"
printf 'NOT_RUN\n' > "$verification_output/integration_dependency.status"
printf 'NOT_RUN\n' > "$verification_output/calculus_composite.status"
printf 'SKIPPED_BY_POLICY\n' > "$verification_output/paid_models.status"
finish() {
    verification_exit=$?
    trap - EXIT ERR
    set +e
    if [[ "$verification_java_started" == true ]]; then
        mkdir -p "$verification_output/maven-reports"
        for reports in surefire-reports failsafe-reports; do
            if [[ -d "services/api/target/$reports" ]]; then cp -R "services/api/target/$reports" "$verification_output/maven-reports/"; fi
        done
    fi
    if [[ -n "$verification_container" ]]; then
        docker cp "$verification_container:/tmp/python-results.xml" "$verification_output/python-results.xml" >> "$verification_output/cleanup.log" 2>&1
        docker rm -f "$verification_container" >> "$verification_output/cleanup.log" 2>&1
    fi
    if [[ -n "$verification_simulator" ]]; then
        xcrun simctl shutdown "$verification_simulator" >> "$verification_output/cleanup.log" 2>&1
        xcrun simctl delete "$verification_simulator" >> "$verification_output/cleanup.log" 2>&1
        if [[ $? -ne 0 && "$verification_exit" -eq 0 ]]; then verification_exit=1; fi
    fi
    if [[ "$verification_exit" -ne 0 ]]; then
        printf '%s\n' "$verification_failure" > "$verification_output/$verification_stage.status"
        if [[ "$verification_stage" == prerequisites ]]; then
            for gate in A B C D E F G H; do printf 'BLOCKED\n' > "$verification_output/$gate.status"; done
        fi
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        printf '{"overall":"BLOCKED","exitCode":%s,"reason":"python3 missing"}\n' "$verification_exit" > "$verification_output/summary.json"
        printf 'Evidence: %s\n' "$verification_output"
        exit "$verification_exit"
    fi
    python3 - "$verification_output" "$verification_exit" <<'SUMMARY'
import json
import sys
from pathlib import Path
out = Path(sys.argv[1])
statuses = {p.stem: p.read_text().strip() for p in out.glob("*.status")}
passed = int(sys.argv[2]) == 0 and all(statuses.get(k) == "PASS" for k in "ABCDEFGH")
summary = {"overall": "PASS" if passed else "BLOCKED" if "BLOCKED" in statuses.values() else "FAIL",
           "exitCode": int(sys.argv[2]), "gates": statuses, "hostedCI": "NOT_RUN",
           "productionRollout": "DISABLED", "calculusComposite": statuses.get("calculus_composite", "NOT_RUN")}
(out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
SUMMARY
    printf '\nEvidence: %s\nExit: %s\n' "$verification_output" "$verification_exit"
    exit "$verification_exit"
}
trap finish EXIT
trap 'printf "Failure: stage=%s line=%s status=%s\n" "$verification_stage" "$LINENO" "$?" >&2' ERR
run_gate() {
    verification_stage="$1"
    shift
    "$@" > >(tee "$verification_output/$verification_stage.log") 2>&1
    printf 'PASS\n' > "$verification_output/$verification_stage.status"
}
block() { verification_failure=BLOCKED; printf '%s\n' "$1" >&2; exit 2; }
export PATH="/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path:/opt/homebrew/bin:/usr/local/bin:$PATH"
export JAVA_HOME="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
export VERIFIER_PYTHON="${VERIFIER_PYTHON:-$verification_root/services/math-verifier/.venv/bin/python}"
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
    command -v "$executable" || block "Required tool missing: $executable"
done
[[ -x "$VERIFIER_PYTHON" ]] || block 'Provision the repository verifier Python 3.11/3.12 environment with its declared dev dependencies first.'
java -version 2>&1 | tee "$verification_output/java-version.log"
java -XshowSettings:properties -version 2>&1 | rg 'java.specification.version = 21$' >/dev/null || block 'Java 21 is required.'
"$VERIFIER_PYTHON" -c 'import sys; assert sys.version_info[:2] in {(3,11),(3,12)}; import fastapi, uvicorn, pydantic, sympy, numpy, httpx, jsonschema, pytest, ruff' || block 'Verifier Python environment lacks a supported interpreter or declared dev dependency.'
mvn -version | tee "$verification_output/maven-version.log"
"$VERIFIER_PYTHON" --version | tee "$verification_output/python-version.log"
docker info > "$verification_output/docker-info.log" || block 'A running Docker engine is required.'
xcodebuild -version | tee "$verification_output/xcode-version.log"
xcrun simctl list --json > "$verification_output/simulators.json"
verification_failure=BLOCKED
python3 - "$verification_output/simulators.json" > "$verification_output/simulator-selection.txt" <<'SIMULATOR'
import json
import sys
from pathlib import Path
inventory = json.loads(Path(sys.argv[1]).read_text())
choices = []
for runtime in inventory["runtimes"]:
    if not runtime.get("isAvailable") or not runtime["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-"):
        continue
    version = tuple(int(x) for x in runtime["version"].split("."))
    if version < (17,):
        continue
    for device in inventory["devices"].get(runtime["identifier"], []):
        kind = device.get("deviceTypeIdentifier", "")
        if device.get("isAvailable") and kind.startswith("com.apple.CoreSimulator.SimDeviceType.iPhone-"):
            choices.append((version, kind, runtime["identifier"]))
if not choices:
    sys.exit("BLOCKED: install an available iPhone simulator with iOS 17 or newer in Xcode.")
_, kind, runtime = max(choices)
print(runtime, kind)
SIMULATOR
read -r verification_runtime verification_device_type < "$verification_output/simulator-selection.txt"
verification_failure=FAIL
git rev-parse HEAD > "$verification_output/head.txt"
git branch --show-current > "$verification_output/branch.txt"
git status --short > "$verification_output/working-tree.txt"
git diff HEAD --binary > "$verification_output/tracked-changes.patch"
git ls-files --others --exclude-standard > "$verification_output/untracked.txt"
printf 'PASS\n' > "$verification_output/prerequisites.status"
linux_suite() {
    docker build --target owner-tests -t verified-ai/math-verifier-owner:5.11 services/math-verifier
    verification_container="verified-ai-sprint-511-$(basename "$verification_output")"
    docker create --name "$verification_container" --init --memory=768m --cpus=2 --pids-limit=64 \
        --mount "type=bind,src=$verification_root,dst=/workspace,readonly" --workdir /workspace/services/math-verifier \
        verified-ai/math-verifier-owner:5.11 python -m pytest -x -ra -vv -p no:cacheprovider --junitxml=/tmp/python-results.xml
    docker start -a "$verification_container"
    local test_exit
    test_exit="$(docker inspect --format '{{.State.ExitCode}}' "$verification_container")"
    docker cp "$verification_container:/tmp/python-results.xml" "$verification_output/python-results.xml"
    [[ "$test_exit" -eq 0 ]] || return "$test_exit"
    python3 - "$verification_output/python-results.xml" <<'PYTHON_REPORTS'
import sys
import xml.etree.ElementTree as ET
cases = ET.parse(sys.argv[1]).getroot().findall(".//testcase")
if not cases or any(c.find(kind) is not None for c in cases for kind in ("skipped", "failure", "error")):
    raise SystemExit("Missing, failed or skipped required Linux verifier tests")
print(f"Linux verifier executed {len(cases)} required cases without skips.")
PYTHON_REPORTS
}
run_gate linux linux_suite
backend_suite() {
    make test-minio-image
    verification_java_started=true
    (cd services/api && mvn -Dsurefire.skipAfterFailureCount=1 -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 clean verify)
    python3 - <<'REPORTS'
from pathlib import Path
import xml.etree.ElementTree as ET
reports = list(Path("services/api/target/surefire-reports").glob("TEST-*.xml"))
required = {"VerificationPolicyTest", "VerificationArbitrationServiceTest", "VerificationDecisionIntegrationTest",
            "VerificationDecisionMigrationIntegrationTest", "VerificationDecisionControllerTest",
            "VerificationArbitrationArchitectureTest", "SecondaryCorroborationQueryTest", "ModularityTest", "NumericDiagnosticContractTest",
            "CalculusProductionIntegrationTest", "CompositeEvidencePolicyTest", "CalculusCompositePolicyTest"}
required_cases = {
    ("CalculusProductionIntegrationTest", "calculusCompositeDecisionsReachOwnedHttpProjectionWithoutNewExecutionAndDeleteWithAccount"),
    ("CalculusProductionIntegrationTest", "laterCalculusEvidenceAndCapabilityRollbackHidePreviousDecision"),
    ("CalculusProductionIntegrationTest", "calculusCorrectionDuringArbitrationRejectsPublicationWithoutHoldingOwnerLock"),
    ("CalculusProductionIntegrationTest", "calculusDomainObstructionRemainsNonproofThroughPublication"),
}
seen = set()
seen_cases = set()
for report in reports:
    root = ET.parse(report).getroot()
    name = root.attrib.get("name", "").split(".")[-1]
    tests = root.findall("testcase")
    if name in required and tests and all(t.find("skipped") is None for t in tests):
        seen.add(name)
    for test in tests:
        if test.find("skipped") is None:
            seen_cases.add((name, test.attrib.get("name")))
        if test.find("failure") is not None or test.find("error") is not None:
            raise SystemExit(f"Failed case: {name}/{test.attrib.get('name')}")
        if test.find("skipped") is not None and name != "QwenRealModelEvaluationTest":
            raise SystemExit(f"Required test skipped: {name}/{test.attrib.get('name')}")
if required - seen:
    raise SystemExit(f"Missing required suite evidence: {sorted(required - seen)}")
if required_cases - seen_cases:
    raise SystemExit(f"Missing calculus integration case evidence: {sorted(required_cases - seen_cases)}")
print("Required backend suites executed; paid Qwen evaluation is explicitly SKIPPED_BY_POLICY.")
REPORTS
}
run_gate backend backend_suite
printf 'PASS\n' > "$verification_output/calculus_composite.status"
for gate in A B C D F; do
    printf 'PASS\n' > "$verification_output/$gate.status"
    ln -s backend.log "$verification_output/$gate.log"
done
ios_suite() {
    verification_simulator="$(xcrun simctl create "VerifiedAI-Sprint511-$(basename "$verification_output")" "$verification_device_type" "$verification_runtime")"
    xcrun simctl boot "$verification_simulator"
    xcrun simctl bootstatus "$verification_simulator" -b
    NSUnbufferedIO=YES xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
        -destination "platform=iOS Simulator,id=$verification_simulator" -destination-timeout 120 \
        -parallel-testing-enabled NO SWIFT_EMIT_LOC_STRINGS=NO -derivedDataPath "$verification_output/DerivedData" -resultBundlePath "$verification_output/VerifiedAI.xcresult"
}
run_gate E ios_suite
printf 'PASS\n' > "$verification_output/G.status"
printf 'Full regression logs: linux.log, backend.log, E.log\n' > "$verification_output/G.log"
quality_suite() {
    make JAVA21_HOME="$JAVA_HOME" VERIFIER_PYTHON="$VERIFIER_PYTHON" lint docs-check contracts-check secret-scan
    python3 -m unittest discover -s scripts/quality -p test_prompt_immutability.py
    python3 -m unittest discover -s scripts/security -p test_secret_scan.py
    python3 scripts/quality/check_prompt_immutability.py --base-ref 66f00200be48f24f5f690ff32fc0bd376ad952e2
    python3 - <<'IMMUTABLE'
import subprocess
from pathlib import Path
base = "66f00200be48f24f5f690ff32fc0bd376ad952e2"
paths = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", base, "--", "packages/schemas", "services/api/src/main/resources/db/migration/platform", "packages/test-fixtures/verification"], text=True).splitlines()
for path in paths:
    previous = subprocess.check_output(["git", "show", f"{base}:{path}"])
    if not Path(path).is_file() or Path(path).read_bytes() != previous:
        raise SystemExit(f"Released artifact changed: {path}")
print("Released schemas, migrations and verification fixtures retain exact bytes.")
IMMUTABLE
    git diff --check
    git status --short
}
run_gate H quality_suite
verification_stage=integration_dependency
printf 'PASS\n' > "$verification_output/integration_dependency.status"
printf 'A-H and the source-backed calculus composite integration gates passed. Hosted CI and production release approval remain separate; rollout is disabled.\n'
