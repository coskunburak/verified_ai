#!/usr/bin/env bash
# Owner-only full acceptance; synthetic tests and disposable infrastructure only.
set -Eeuo pipefail
[[ "$#" -eq 1 && "$1" == --full ]] || { printf 'Use --full\n' >&2; exit 2; }
s69_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s69_root"
mkdir -p .generated/sprint-6-9-owner
s69_out="$(mktemp -d "$s69_root/.generated/sprint-6-9-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
ln -sfn "$s69_out" .generated/sprint-6-9-owner/latest
exec > >(tee "$s69_out/acceptance.log") 2>&1
s69_exit=0
s69_python=python3
if [[ -x services/math-verifier/.venv/bin/python ]]; then s69_python="$s69_root/services/math-verifier/.venv/bin/python"; fi
for gate in A B C D cleanup; do printf 'NOT_RUN\n' > "$s69_out/$gate.status"; done
printf 'UNKNOWN\n' > "$s69_out/head.txt"
finish() {
    local incoming=$?
    trap - EXIT INT TERM
    set +e
    [[ "$incoming" -eq 0 ]] || s69_exit="$incoming"
    printf 'PASS\n' > "$s69_out/cleanup.status"
    if [[ -f "$s69_out/simulator-id.txt" ]]; then
        local simulator
        simulator="$(cat "$s69_out/simulator-id.txt")"
        xcrun simctl shutdown "$simulator" >> "$s69_out/cleanup.log" 2>&1
        xcrun simctl delete "$simulator" >> "$s69_out/cleanup.log" 2>&1 || printf 'FAIL\n' > "$s69_out/cleanup.status"
    fi
    "$s69_python" scripts/quality/sprint_6_9_evidence.py fingerprint "$s69_out/source-after.json"
    "$s69_python" scripts/quality/sprint_6_9_evidence.py summary "$s69_out" "$s69_exit"
    local code=$?
    if [[ ! -f "$s69_out/summary.json" ]]; then
        printf '{"sprint":"6.9","overall":"BLOCKED","missingMandatoryEvidence":["Evidence writer unavailable"],"hostedCI":"NOT_VERIFIED","productionAuthorization":"NOT_VERIFIED"}\n' > "$s69_out/summary.json"
        printf '{"missing":["Evidence writer unavailable"],"failed":[],"skipped":[]}\n' > "$s69_out/failure-inventory.json"
        printf '# Sprint 6.9\n\nBLOCKED: evidence writer unavailable.\n' > "$s69_out/summary.md"
        code=2
    fi
    printf 'Evidence: %s/summary.json\n' "$s69_out"
    exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
run_gate() {
    local gate="$1"; shift
    printf 'Gate %s; log %s/%s.log\n' "$gate" "$s69_out" "$gate"
    set +e
    (trap - EXIT INT TERM; set -Eeuo pipefail; "$@") > "$s69_out/$gate.log" 2>&1
    local code=$?
    set -e
    if [[ "$code" -eq 0 ]]; then printf 'PASS\n' > "$s69_out/$gate.status"
    elif [[ "$code" -eq 2 && -f "$s69_out/$gate.blocked" ]]; then printf 'BLOCKED\n' > "$s69_out/$gate.status"
    else printf 'FAIL\n' > "$s69_out/$gate.status"; s69_exit=1; fi
}
blocked() { printf '%s\n' "$2" > "$s69_out/$1.blocked"; exit 2; }
git rev-parse HEAD > "$s69_out/head.txt"
git status --short > "$s69_out/working-tree.txt"
"$s69_python" scripts/quality/sprint_6_9_evidence.py fingerprint "$s69_out/source-before.json"
s69_java="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
s69_path="$PATH"
if [[ -x "$s69_java/bin/java" ]]; then s69_path="$s69_java/bin:$s69_path"; fi
safe_run() {
    local -a test_environment=("PATH=$s69_path" "TMPDIR=${TMPDIR:-/tmp}" APP_ENV=test PYTHONDONTWRITEBYTECODE=1
        AI_GEMINI_ENABLED=false SOLVING_PRIMARY_ENABLED=false SOLVING_SECONDARY_ENABLED=false
        AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE
        AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE ATTEMPT_ANALYSIS_ENABLED=false ATTEMPT_VISION_ENABLED=false
        ATTEMPT_PRODUCTION_AUTHORIZED=false MATH_VERIFIER_ATTEMPT_ENABLED=false MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false
        MATH_VERIFIER_VISUALIZATION_ENABLED=false MATH_VERIFIER_VISUALIZATION_PRODUCTION_AUTHORIZED=false
        MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me)
    if [[ -x "$s69_java/bin/java" ]]; then test_environment+=("JAVA_HOME=$s69_java"); fi
    env -i "${test_environment[@]}" "$@"
}
gate_a() {
    "$s69_python" -c 'import jsonschema, sympy, pydantic' || blocked A 'Install the declared Python development dependencies in services/math-verifier/.venv.'
    safe_run "$s69_python" scripts/quality/docs_check.py
    safe_run "$s69_python" scripts/quality/check_contracts.py
    safe_run "$s69_python" scripts/quality/check_socratic_contracts.py
    safe_run "$s69_python" scripts/quality/check_hint_contracts.py
    safe_run "$s69_python" scripts/quality/check_attempt_contracts.py
    safe_run "$s69_python" scripts/quality/check_method_comparison_contracts.py
    safe_run "$s69_python" scripts/quality/check_math_visualization_contracts.py
    safe_run "$s69_python" scripts/quality/evaluate_attempt_golden.py "$s69_out/attempt-regression-golden.json"
    git diff --check
}
gate_b() {
    "$s69_python" -c 'import pytest, fastapi, pydantic, sympy, httpx, jsonschema' || blocked B 'Python verifier development dependencies unavailable.'
    mkdir -p "$s69_out/verifier-reports"
    cd services/math-verifier
    local code=0
    local -a selected_python=(tests)
    if [[ "${#selected_python[@]}" -gt 0 ]]; then
    safe_run "$s69_python" -m pytest "${selected_python[@]}" --junitxml="$s69_out/verifier-reports/verifier.xml" > "$s69_out/verifier-tests.log" 2>&1 || code=$?
    fi
    cd "$s69_root"
    local evidence=0
    if [[ "${#selected_python[@]}" -gt 0 ]]; then
        "$s69_python" scripts/quality/sprint_6_9_evidence.py junit "$s69_out" verifier || evidence=$?
    fi
    local golden=0
    if true; then
        safe_run "$s69_python" scripts/quality/evaluate_method_comparison.py "$s69_out/method-regression-golden.json" || golden=$?
        safe_run "$s69_python" scripts/quality/evaluate_math_visualization.py "$s69_out/golden.json" || golden=$?
    fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    if [[ "$golden" -ne 0 ]]; then return "$golden"; fi
    if [[ "$evidence" -eq 2 ]]; then blocked B 'Unexpected skipped or missing verifier evidence. Inspect verifier-summary.json. Known Linux-only checks are reported separately.'; fi
    return "$evidence"
}
gate_c() {
    for tool in java mvn docker; do PATH="$s69_path" command -v "$tool" >/dev/null 2>&1 || blocked C "Missing tool: $tool"; done
    PATH="$s69_path" java -version > "$s69_out/java.log" 2>&1
    "$s69_python" - "$s69_out/java.log" <<'JAVA' || blocked C 'Java 21 is required.'
import re,sys
from pathlib import Path
sys.exit(0 if re.search(r'version "21[.\"]',Path(sys.argv[1]).read_text()) else 1)
JAVA
    docker info > "$s69_out/docker.log" 2>&1 || blocked C 'Docker/Testcontainers unavailable. Start Docker Desktop; do not disable Ryuk.'
    mkdir -p "$s69_out/maven-reports"
    # Never inherit credentials/mirrors from owner-managed Maven settings.
    printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$s69_out/maven-settings.xml"
    local code=0
    local tests=VisualizationContractTest,VisualizationVerifierTest,VisualizationControllerTest,VisualizationIntegrationTest,MethodComparisonEvidenceTest,AttemptAnalysisPolicyTest,StudentAttemptIntegrationTest,StudentAttemptControllerTest,CanonicalManualStepNormalizerTest,AttemptVerifierClientTest,ModularityTest,AccountPrivacyApplicationServiceTest,PrimarySolveIntegrationTest,VerificationPolicyTest,VerificationExecutionServiceTest,TutorSessionControllerTest,ProgressiveHintIntegrationTest,LearnerMasteryPolicyTest,AttemptMathParserV2Test
    (cd services/api; safe_run mvn -s "$s69_out/maven-settings.xml" -gs "$s69_out/maven-settings.xml" -Dspring.profiles.active=test -Dsurefire.reportsDirectory="$s69_out/maven-reports" \
        "-Dtest=$tests" \
        test) > "$s69_out/backend-tests.log" 2>&1 || code=$?
    local evidence=0
    "$s69_python" scripts/quality/sprint_6_9_evidence.py junit "$s69_out" backend || evidence=$?
    cat "$s69_out/backend-summary.json"
    if [[ "$code" -ne 0 ]]; then tail -n 60 "$s69_out/backend-tests.log"; fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    if [[ "$evidence" -eq 2 ]]; then blocked C 'Skipped backend checks require owner evidence.'; fi
    return "$evidence"
}
gate_d() {
    for tool in xcrun xcodebuild; do command -v "$tool" >/dev/null 2>&1 || blocked D "Missing tool: $tool"; done
    xcodebuild -version > "$s69_out/xcode-version.log" 2>&1 || blocked D 'Full Xcode unavailable.'
    xcrun simctl list --json > "$s69_out/simulators.json" || blocked D 'Simulator inventory unavailable.'
    "$s69_python" - "$s69_out/simulators.json" > "$s69_out/simulator-selection.txt" <<'SIM' || blocked D 'Install an iOS 17+ iPhone simulator.'
import json,sys
from pathlib import Path
v=json.loads(Path(sys.argv[1]).read_text()); choices=[]
for runtime in v.get('runtimes',[]):
    if not runtime.get('isAvailable') or not runtime['identifier'].startswith('com.apple.CoreSimulator.SimRuntime.iOS-'):continue
    version=tuple(map(int,runtime['version'].split('.')))
    if version<(17,):continue
    for d in v.get('devices',{}).get(runtime['identifier'],[]):
        kind=d.get('deviceTypeIdentifier','')
        if d.get('isAvailable') and kind.startswith('com.apple.CoreSimulator.SimDeviceType.iPhone-'):choices.append((version,runtime['identifier'],kind))
if not choices:sys.exit(2)
_,runtime,kind=max(choices);print(runtime,kind)
SIM
    local runtime device simulator code=0
    read -r runtime device < "$s69_out/simulator-selection.txt"
    simulator="$(xcrun simctl create "VerifiedAI-Sprint69-$(basename "$s69_out")" "$device" "$runtime")"
    printf '%s\n' "$simulator" > "$s69_out/simulator-id.txt"
    xcrun simctl boot "$simulator"
    xcrun simctl bootstatus "$simulator" -b
    local -a xcode_arguments=(test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI
        -destination "platform=iOS Simulator,id=$simulator" -destination-timeout 120
        -parallel-testing-enabled NO SWIFT_EMIT_LOC_STRINGS=NO CODE_SIGNING_ALLOWED=NO
        -derivedDataPath "$s69_out/DerivedData" -resultBundlePath "$s69_out/VerifiedAI.xcresult")
    safe_run xcodebuild "${xcode_arguments[@]}" > "$s69_out/ios-tests.log" 2>&1 || code=$?
    local evidence=0
    "$s69_python" - "$s69_out" "$code" <<'IOS' || evidence=$?
import json,re,sys
from pathlib import Path
out=Path(sys.argv[1]);text=(out/'ios-tests.log').read_text(errors='replace')
# Require actual passed XCTest cases, never infer success from build or test source presence.
required={'MathVisualizationTests':8,'MathVisualizationUITests':3,'AttemptProductContractTests':3,'AttemptAnalysisTests':13,'AttemptAnalysisUITests':5,'MethodComparisonTests':6,'TutorTests':1,'TutorUITests':1,'PrimarySolveUITests':1}
passed=re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' passed",text)
failed=re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' failed",text)
counts={name:sum(c.split('.')[-1]==name for c,_ in passed) for name in required}
missing=[n for n,minimum in required.items() if counts[n]<minimum]
unexpected=[]
skipped=bool(re.search(r"Test Case .* skipped",text))
status='PASS' if int(sys.argv[2])==0 and not failed and not missing and not unexpected and not skipped and (out/'VerifiedAI.xcresult').exists() else 'FAIL'
report={'status':status,'passedCases':passed,'failedCases':failed,'missing':missing,'unexpectedCases':unexpected,'skipped':skipped,'artifact':'VerifiedAI.xcresult',
    'scope':'FULL'}
(out/'ios-summary.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2));sys.exit(0 if status=='PASS' else 1)
IOS
    if [[ "$code" -ne 0 ]]; then tail -n 35 "$s69_out/ios-tests.log"; fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    return "$evidence"
}
run_gate A gate_a
run_gate B gate_b
run_gate C gate_c
run_gate D gate_d
exit "$s69_exit"
