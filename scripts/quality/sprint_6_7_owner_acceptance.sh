#!/usr/bin/env bash
# Owner-only execution. No provider activation, production DBs, Git changes or shared DB repair.
set -Eeuo pipefail
s67_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s67_root"
mkdir -p .generated/sprint-6-7-owner
# Subsequent invocations default to the previous run's failed test methods only.
# Capture the real previous directory before moving latest to this new run.
s67_previous=""
if [[ -d .generated/sprint-6-7-owner/latest ]]; then
    s67_previous="$(cd .generated/sprint-6-7-owner/latest && pwd -P)"
fi
if [[ "${1:-}" == --full ]]; then s67_previous=""
elif [[ "$#" -ne 0 ]]; then printf 'Unsupported argument\n' >&2; exit 2; fi
s67_out="$(mktemp -d "$s67_root/.generated/sprint-6-7-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
ln -sfn "$s67_out" "$s67_root/.generated/sprint-6-7-owner/latest"
exec > >(tee "$s67_out/acceptance.log") 2>&1
s67_exit=0
s67_simulator=""
s67_python=python3
if [[ -x services/math-verifier/.venv/bin/python ]]; then s67_python="$s67_root/services/math-verifier/.venv/bin/python"; fi
for s67_gate in prerequisites A B C D E F cleanup; do printf 'NOT_RUN\n' > "$s67_out/$s67_gate.status"; done
printf 'UNKNOWN\n' > "$s67_out/head.txt"
printf 'UNKNOWN\n' > "$s67_out/branch.txt"
finish() {
    local incoming=$?
    trap - EXIT INT TERM
    set +e
    if [[ "$s67_exit" -eq 0 && "$incoming" -ne 0 ]]; then s67_exit="$incoming"; fi
    printf 'PASS\n' > "$s67_out/cleanup.status"
    if [[ -f "$s67_out/simulator-id.txt" ]]; then
        s67_simulator="$(cat "$s67_out/simulator-id.txt")"
        xcrun simctl shutdown "$s67_simulator" >> "$s67_out/cleanup.log" 2>&1
        xcrun simctl delete "$s67_simulator" >> "$s67_out/cleanup.log" 2>&1
        if [[ "$?" -ne 0 ]]; then printf 'FAIL\n' > "$s67_out/cleanup.status"; [[ "$s67_exit" -ne 0 ]] || s67_exit=1; fi
    fi
    if command -v "$s67_python" >/dev/null 2>&1; then
        "$s67_python" scripts/quality/sprint_6_7_evidence.py fingerprint "$s67_out/source-after.json"
        "$s67_python" scripts/quality/sprint_6_7_evidence.py summary "$s67_out" "$s67_exit"
        s67_exit=$?
    else
        printf '{"sprint":"6.7","overall":"BLOCKED","exitCode":2,"actualHead":null,"sourceFingerprint":null,"gates":{"prerequisites":"BLOCKED"},"ios":{"status":"NOT_RUN"},"backend":{"status":"NOT_RUN"},"verifier":{"status":"NOT_RUN"},"goldenEvaluation":{"status":"NOT_RUN"},"migration":{"status":"BLOCKED"},"securityPrivacy":{"status":"NOT_VERIFIED"},"missingMandatoryEvidence":["python3 unavailable"],"unresolvedFailures":[],"hostedCI":"NOT_VERIFIED","liveAiEvaluation":"NOT_EVALUATED","productionRelease":"BLOCKED","limitations":["Current-source owner acceptance has not executed"]}\n' > "$s67_out/summary.json"
        s67_exit=2
    fi
    printf 'Evidence: %s/summary.json\n' "$s67_out"
    exit "$s67_exit"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
run_gate() {
    local gate="$1"; shift
    printf 'Gate %s RUNNING; log %s/%s.log\n' "$gate" "$s67_out" "$gate"
    set +e
    (trap - EXIT INT TERM; set -Eeuo pipefail; "$@") > "$s67_out/$gate.log" 2>&1
    local code=$?
    set -e
    if [[ "$code" -eq 0 ]]; then printf 'PASS\n' > "$s67_out/$gate.status"
    elif [[ "$code" -eq 2 && -f "$s67_out/$gate.blocked" ]]; then printf 'BLOCKED\n' > "$s67_out/$gate.status"
    else printf 'FAIL\n' > "$s67_out/$gate.status"; [[ "$s67_exit" -ne 0 ]] || s67_exit="$code"; fi
    printf 'Gate %s %s\n' "$gate" "$(cat "$s67_out/$gate.status")"
}
blocked() { printf '%s\n' "$2"; printf '%s\n' "$2" > "$s67_out/$1.blocked"; exit 2; }
command -v git >/dev/null 2>&1 || exit 2
git rev-parse HEAD > "$s67_out/head.txt"
git branch --show-current > "$s67_out/branch.txt"
git status --short > "$s67_out/working-tree.txt"
command -v "$s67_python" >/dev/null 2>&1 || exit 2
"$s67_python" scripts/quality/sprint_6_7_evidence.py fingerprint "$s67_out/source-before.json"
# V030-V032 are forward-only source migrations. Actual disposable migration evidence is in C;
# this prerequisite status never authorizes a shared/production migration.
printf 'PASS\n' > "$s67_out/prerequisites.status"
printf 'Forward migration source V030-V032; runtime application and production authorization remain owner-owned.\n' > "$s67_out/prerequisites.log"
if [[ -n "$s67_previous" ]]; then
    s67_plan="$("$s67_python" scripts/quality/sprint_6_7_evidence.py select-mode "$s67_previous" "$s67_out")"
    if [[ "$s67_plan" == PRODUCT_CHANGE ]]; then
        printf 'Current-source acceptance required: canonical/domain, worker dispatch, forward migrations and shared iOS math rendering changed. Historical PASS evidence is retained.\n'
        s67_previous=""
    else
        "$s67_python" scripts/quality/sprint_6_7_evidence.py plan-retry "$s67_previous" "$s67_out"
    fi
fi
# Only synthetic settings enter test subprocesses; inherited provider flags/credentials are not forwarded.
s67_java="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
s67_path="$PATH"
if [[ -x "$s67_java/bin/java" ]]; then s67_path="$s67_java/bin:$s67_path"; fi
safe_run() {
    local -a test_environment=("PATH=$s67_path" "TMPDIR=${TMPDIR:-/tmp}" APP_ENV=test PYTHONDONTWRITEBYTECODE=1
        AI_GEMINI_ENABLED=false SOLVING_PRIMARY_ENABLED=false SOLVING_SECONDARY_ENABLED=false
        AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE
        AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE ATTEMPT_ANALYSIS_ENABLED=false ATTEMPT_VISION_ENABLED=false ATTEMPT_PRODUCTION_AUTHORIZED=false MATH_VERIFIER_ATTEMPT_ENABLED=false MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false
        MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me)
    if [[ -x "$s67_java/bin/java" ]]; then test_environment+=("JAVA_HOME=$s67_java"); fi
    env -i "${test_environment[@]}" "$@"
}
gate_a() {
    "$s67_python" -c 'import jsonschema' || blocked A 'Install the declared Python development dependencies in services/math-verifier/.venv.'
    safe_run "$s67_python" scripts/quality/docs_check.py
    safe_run "$s67_python" scripts/quality/check_contracts.py
    safe_run "$s67_python" scripts/quality/check_socratic_contracts.py
    safe_run "$s67_python" scripts/quality/check_hint_contracts.py
    safe_run "$s67_python" scripts/quality/check_attempt_contracts.py
    git diff --check
}
gate_b() {
    "$s67_python" -c 'import pytest, fastapi, pydantic, sympy, httpx, jsonschema' || blocked B 'Python verifier development dependencies unavailable.'
    mkdir -p "$s67_out/verifier-reports"
    cd services/math-verifier
    local code=0
    local -a selected_python=(tests)
    if [[ -n "$s67_previous" && ! -f "$s67_out/B.recovery" ]]; then
        selected_python=()
        local selected_case
        while IFS= read -r selected_case; do [[ -z "$selected_case" ]] || selected_python+=("$selected_case"); done < "$s67_out/verifier-selection.txt"
    fi
    if [[ "${#selected_python[@]}" -gt 0 ]]; then
    safe_run "$s67_python" -m pytest "${selected_python[@]}" --junitxml="$s67_out/verifier-reports/verifier.xml" > "$s67_out/verifier-tests.log" 2>&1 || code=$?
    fi
    cd "$s67_root"
    local evidence=0
    if [[ "${#selected_python[@]}" -gt 0 ]]; then
        "$s67_python" scripts/quality/sprint_6_7_evidence.py junit "$s67_out" verifier || evidence=$?
    fi
    local golden=0
    if [[ -z "$s67_previous" || -f "$s67_out/golden.selected" ]]; then
        safe_run "$s67_python" scripts/quality/evaluate_attempt_golden.py "$s67_out/golden.json" || golden=$?
    fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    if [[ "$golden" -ne 0 ]]; then return "$golden"; fi
    if [[ "$evidence" -eq 2 ]]; then blocked B 'Unexpected skipped or missing verifier evidence. Inspect verifier-summary.json. Known Linux-only checks are reported separately.'; fi
    return "$evidence"
}
gate_c() {
    for tool in java mvn docker; do PATH="$s67_path" command -v "$tool" >/dev/null 2>&1 || blocked C "Missing tool: $tool"; done
    PATH="$s67_path" java -version > "$s67_out/java.log" 2>&1
    "$s67_python" - "$s67_out/java.log" <<'JAVA' || blocked C 'Java 21 is required.'
import re,sys
from pathlib import Path
sys.exit(0 if re.search(r'version "21[.\"]',Path(sys.argv[1]).read_text()) else 1)
JAVA
    docker info > "$s67_out/docker.log" 2>&1 || blocked C 'Docker/Testcontainers unavailable. Start Docker Desktop; do not disable Ryuk.'
    mkdir -p "$s67_out/maven-reports"
    local code=0
    local tests=AttemptAnalysisPolicyTest,StudentAttemptIntegrationTest,StudentAttemptControllerTest,CanonicalManualStepNormalizerTest,AttemptVerifierClientTest,ModularityTest,AccountPrivacyApplicationServiceTest,PrimarySolveIntegrationTest,VerificationPolicyTest,VerificationExecutionServiceTest,TutorSessionControllerTest,ProgressiveHintIntegrationTest,LearnerMasteryPolicyTest,AttemptMathParserV2Test
    if [[ -n "$s67_previous" && ! -f "$s67_out/C.recovery" ]]; then
        tests="$(cat "$s67_out/backend-selection.txt")"
        [[ -n "$tests" ]] || blocked C 'No failed backend test methods selected; refusing a full-suite fallback.'
    fi
    (cd services/api; safe_run mvn -Dspring.profiles.active=test -Dsurefire.reportsDirectory="$s67_out/maven-reports" \
        "-Dtest=$tests" \
        test) > "$s67_out/backend-tests.log" 2>&1 || code=$?
    local evidence=0
    "$s67_python" scripts/quality/sprint_6_7_evidence.py junit "$s67_out" backend || evidence=$?
    cat "$s67_out/backend-summary.json"
    if [[ "$code" -ne 0 ]]; then tail -n 60 "$s67_out/backend-tests.log"; fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    return "$evidence"
}
gate_d() {
    for tool in xcrun xcodebuild; do command -v "$tool" >/dev/null 2>&1 || blocked D "Missing tool: $tool"; done
    xcodebuild -version > "$s67_out/xcode-version.log" 2>&1 || blocked D 'Full Xcode unavailable.'
    xcrun simctl list --json > "$s67_out/simulators.json" || blocked D 'Simulator inventory unavailable.'
    "$s67_python" - "$s67_out/simulators.json" > "$s67_out/simulator-selection.txt" <<'SIM' || blocked D 'Install an iOS 17+ iPhone simulator.'
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
    read -r runtime device < "$s67_out/simulator-selection.txt"
    simulator="$(xcrun simctl create "VerifiedAI-Sprint67-$(basename "$s67_out")" "$device" "$runtime")"
    printf '%s\n' "$simulator" > "$s67_out/simulator-id.txt"
    xcrun simctl boot "$simulator"
    xcrun simctl bootstatus "$simulator" -b
    local -a xcode_arguments=(test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI
        -destination "platform=iOS Simulator,id=$simulator" -destination-timeout 120
        -parallel-testing-enabled NO SWIFT_EMIT_LOC_STRINGS=NO
        -derivedDataPath "$s67_out/DerivedData" -resultBundlePath "$s67_out/VerifiedAI.xcresult")
    if [[ -n "$s67_previous" && ! -f "$s67_out/D.recovery" ]]; then
        [[ -s "$s67_out/ios-selection.txt" ]] || blocked D 'No failed iOS tests selected; refusing a full-suite fallback.'
        local selected_test
        while IFS= read -r selected_test; do xcode_arguments+=("-only-testing:$selected_test"); done < "$s67_out/ios-selection.txt"
    fi
    safe_run xcodebuild "${xcode_arguments[@]}" > "$s67_out/ios-tests.log" 2>&1 || code=$?
    local evidence=0
    "$s67_python" - "$s67_out" "$code" <<'IOS' || evidence=$?
import json,re,sys
from pathlib import Path
out=Path(sys.argv[1]);text=(out/'ios-tests.log').read_text(errors='replace')
# Require actual passed XCTest cases, never infer success from build or test source presence.
required={'AttemptProductContractTests':3,'AttemptAnalysisTests':13,'AttemptAnalysisUITests':2,'TutorTests':1,'TutorUITests':1,'PrimarySolveUITests':1}
passed=re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' passed",text)
failed=re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' failed",text)
counts={name:sum(c.split('.')[-1]==name for c,_ in passed) for name in required}
missing=[n for n,minimum in required.items() if counts[n]<minimum]
unexpected=[]
if (out/'retry-plan.json').exists() and not (out/'D.recovery').exists():
    expected={tuple(pair) for pair in json.loads((out/'retry-plan.json').read_text())['ios']}
    actual=set(passed)
    missing=sorted(expected-actual)
    unexpected=sorted((actual|set(failed))-expected)
skipped=bool(re.search(r"Test Case .* skipped",text))
status='PASS' if int(sys.argv[2])==0 and not failed and not missing and not unexpected and not skipped and (out/'VerifiedAI.xcresult').exists() else 'FAIL'
report={'status':status,'passedCases':passed,'failedCases':failed,'missing':missing,'unexpectedCases':unexpected,'skipped':skipped,'artifact':'VerifiedAI.xcresult',
    'scope':'FAILED_TESTS_ONLY' if (out/'retry-plan.json').exists() and not (out/'D.recovery').exists() else 'GATE_RECOVERY' if (out/'D.recovery').exists() else 'FULL'}
(out/'ios-summary.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2));sys.exit(0 if status=='PASS' else 1)
IOS
    if [[ "$code" -ne 0 ]]; then tail -n 35 "$s67_out/ios-tests.log"; fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    return "$evidence"
}
gate_e() {
    "$s67_python" - "$s67_out" <<'RECONCILE'
import hashlib,json,sys
from pathlib import Path
out=Path(sys.argv[1]);path=Path('evaluations/golden-datasets/attempts/manual-polynomial-v1.json')
for name in ('backend','verifier','ios'):
    report=json.loads((out/(name+'-summary.json')).read_text())
    if report['status']!='PASS':raise SystemExit('Missing or failing mandatory evidence: '+name)
golden=json.loads((out/'golden.json').read_text())
assert golden['status']=='PASS' and golden['sha256']==hashlib.sha256(path.read_bytes()).hexdigest()
assert golden['falseAccusationRate']==0 and golden['counterexamples']['published']==golden['counterexamples']['sound']
assert golden['firstWrongStepRecall']==1 and golden['supportedTaxonomyAccuracy']==1
assert golden['domainProtectionCases']['total']==golden['domainProtectionCases']['safe']
print('Current-source synthetic results reconciled. Platform-specific validation and production authorization are separate gates.')
RECONCILE
}
if [[ -n "$s67_previous" ]]; then
    if [[ -f "$s67_out/A.selected" ]]; then run_gate A gate_a; fi
    if [[ -f "$s67_out/B.selected" ]]; then run_gate B gate_b; fi
    if [[ -f "$s67_out/C.selected" ]]; then run_gate C gate_c; fi
    if [[ -f "$s67_out/D.selected" ]]; then run_gate D gate_d; fi
else
    run_gate A gate_a
    run_gate B gate_b
    run_gate C gate_c
    run_gate D gate_d
fi
if [[ -z "$s67_previous" && "$(cat "$s67_out/B.status")" == PASS && "$(cat "$s67_out/C.status")" == PASS && "$(cat "$s67_out/D.status")" == PASS ]]; then
    run_gate E gate_e
else
    printf 'BLOCKED\n' > "$s67_out/E.status"
    printf 'Mandatory suite evidence unavailable; inspect B/C/D logs.\n' > "$s67_out/E.log"
fi
# EXIT reconciles F, fingerprints and cleanup; migration blockers keep full acceptance nonzero.
exit "$s67_exit"
