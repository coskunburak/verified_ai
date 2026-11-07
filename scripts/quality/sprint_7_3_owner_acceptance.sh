#!/usr/bin/env bash
# Owner-only Sprint 7.3 acceptance. Extends current-source 7.2 regressions with evaluation gates. Never executes production migrations, deployment or paid inference.
set -Eeuo pipefail
s73_mode=FULL
s73_selected=ABCDEFG
s73_failed_from=
if [[ "$#" -eq 1 && "$1" == --full ]]; then :
elif [[ "$#" -eq 2 && "$1" == --gate && "$2" =~ ^[BCDFG]$ ]]; then
    s73_mode=TARGETED
    s73_selected="A$2"
elif [[ "$#" -eq 2 && "$1" == --failed-from && -n "$2" ]]; then
    s73_mode=TARGETED
    s73_failed_from="$2"
else printf 'Use --full, --gate B|C|D|F|G, or --failed-from <Sprint 7.3 run directory>.\n' >&2; exit 2; fi
s73_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s73_root"
mkdir -p .generated/sprint-7-3-owner
s73_out="$(mktemp -d "$s73_root/.generated/sprint-7-3-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
exec > >(tee "$s73_out/acceptance.log") 2>&1
s73_exit=0
s73_python=python3
if [[ -x services/math-verifier/.venv/bin/python ]]; then s73_python="$s73_root/services/math-verifier/.venv/bin/python"; fi
if [[ -n "$s73_failed_from" ]]; then
    "$s73_python" scripts/quality/sprint_7_3_evidence.py repair-plan "$s73_out/repair-selection.json" "$s73_failed_from"
    s73_selected="$("$s73_python" -c 'import json,sys; print("".join(json.load(open(sys.argv[1]))["selectedGates"]))' "$s73_out/repair-selection.json")"
