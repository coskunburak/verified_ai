#!/usr/bin/env bash
# Owner-only execution. No provider activation, production DBs, Git changes or shared DB repair.
set -Eeuo pipefail
s68_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s68_root"
mkdir -p .generated/sprint-6-8-owner
# Subsequent invocations default to the previous run's failed test methods only.
# Capture the real previous directory before moving latest to this new run.
s68_previous=""
if [[ -d .generated/sprint-6-8-owner/latest ]]; then
    s68_previous="$(cd .generated/sprint-6-8-owner/latest && pwd -P)"
fi
s68_failed_only=false
if [[ "$#" -eq 1 && "${1:-}" == --full ]]; then s68_previous=""
elif [[ "$#" -eq 1 && "${1:-}" == --failed-only ]]; then
    s68_failed_only=true
    if [[ -z "$s68_previous" || ! -f "$s68_previous/summary.json" ]]; then
        printf 'Failed-only repair requires an existing owner summary; no full-suite fallback.\n' >&2
        exit 2
    fi
elif [[ "$#" -ne 0 ]]; then printf 'Unsupported argument\n' >&2; exit 2; fi
s68_out="$(mktemp -d "$s68_root/.generated/sprint-6-8-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
ln -sfn "$s68_out" "$s68_root/.generated/sprint-6-8-owner/latest"
exec > >(tee "$s68_out/acceptance.log") 2>&1
s68_exit=0
s68_simulator=""
s68_python=python3
if [[ -x services/math-verifier/.venv/bin/python ]]; then s68_python="$s68_root/services/math-verifier/.venv/bin/python"; fi
for s68_gate in prerequisites A B C D E F cleanup; do printf 'NOT_RUN\n' > "$s68_out/$s68_gate.status"; done
printf 'UNKNOWN\n' > "$s68_out/head.txt"
printf 'UNKNOWN\n' > "$s68_out/branch.txt"
finish() {
    local incoming=$?
    trap - EXIT INT TERM
    set +e
    if [[ "$s68_exit" -eq 0 && "$incoming" -ne 0 ]]; then s68_exit="$incoming"; fi
    printf 'PASS\n' > "$s68_out/cleanup.status"
    if [[ -f "$s68_out/simulator-id.txt" ]]; then
        s68_simulator="$(cat "$s68_out/simulator-id.txt")"
        xcrun simctl shutdown "$s68_simulator" >> "$s68_out/cleanup.log" 2>&1
        xcrun simctl delete "$s68_simulator" >> "$s68_out/cleanup.log" 2>&1
        if [[ "$?" -ne 0 ]]; then printf 'FAIL\n' > "$s68_out/cleanup.status"; [[ "$s68_exit" -ne 0 ]] || s68_exit=1; fi
    fi
    if command -v "$s68_python" >/dev/null 2>&1; then
        "$s68_python" scripts/quality/sprint_6_8_evidence.py fingerprint "$s68_out/source-after.json"
        "$s68_python" scripts/quality/sprint_6_8_evidence.py summary "$s68_out" "$s68_exit"
        s68_exit=$?
    else
        printf '{"sprint":"6.8","overall":"BLOCKED","exitCode":2,"actualHead":null,"sourceFingerprint":null,"gates":{"prerequisites":"BLOCKED"},"ios":{"status":"NOT_RUN"},"backend":{"status":"NOT_RUN"},"verifier":{"status":"NOT_RUN"},"goldenEvaluation":{"status":"NOT_RUN"},"migration":{"status":"BLOCKED"},"securityPrivacy":{"status":"NOT_VERIFIED"},"missingMandatoryEvidence":["python3 unavailable"],"unresolvedFailures":[],"hostedCI":"NOT_VERIFIED","liveAiEvaluation":"NOT_EVALUATED","productionRelease":"BLOCKED","limitations":["Current-source owner acceptance has not executed"]}\n' > "$s68_out/summary.json"
        printf '# Sprint 6.8 owner acceptance\n\nBLOCKED: Python prerequisite missing. No acceptance or release authorization.\n' > "$s68_out/summary.md"
        s68_exit=2
    fi
    printf 'Evidence: %s/summary.json\n' "$s68_out"
    exit "$s68_exit"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
run_gate() {
    local gate="$1"; shift
    printf 'Gate %s RUNNING; log %s/%s.log\n' "$gate" "$s68_out" "$gate"
    set +e
    (trap - EXIT INT TERM; set -Eeuo pipefail; "$@") > "$s68_out/$gate.log" 2>&1
    local code=$?
    set -e
    if [[ "$code" -eq 0 ]]; then printf 'PASS\n' > "$s68_out/$gate.status"
    elif [[ "$code" -eq 2 && -f "$s68_out/$gate.blocked" ]]; then printf 'BLOCKED\n' > "$s68_out/$gate.status"
    else printf 'FAIL\n' > "$s68_out/$gate.status"; [[ "$s68_exit" -ne 0 ]] || s68_exit="$code"; fi
    printf 'Gate %s %s\n' "$gate" "$(cat "$s68_out/$gate.status")"
}
blocked() { printf '%s\n' "$2"; printf '%s\n' "$2" > "$s68_out/$1.blocked"; exit 2; }
command -v git >/dev/null 2>&1 || exit 2
git rev-parse HEAD > "$s68_out/head.txt"
git branch --show-current > "$s68_out/branch.txt"
git status --short > "$s68_out/working-tree.txt"
command -v "$s68_python" >/dev/null 2>&1 || exit 2
"$s68_python" scripts/quality/sprint_6_8_evidence.py fingerprint "$s68_out/source-before.json"
# V030-V032 are forward-only source migrations. Actual disposable migration evidence is in C;
# this prerequisite status never authorizes a shared/production migration.
printf 'PASS\n' > "$s68_out/prerequisites.status"
printf 'Forward migration source V030-V032; runtime application and production authorization remain owner-owned.\n' > "$s68_out/prerequisites.log"
if [[ -n "$s68_previous" ]]; then
    s68_plan="$("$s68_python" scripts/quality/sprint_6_8_evidence.py select-mode "$s68_previous" "$s68_out")"
    if [[ "$s68_failed_only" == true ]]; then
        printf 'Explicit failed-only repair: prior passes stay historical; source changes require separate full acceptance.\n'
        "$s68_python" scripts/quality/sprint_6_8_evidence.py plan-retry "$s68_previous" "$s68_out"
    elif [[ "$s68_plan" == PRODUCT_CHANGE ]]; then
        printf 'Current-source acceptance required: canonical/domain, worker dispatch, forward migrations and shared iOS math rendering changed. Historical PASS evidence is retained.\n'
        s68_previous=""
    else
        "$s68_python" scripts/quality/sprint_6_8_evidence.py plan-retry "$s68_previous" "$s68_out"
    fi
fi
# Only synthetic settings enter test subprocesses; inherited provider flags/credentials are not forwarded.
s68_java="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
s68_path="$PATH"
if [[ -x "$s68_java/bin/java" ]]; then s68_path="$s68_java/bin:$s68_path"; fi
safe_run() {
    local -a test_environment=("PATH=$s68_path" "TMPDIR=${TMPDIR:-/tmp}" APP_ENV=test PYTHONDONTWRITEBYTECODE=1
        AI_GEMINI_ENABLED=false SOLVING_PRIMARY_ENABLED=false SOLVING_SECONDARY_ENABLED=false
        AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE
        AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE ATTEMPT_ANALYSIS_ENABLED=false ATTEMPT_VISION_ENABLED=false ATTEMPT_PRODUCTION_AUTHORIZED=false MATH_VERIFIER_ATTEMPT_ENABLED=false MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false
        MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me)
    if [[ -x "$s68_java/bin/java" ]]; then test_environment+=("JAVA_HOME=$s68_java"); fi
    env -i "${test_environment[@]}" "$@"
}
gate_a() {
    "$s68_python" -c 'import jsonschema, sympy, pydantic' || blocked A 'Install the declared Python development dependencies in services/math-verifier/.venv.'
    safe_run "$s68_python" scripts/quality/docs_check.py
    safe_run "$s68_python" scripts/quality/check_contracts.py
    safe_run "$s68_python" scripts/quality/check_socratic_contracts.py
    safe_run "$s68_python" scripts/quality/check_hint_contracts.py
    safe_run "$s68_python" scripts/quality/check_attempt_contracts.py
    safe_run "$s68_python" scripts/quality/check_method_comparison_contracts.py
    safe_run "$s68_python" scripts/quality/evaluate_attempt_golden.py "$s68_out/attempt-regression-golden.json"
    git diff --check
}
gate_b() {
    "$s68_python" -c 'import pytest, fastapi, pydantic, sympy, httpx, jsonschema' || blocked B 'Python verifier development dependencies unavailable.'
    mkdir -p "$s68_out/verifier-reports"
    cd services/math-verifier
    local code=0
    local -a selected_python=(tests)
    if [[ -n "$s68_previous" && ! -f "$s68_out/B.recovery" ]]; then
        selected_python=()
        local selected_case
        while IFS= read -r selected_case; do [[ -z "$selected_case" ]] || selected_python+=("$selected_case"); done < "$s68_out/verifier-selection.txt"
    fi
    if [[ "${#selected_python[@]}" -gt 0 ]]; then
    safe_run "$s68_python" -m pytest "${selected_python[@]}" --junitxml="$s68_out/verifier-reports/verifier.xml" > "$s68_out/verifier-tests.log" 2>&1 || code=$?
    fi
    cd "$s68_root"
    local evidence=0
    if [[ "${#selected_python[@]}" -gt 0 ]]; then
        "$s68_python" scripts/quality/sprint_6_8_evidence.py junit "$s68_out" verifier || evidence=$?
    fi
    local golden=0
    if [[ -z "$s68_previous" || -f "$s68_out/golden.selected" ]]; then
        safe_run "$s68_python" scripts/quality/evaluate_method_comparison.py "$s68_out/golden.json" || golden=$?
    fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    if [[ "$golden" -ne 0 ]]; then return "$golden"; fi
    if [[ "$evidence" -eq 2 ]]; then blocked B 'Unexpected skipped or missing verifier evidence. Inspect verifier-summary.json. Known Linux-only checks are reported separately.'; fi
    return "$evidence"
}
gate_c() {
    for tool in java mvn docker; do PATH="$s68_path" command -v "$tool" >/dev/null 2>&1 || blocked C "Missing tool: $tool"; done
    PATH="$s68_path" java -version > "$s68_out/java.log" 2>&1
    "$s68_python" - "$s68_out/java.log" <<'JAVA' || blocked C 'Java 21 is required.'
import re,sys
from pathlib import Path
sys.exit(0 if re.search(r'version "21[.\"]',Path(sys.argv[1]).read_text()) else 1)
JAVA
    docker info > "$s68_out/docker.log" 2>&1 || blocked C 'Docker/Testcontainers unavailable. Start Docker Desktop; do not disable Ryuk.'
    mkdir -p "$s68_out/maven-reports"
    # Never inherit credentials/mirrors from owner-managed Maven settings.
    printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$s68_out/maven-settings.xml"
    local code=0
    local tests=MethodComparisonEvidenceTest,AttemptAnalysisPolicyTest,StudentAttemptIntegrationTest,StudentAttemptControllerTest,CanonicalManualStepNormalizerTest,AttemptVerifierClientTest,ModularityTest,AccountPrivacyApplicationServiceTest,PrimarySolveIntegrationTest,VerificationPolicyTest,VerificationExecutionServiceTest,TutorSessionControllerTest,ProgressiveHintIntegrationTest,LearnerMasteryPolicyTest,AttemptMathParserV2Test
    if [[ -n "$s68_previous" && ! -f "$s68_out/C.recovery" ]]; then
        tests="$(cat "$s68_out/backend-selection.txt")"
        [[ -n "$tests" ]] || blocked C 'No failed backend test methods selected; refusing a full-suite fallback.'
    fi
    (cd services/api; safe_run mvn -s "$s68_out/maven-settings.xml" -gs "$s68_out/maven-settings.xml" -Dspring.profiles.active=test -Dsurefire.reportsDirectory="$s68_out/maven-reports" \
        "-Dtest=$tests" \
        test) > "$s68_out/backend-tests.log" 2>&1 || code=$?
    local evidence=0
    "$s68_python" scripts/quality/sprint_6_8_evidence.py junit "$s68_out" backend || evidence=$?
    cat "$s68_out/backend-summary.json"
    if [[ "$code" -ne 0 ]]; then tail -n 60 "$s68_out/backend-tests.log"; fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    if [[ "$evidence" -eq 2 ]]; then blocked C 'Skipped backend checks require owner evidence.'; fi
    return "$evidence"
}
gate_d() {
    for tool in xcrun xcodebuild; do command -v "$tool" >/dev/null 2>&1 || blocked D "Missing tool: $tool"; done
    xcodebuild -version > "$s68_out/xcode-version.log" 2>&1 || blocked D 'Full Xcode unavailable.'
    xcrun simctl list --json > "$s68_out/simulators.json" || blocked D 'Simulator inventory unavailable.'
    "$s68_python" - "$s68_out/simulators.json" > "$s68_out/simulator-selection.txt" <<'SIM' || blocked D 'Install an iOS 17+ iPhone simulator.'
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
    read -r runtime device < "$s68_out/simulator-selection.txt"
    simulator="$(xcrun simctl create "VerifiedAI-Sprint68-$(basename "$s68_out")" "$device" "$runtime")"
    printf '%s\n' "$simulator" > "$s68_out/simulator-id.txt"
    xcrun simctl boot "$simulator"
    xcrun simctl bootstatus "$simulator" -b
    local -a xcode_arguments=(test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI
        -destination "platform=iOS Simulator,id=$simulator" -destination-timeout 120
        -parallel-testing-enabled NO SWIFT_EMIT_LOC_STRINGS=NO CODE_SIGNING_ALLOWED=NO
        -derivedDataPath "$s68_out/DerivedData" -resultBundlePath "$s68_out/VerifiedAI.xcresult")
    if [[ -n "$s68_previous" && ! -f "$s68_out/D.recovery" ]]; then
        [[ -s "$s68_out/ios-selection.txt" ]] || blocked D 'No failed iOS tests selected; refusing a full-suite fallback.'
        local selected_test
        while IFS= read -r selected_test; do xcode_arguments+=("-only-testing:$selected_test"); done < "$s68_out/ios-selection.txt"
    fi
    safe_run xcodebuild "${xcode_arguments[@]}" > "$s68_out/ios-tests.log" 2>&1 || code=$?
    local evidence=0
    "$s68_python" - "$s68_out" "$code" <<'IOS' || evidence=$?
import json,re,sys
from pathlib import Path
out=Path(sys.argv[1]);text=(out/'ios-tests.log').read_text(errors='replace')
# Require actual passed XCTest cases, never infer success from build or test source presence.
required={'AttemptProductContractTests':3,'AttemptAnalysisTests':13,'AttemptAnalysisUITests':5,'MethodComparisonTests':6,'TutorTests':1,'TutorUITests':1,'PrimarySolveUITests':1}
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
    if [[ "$code" -ne 0 ]]; then tail -n 35 "$s68_out/ios-tests.log"; fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    return "$evidence"
}
gate_e() {
    "$s68_python" - "$s68_out" <<'RECONCILE'
import hashlib,json,sys
from pathlib import Path
out=Path(sys.argv[1]);path=Path('evaluations/golden-datasets/methods/rational-method-comparison-v2.json')
for name in ('backend','verifier','ios'):
    report=json.loads((out/(name+'-summary.json')).read_text())
    if report['status']!='PASS':raise SystemExit('Missing or failing mandatory evidence: '+name)
golden=json.loads((out/'golden.json').read_text())
assert golden['status']=='PASS' and golden['sha256']==hashlib.sha256(path.read_bytes()).hexdigest()
assert golden['schemaVersion']=='method-comparison-evaluation-v2' and golden['synthetic']
assert golden['caseCount']==len(json.loads(path.read_text())['cases'])
assert all(case['status']=='PASS' for case in golden['cases'])
print('Current-source synthetic results reconciled. Platform-specific validation and production authorization are separate gates.')
RECONCILE
}
if [[ -n "$s68_previous" ]]; then
    if [[ -f "$s68_out/A.selected" ]]; then run_gate A gate_a; fi
    if [[ -f "$s68_out/B.selected" ]]; then run_gate B gate_b; fi
    if [[ -f "$s68_out/C.selected" ]]; then run_gate C gate_c; fi
    if [[ -f "$s68_out/D.selected" ]]; then run_gate D gate_d; fi
else
    run_gate A gate_a
    run_gate B gate_b
    run_gate C gate_c
    run_gate D gate_d
fi
if [[ -z "$s68_previous" && "$(cat "$s68_out/B.status")" == PASS && "$(cat "$s68_out/C.status")" == PASS && "$(cat "$s68_out/D.status")" == PASS ]]; then
    run_gate E gate_e
else
    printf 'BLOCKED\n' > "$s68_out/E.status"
    printf 'Mandatory suite evidence unavailable; inspect B/C/D logs.\n' > "$s68_out/E.log"
fi
# EXIT reconciles F, fingerprints and cleanup; migration blockers keep full acceptance nonzero.
exit "$s68_exit"