fi
ln -sfn "$s73_out" .generated/sprint-7-3-owner/latest
for gate in A B C D E F G cleanup; do printf 'NOT_RUN\n' > "$s73_out/$gate.status"; done
finish() {
    local incoming=$?
    trap - EXIT INT TERM
    set +e
    [[ "$incoming" -eq 0 ]] || s73_exit="$incoming"
    printf 'PASS\n' > "$s73_out/cleanup.status"
    if [[ -f "$s73_out/simulator-id.txt" ]]; then
        local simulator
        simulator="$(cat "$s73_out/simulator-id.txt")"
        xcrun simctl shutdown "$simulator" >> "$s73_out/cleanup.log" 2>&1
        xcrun simctl delete "$simulator" >> "$s73_out/cleanup.log" 2>&1 || printf 'FAIL\n' > "$s73_out/cleanup.status"
    fi
    "$s73_python" scripts/quality/sprint_7_3_evidence.py fingerprint "$s73_out/source-after.json"
    "$s73_python" scripts/quality/sprint_7_3_evidence.py summary "$s73_out" "$s73_exit"
    local code=$?
    if [[ ! -f "$s73_out/summary.json" ]]; then
        printf '{"sprint":"7.3","overall":"BLOCKED","missingMandatoryEvidence":["Evidence writer unavailable"],"productionAuthorization":"NOT_AUTHORIZED"}\n' > "$s73_out/summary.json"
        printf '{"missingMandatoryEvidence":["Evidence writer unavailable"]}\n' > "$s73_out/failure-inventory.json"
        printf '# Sprint 7.3\n\nBLOCKED: evidence writer unavailable.\n' > "$s73_out/summary.md"
        [[ -f "$s73_out/execution-metadata.json" ]] || printf '{"sprint":"7.3","mode":"UNKNOWN","status":"BLOCKED"}\n' > "$s73_out/execution-metadata.json"
        [[ -f "$s73_out/source-before.json" ]] || printf '{"status":"NOT_RUN","sha256":null}\n' > "$s73_out/source-before.json"
        for gate in A B C D E F G cleanup; do
            printf '{"gate":"%s","status":"%s","reason":"Evidence writer unavailable"}\n' "$gate" "$(cat "$s73_out/$gate.status")" > "$s73_out/gate-$gate.json"
        done
        code=2
    fi
    cat "$s73_out/summary.md"
    for gate in A B C D E F G; do
        if [[ -f "$s73_out/$gate.blocked" ]]; then
            printf 'Gate %s prerequisite: %s\n' "$gate" "$(cat "$s73_out/$gate.blocked")"
        fi
    done
    printf 'Evidence: %s/summary.json\n' "$s73_out"
    exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
run_gate() {
    local gate="$1"; shift
    [[ "$s73_selected" == *"$gate"* ]] || return 0
    printf 'Gate %s; log %s/%s.log\n' "$gate" "$s73_out" "$gate"
    set +e
    (trap - EXIT INT TERM; set -Eeuo pipefail; "$@") > "$s73_out/$gate.log" 2>&1
    local code=$?
    printf '%s\n' "$code" > "$s73_out/$gate.exit"
    set -e
    if [[ "$code" -eq 0 ]]; then printf 'PASS\n' > "$s73_out/$gate.status"
    elif [[ "$code" -eq 2 && -f "$s73_out/$gate.blocked" ]]; then printf 'BLOCKED\n' > "$s73_out/$gate.status"
    else printf 'FAIL\n' > "$s73_out/$gate.status"; s73_exit=1; fi
}
blocked() { printf '%s\n' "$2" > "$s73_out/$1.blocked"; exit 2; }
git rev-parse HEAD > "$s73_out/head.txt"
git branch --show-current > "$s73_out/branch.txt"
git status --short > "$s73_out/working-tree.txt"
"$s73_python" - "$s73_out" "$s73_mode" "$s73_selected" <<'META'
import json,platform,sys
from pathlib import Path
from datetime import datetime,timezone
out=Path(sys.argv[1])
value={'sprint':'7.3','mode':sys.argv[2],'selectedGates':list(sys.argv[3]),'startedAt':datetime.now(timezone.utc).isoformat(),
       'platform':platform.system(),'pythonVersion':platform.python_version(),'head':(out/'head.txt').read_text().strip(),
       'branch':(out/'branch.txt').read_text().strip(),'syntheticOnly':True,'paidInferenceEnabled':False,'previousEvidenceImported':False}
if (out/'repair-selection.json').is_file():
    value['repairSelection']=json.loads((out/'repair-selection.json').read_text())
(out/'execution-metadata.json').write_text(json.dumps(value,indent=2)+'\n')
META
"$s73_python" scripts/quality/sprint_7_3_evidence.py fingerprint "$s73_out/source-before.json"
s73_java="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
s73_path="$PATH"
if [[ -x "$s73_java/bin/java" ]]; then s73_path="$s73_java/bin:$s73_path"; fi
safe_run() {
    local -a test_environment=("PATH=$s73_path" "TMPDIR=${TMPDIR:-/tmp}" APP_ENV=test PYTHONDONTWRITEBYTECODE=1
        AI_GEMINI_ENABLED=false SOLVING_PRIMARY_ENABLED=false SOLVING_SECONDARY_ENABLED=false
        AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE
        AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE ATTEMPT_ANALYSIS_ENABLED=false ATTEMPT_VISION_ENABLED=false ATTEMPT_STRUCTURED_WORK_ENABLED=false
        ATTEMPT_EVALUATION_ENABLED=false ATTEMPT_PRODUCTION_AUTHORIZED=false MATH_VERIFIER_ATTEMPT_ENABLED=false MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false
        MATH_VERIFIER_VISUALIZATION_ENABLED=false MATH_VERIFIER_VISUALIZATION_PRODUCTION_AUTHORIZED=false
        MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me)
    if [[ -x "$s73_java/bin/java" ]]; then test_environment+=("JAVA_HOME=$s73_java"); fi
    env -i "${test_environment[@]}" "$@"
}
gate_a() {
    for tool in git python3; do command -v "$tool" >/dev/null 2>&1 || blocked A "Missing tool: $tool"; done
    git diff --check
    "$s73_python" - "$s73_out" <<'PRE'
import hashlib,json,sys
from pathlib import Path
out=Path(sys.argv[1]); root=Path('services/api/src/main/resources/db/migration/platform')
paths=sorted(root.glob('V*.sql')); versions=[int(p.name.split('__')[0][1:]) for p in paths]
assert len(versions)==len(set(versions)) and 33 in versions and 34 in versions and 35 in versions and not {24,25,26}&set(versions)
fixtures=Path('packages/schemas/fixtures')
for name in ('attempt-structured-work-request-v1','attempt-structured-work-v1','attempt-structured-work-golden','attempt-evaluation-request-v1','attempt-evaluation-v1','attempt-evaluation-view-v1','attempt-evaluation-golden'):
    assert json.loads((fixtures/(name+'.json')).read_text())
for path in ('services/api/src/main/java/com/verifiedai/attempt/application/AttemptWorkService.java',
             'services/api/src/main/java/com/verifiedai/attempt/domain/AttemptStructuredWork.java',
             'services/api/src/test/java/com/verifiedai/attempt/integration/AttemptWorkIntegrationTest.java',
             'scripts/quality/check_structured_work_contracts.py',
             'services/api/src/main/java/com/verifiedai/attempt/application/AttemptEvaluationService.java',
             'services/api/src/test/java/com/verifiedai/attempt/integration/AttemptEvaluationIntegrationTest.java',
             'scripts/quality/check_attempt_evaluation_contracts.py'):
    assert Path(path).is_file(), path
(out/'migration-inventory.json').write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths},indent=2)+'\n')
PRE
}
gate_b() {
    for tool in java mvn docker; do PATH="$s73_path" command -v "$tool" >/dev/null 2>&1 || blocked B "Missing tool: $tool"; done
    PATH="$s73_path" java -version > "$s73_out/java.log" 2>&1
    "$s73_python" - "$s73_out/java.log" <<'JAVA' || blocked B 'Java 21 is required.'
import re,sys
from pathlib import Path
sys.exit(0 if re.search(r'version "21[.\"]',Path(sys.argv[1]).read_text()) else 1)
JAVA
    docker info > "$s73_out/docker.log" 2>&1 || blocked B 'Docker/Testcontainers unavailable. Start Docker Desktop; do not disable Ryuk.'
    mkdir -p "$s73_out/maven-reports"
    printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$s73_out/maven-settings.xml"
    local tests code=0 evidence=0
    tests="$("$s73_python" -c 'import sys; sys.path.insert(0,"scripts/quality"); from sprint_7_3_evidence import BACKEND; print(",".join(BACKEND))')"
    if [[ -f "$s73_out/repair-selection.json" ]]; then
        local selected_tests
        selected_tests="$("$s73_python" -c 'import json,sys; print(",".join(json.load(open(sys.argv[1])).get("backendSelectors",[])))' "$s73_out/repair-selection.json")"
        if [[ -n "$selected_tests" ]]; then tests="$selected_tests"; fi
    fi
    printf '%s\n' "$tests" > "$s73_out/backend-selected-tests.txt"
    (cd services/api; safe_run mvn -s "$s73_out/maven-settings.xml" -gs "$s73_out/maven-settings.xml" -Dspring.profiles.active=test \
        -Dsurefire.reportsDirectory="$s73_out/maven-reports" "-Dtest=$tests" test) > "$s73_out/backend-tests.log" 2>&1 || code=$?
    "$s73_python" scripts/quality/sprint_7_3_evidence.py junit "$s73_out" backend || evidence=$?
    if [[ "$code" -ne 0 ]]; then tail -n 60 "$s73_out/backend-tests.log"; return "$code"; fi
    if [[ "$evidence" -eq 2 ]]; then blocked B 'Skipped PostgreSQL/backend tests require fresh evidence.'; fi
    return "$evidence"
}
gate_c() {
    "$s73_python" -c 'import jsonschema, yaml, openapi_spec_validator' || blocked C "Missing contract validation dependencies. Install the dev extra from services/math-verifier/pyproject.toml into $s73_python before rerunning; no automatic package installation."
    safe_run "$s73_python" scripts/quality/check_contracts.py
    safe_run "$s73_python" scripts/quality/check_attempt_contracts.py
    safe_run "$s73_python" scripts/quality/check_structured_work_contracts.py
    safe_run "$s73_python" scripts/quality/check_attempt_evaluation_contracts.py
    safe_run "$s73_python" scripts/quality/check_method_comparison_contracts.py
    safe_run "$s73_python" scripts/quality/check_socratic_contracts.py
    safe_run "$s73_python" scripts/quality/check_hint_contracts.py
}
gate_e() {
    local code=0
    "$s73_python" scripts/quality/sprint_7_3_evidence.py security "$s73_out" || code=$?
    if [[ "$code" -eq 2 ]]; then blocked E 'Fresh backend privacy/authorization/training/abuse evidence is unavailable.'; fi
    return "$code"
}
gate_f() {
    safe_run "$s73_python" scripts/quality/docs_check.py
    git diff --check
    "$s73_python" - <<'DOC'
from pathlib import Path
root=Path('docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence')
for name in ('IMPLEMENTATION_MAP','FUNCTIONAL_COMPLETION_MATRIX','EXECUTION_REPORT'):
    assert (root/('SPRINT_7.3_'+name+'.md')).is_file()
text=(root/'SPRINT_7.3_FUNCTIONAL_COMPLETION_MATRIX.md').read_text()
assert all('| '+identifier+' |' in text for identifier in ('A','B','C','D','E','F','G','H','I','J'))
DOC
}
gate_g() {
    "$s73_python" -c 'import pytest, fastapi, pydantic, sympy, httpx, jsonschema' || blocked G 'Verifier development dependencies unavailable.'
    mkdir -p "$s73_out/verifier-reports"
    local code=0 evidence=0
    (cd services/math-verifier; safe_run "$s73_python" -m pytest tests/test_attempt_verification_and_counterexample.py \
        tests/test_attempt_v2_product.py tests/test_method_comparison_v2.py tests/test_sprint_7_1_evidence.py tests/test_sprint_7_2_evidence.py tests/test_sprint_7_3_evidence.py tests/test_structured_work_golden.py tests/test_attempt_evaluation_golden.py \
        --junitxml="$s73_out/verifier-reports/verifier.xml") > "$s73_out/verifier-tests.log" 2>&1 || code=$?
    "$s73_python" scripts/quality/sprint_7_3_evidence.py junit "$s73_out" verifier || evidence=$?
    if [[ "$code" -ne 0 ]]; then tail -n 60 "$s73_out/verifier-tests.log"; return "$code"; fi
    if [[ "$evidence" -eq 2 ]]; then blocked G 'Unexpected skipped dependent regressions; inspect verifier-summary.json.'; fi
    return "$evidence"
}
gate_d() {
    for tool in xcrun xcodebuild; do command -v "$tool" >/dev/null 2>&1 || blocked D "Missing tool: $tool"; done
    xcodebuild -version > "$s73_out/xcode-version.log" 2>&1 || blocked D 'Full Xcode unavailable.'
    xcrun simctl list --json > "$s73_out/simulators.json" || blocked D 'Simulator inventory unavailable.'
    "$s73_python" - "$s73_out/simulators.json" > "$s73_out/simulator-selection.txt" <<'SIM' || blocked D 'Install an iOS 17+ iPhone simulator.'
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
    read -r runtime device < "$s73_out/simulator-selection.txt"
    simulator="$(xcrun simctl create "VerifiedAI-Sprint73-$(basename "$s73_out")" "$device" "$runtime")"
    printf '%s\n' "$simulator" > "$s73_out/simulator-id.txt"
    xcrun simctl boot "$simulator"
    xcrun simctl bootstatus "$simulator" -b
    local -a xcode_arguments=(test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI
        -destination "platform=iOS Simulator,id=$simulator" -destination-timeout 120
        -parallel-testing-enabled NO SWIFT_EMIT_LOC_STRINGS=NO CODE_SIGNING_ALLOWED=NO
        -derivedDataPath "$s73_out/DerivedData" -resultBundlePath "$s73_out/VerifiedAI.xcresult")
    if [[ -f "$s73_out/repair-selection.json" ]]; then
        "$s73_python" -c 'import json,sys; print("\n".join(json.load(open(sys.argv[1]))["iosSelectors"]))' "$s73_out/repair-selection.json" > "$s73_out/ios-selected-tests.txt"
        local selector
        while IFS= read -r selector; do
            [[ -n "$selector" ]] && xcode_arguments+=("-only-testing:$selector")
        done < "$s73_out/ios-selected-tests.txt"
    fi
    safe_run xcodebuild "${xcode_arguments[@]}" > "$s73_out/ios-tests.log" 2>&1 || code=$?
    local evidence=0
    "$s73_python" scripts/quality/sprint_7_3_evidence.py ios "$s73_out" "$code" || evidence=$?
    if [[ "$code" -ne 0 ]]; then tail -n 35 "$s73_out/ios-tests.log"; fi
    if [[ "$code" -ne 0 ]]; then return "$code"; fi
    return "$evidence"
}
run_gate A gate_a
run_gate B gate_b
run_gate C gate_c
run_gate D gate_d
run_gate E gate_e
run_gate F gate_f
run_gate G gate_g
exit "$s73_exit"
