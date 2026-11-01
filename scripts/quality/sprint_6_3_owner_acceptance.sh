#!/usr/bin/env bash
# Owner execution only. Synthetic fixtures; no paid evaluation, deployment or Git mutation.
set -Eeuo pipefail
s63_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s63_root"
mkdir -p .generated/sprint-6-3-owner
s63_out="$(mktemp -d "$s63_root/.generated/sprint-6-3-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
ln -sfn "$s63_out" "$s63_root/.generated/sprint-6-3-owner/latest"
exec > >(tee "$s63_out/acceptance.log") 2>&1
# Keep the terminal/log stream available when errexit fires inside a redirected gate.
exec 3>&1 4>&2
s63_stage=prerequisites
s63_failure=FAIL
s63_simulator=""
for gate in prerequisites A B C D cleanup; do printf 'NOT_RUN\n' > "$s63_out/$gate.status"; done
printf 'UNKNOWN\n' > "$s63_out/head.txt"
printf 'UNKNOWN\n' > "$s63_out/branch.txt"

finish() {
    local result=$?
    trap - EXIT ERR INT TERM
    exec 1>&3 2>&4
    set +e
    if [[ "$result" -ne 0 ]]; then
        printf 'Gate %s %s. Diagnostic log: %s/%s.log\n' "$s63_stage" "$s63_failure" "$s63_out" "$s63_stage"
        printf '%s\n' "$s63_failure" > "$s63_out/$s63_stage.status"
        if [[ "$s63_stage" == prerequisites ]]; then
            for gate in A B C D; do printf 'BLOCKED\n' > "$s63_out/$gate.status"; done
        fi
    fi
    if [[ -n "$s63_simulator" ]]; then
        xcrun simctl shutdown "$s63_simulator" >> "$s63_out/cleanup.log" 2>&1
        if xcrun simctl delete "$s63_simulator" >> "$s63_out/cleanup.log" 2>&1; then
            printf 'PASS\n' > "$s63_out/cleanup.status"
        else
            printf 'FAIL\n' > "$s63_out/cleanup.status"
            result=1
        fi
    else
        printf 'PASS\n' > "$s63_out/cleanup.status"
    fi
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$s63_out" "$result" <<'SUMMARY'
import json, sys
from pathlib import Path
out = Path(sys.argv[1])
gates = {p.stem: p.read_text().strip() for p in out.glob('*.status')}
def evidence(name):
    try:
        return json.loads((out / name).read_text())
    except (OSError, ValueError):
        return {'status': 'NOT_RUN', 'passed': 0, 'failed': 0, 'skipped': 0, 'missingEvidence': True}
ios, backend = evidence('ios-summary.json'), evidence('backend-summary.json')
passed = int(sys.argv[2]) == 0 and all(gates.get(g) == 'PASS' for g in ('prerequisites','A','B','C','D','cleanup'))
passed = passed and ios.get('status') == 'PASS' and backend.get('status') == 'PASS'
status = 'PASS' if passed else ('BLOCKED' if 'BLOCKED' in gates.values() else 'FAIL')
summary = {
    'version': 'sprint-6-3-owner-v1', 'sprint': '6.3',
    'branch': (out/'branch.txt').read_text().strip(), 'head': (out/'head.txt').read_text().strip(),
    'gates': gates, 'ios': ios, 'backend': backend,
    'failureCount': ios.get('failed',0) + backend.get('failed',0),
    'skipCount': ios.get('skipped',0) + backend.get('skipped',0),
    'failedGateCount': sum(v == 'FAIL' for v in gates.values()),
    'localAcceptance': 'LOCAL_ACCEPTANCE_PASSED' if passed else status,
    'overall': status, 'exitCode': 0 if passed else max(1,int(sys.argv[2])),
    'hostedCI': 'NOT_RUN', 'paidModelEvaluation': 'NOT_RUN', 'aiEvaluation': 'NOT_RUN',
    'aiEvaluationApplicability': 'No model, prompt, routing or schema behavior change; presentation-only.',
    'productionRelease': 'BLOCKED', 'productionRollout': 'DISABLED',
    'limitations': [
        'Local tests do not approve hosted CI or production rollout.',
        'Owner-directed local Sprint 6.3 scheduling exception does not complete V6 commercial-wedge prerequisites.',
        'Candidate v1 has no independently authored deep narratives or resolved restriction text.',
        'Beginner catalog covers expression/equation terminology only; owner pedagogical and EN/TR review remains required.',
        'Manual VoiceOver, Reduce Motion, small-screen/device and offline UX review remain NOT_RUN.',
        'Existing freshness rules forbid offline reopening of stale solution/verification evidence.'
    ]
}
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary,indent=2))
sys.exit(summary['exitCode'])
SUMMARY
        result=$?
    else
        printf '{"sprint":"6.3","branch":null,"head":null,"overall":"BLOCKED","localAcceptance":"BLOCKED","gates":{"prerequisites":"BLOCKED"},"ios":{"status":"NOT_RUN"},"backend":{"status":"NOT_RUN"},"failureCount":0,"skipCount":0,"hostedCI":"NOT_RUN","paidModelEvaluation":"NOT_RUN","productionRelease":"BLOCKED","limitations":["python3 missing; full evidence unavailable"]}\n' > "$s63_out/summary.json"
        result=2
    fi
    printf '\nEvidence: %s/summary.json\n' "$s63_out"
    exit "$result"
}
trap finish EXIT
trap 'printf "Failure at gate=%s line=%s\n" "$s63_stage" "$LINENO" >&2' ERR
trap 'exit 130' INT
trap 'exit 143' TERM
block() { s63_failure=BLOCKED; printf '%s\n' "$1" >&2; exit 2; }
run_gate() {
    s63_stage="$1"; shift
    printf 'Gate %s RUNNING. Log: %s/%s.log\n' "$s63_stage" "$s63_out" "$s63_stage"
    # Run in the parent shell: simulator identity must survive failure for EXIT cleanup.
    "$@" > "$s63_out/$s63_stage.log" 2>&1
    printf 'PASS\n' > "$s63_out/$s63_stage.status"
    printf 'Gate %s PASS\n' "$s63_stage"
}

# Interactive terminals may not inherit the bundled Codex tool directory.
# Preserve an existing rg; otherwise expose an installed executable to child gates too.
if ! command -v rg >/dev/null 2>&1; then
    for s63_rg_dir in \
        /Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path \
        /opt/homebrew/bin /usr/local/bin; do
        if [[ -x "$s63_rg_dir/rg" ]]; then
            export PATH="$s63_rg_dir:$PATH"
            break
        fi
    done
fi
s63_java_home="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
if [[ -x "$s63_java_home/bin/java" ]]; then
    export JAVA_HOME="$s63_java_home"
    export PATH="$s63_java_home/bin:$PATH"
fi
if command -v git >/dev/null 2>&1; then
    git rev-parse HEAD > "$s63_out/head.txt"
    git branch --show-current > "$s63_out/branch.txt"
    git status --short > "$s63_out/working-tree.txt"
fi
for tool in git python3 java mvn docker xcrun xcodebuild rg; do
    command -v "$tool" >/dev/null 2>&1 || block "Required tool missing: $tool"
done
java -version > "$s63_out/java-version.log" 2>&1
java -XshowSettings:properties -version 2>&1 | rg 'java.specification.version = 21$' >/dev/null || block 'Java 21 is required on PATH.'
docker info > "$s63_out/docker-info.log" 2>&1 || block "Docker engine is unreachable. Start Docker Desktop and wait until its engine is running, then rerun this script. If Docker is already running, inspect $s63_out/docker-info.log for the connection error."
xcodebuild -version > "$s63_out/xcode-version.log" 2>&1 || block 'Full Xcode is required.'
xcrun simctl list --json > "$s63_out/simulators.json" || block 'Simulator inventory unavailable.'
s63_failure=BLOCKED
python3 - "$s63_out/simulators.json" > "$s63_out/simulator-selection.txt" <<'SIMULATOR'
import json,sys
from pathlib import Path
inventory=json.loads(Path(sys.argv[1]).read_text())
choices=[]
for runtime in inventory.get('runtimes',[]):
    if not runtime.get('isAvailable') or not runtime.get('identifier','').startswith('com.apple.CoreSimulator.SimRuntime.iOS-'): continue
    version=tuple(int(v) for v in runtime['version'].split('.'))
    if version < (17,): continue
    for device in inventory.get('devices',{}).get(runtime['identifier'],[]):
        kind=device.get('deviceTypeIdentifier','')
        if device.get('isAvailable') and kind.startswith('com.apple.CoreSimulator.SimDeviceType.iPhone-'):
            choices.append((version,kind,runtime['identifier']))
if not choices: sys.exit('BLOCKED: install an iOS 17+ iPhone simulator in Xcode.')
_,kind,runtime=max(choices)
print(runtime,kind)
SIMULATOR
read -r s63_runtime s63_device < "$s63_out/simulator-selection.txt"
s63_failure=FAIL
printf 'PASS\n' > "$s63_out/prerequisites.status"

# Do not inherit live inference activation. Never read provider secrets or .env files.
export AI_GEMINI_ENABLED=false
export AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE
export AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE
export AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE
export AI_VISION_RECOGNITION_FALLBACK_PROVIDER=""
export AI_PROBLEM_PARSER_FALLBACK_PROVIDER=""
export AI_PROBLEM_CLASSIFIER_FALLBACK_PROVIDER=""
export VERIFICATION_ARBITRATION_ENABLED=false
export VERIFICATION_ARBITRATION_OWNER_ACCEPTED=false
export VERIFICATION_CALCULUS_ENABLED=false
export VERIFICATION_CALCULUS_OWNER_ACCEPTED=false
export VERIFICATION_EXECUTION_ENABLED=false
export VERIFICATION_NUMERIC_ENABLED=false
export CALCULUS_FIXTURE_ENABLED=false
export PYTHONDONTWRITEBYTECODE=1
unset QWEN_OWNER_RUN VERIFIEDAI_MATH_WIRE

gate_a() {
    python3 scripts/quality/docs_check.py
    python3 scripts/quality/check_contracts.py
    # Bound the existing scanner away from all local credential stores, even if unignored.
    cat > "$s63_out/secret-scan.rg" <<'SCAN_SCOPE'
--glob=!**/.env*
--glob=!**/.aws/**
--glob=!**/.ssh/**
--glob=!**/.config/**
--glob=!**/.codex/**
--glob=!**/.claude/**
--glob=!**/.gemini/**
--glob=!**/.devcontainer/local-secrets/**
SCAN_SCOPE
    mkdir -p "$s63_out/secret-scan-temp"
    RIPGREP_CONFIG_PATH="$s63_out/secret-scan.rg" TMPDIR="$s63_out/secret-scan-temp" bash scripts/security/secret_scan.sh
    python3 -m unittest discover -s scripts/security -p test_secret_scan.py
    python3 scripts/quality/check_prompt_immutability.py --base-ref 6017ec45e0cffeb90d8b5bdbd9395bb3d47e91f8
    python3 - <<'IMMUTABLE'
import subprocess
from pathlib import Path
# Inspected Sprint 6.3 implementation baseline, not an inherited Sprint 6.2 pin.
base='6017ec45e0cffeb90d8b5bdbd9395bb3d47e91f8'
roots=['packages/schemas','services/api/src/main/resources/db/migration/platform',
       'packages/test-fixtures/verification','evaluations/baselines']
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',base,'--',*roots],text=True).splitlines()
if not paths: raise SystemExit('Missing immutable baseline inventory')
for path in paths:
    previous=subprocess.check_output(['git','show',f'{base}:{path}'])
    if not Path(path).is_file() or Path(path).read_bytes()!=previous:
        raise SystemExit(f'Released artifact changed: {path}')
print('Released contracts, evidence fixtures, migrations and baselines retain exact bytes.')
IMMUTABLE
    git diff --check
}
run_gate A gate_a

gate_b() {
    s63_simulator="$(xcrun simctl create "VerifiedAI-Sprint63-$(basename "$s63_out")" "$s63_device" "$s63_runtime")"
    xcrun simctl boot "$s63_simulator"
    xcrun simctl bootstatus "$s63_simulator" -b
    local test_exit=0
    NSUnbufferedIO=YES xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
        -destination "platform=iOS Simulator,id=$s63_simulator" -destination-timeout 120 \
        -parallel-testing-enabled NO SWIFT_EMIT_LOC_STRINGS=NO \
        -derivedDataPath "$s63_out/DerivedData" -resultBundlePath "$s63_out/VerifiedAI.xcresult" \
        > "$s63_out/ios-tests.log" 2>&1 || test_exit=$?
    python3 - "$s63_out" "$test_exit" <<'IOS'
import json,re,sys
from pathlib import Path
out=Path(sys.argv[1]); log=(out/'ios-tests.log').read_text(errors='replace')
records={}
for m in re.finditer(r"Test Case '-\[(?P<target>[^.\s]+)\.(?P<suite>[^\s]+)\s+(?P<test>[^\]]+)\]' (?P<outcome>passed|failed|skipped)",log):
    key=m['suite']+'.'+m['test']
    # A later passing retry must not erase a failure/skip from this acceptance run.
    if records.get(key) not in ('failed','skipped'): records[key]=m['outcome']
paths=[
 'apps/ios/VerifiedAITests/ProblemSolving/ExplanationPresentationTests.swift',
 'apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveContractTests.swift',
 'apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveViewModelTests.swift',
 'apps/ios/VerifiedAITests/ProblemSolving/MathPresentationTests.swift',
 'apps/ios/VerifiedAITests/ProblemSolving/SolveRecoveryStoreTests.swift',
 'apps/ios/VerifiedAITests/Onboarding/OnboardingViewModelTests.swift',
 'apps/ios/VerifiedAIUITests/PrimarySolveUITests.swift']
required=set()
for path in paths:
    suite=None
    for line in Path(path).read_text().splitlines():
        m=re.search(r'class (\w+): XCTestCase',line)
        if m: suite=m[1]
        t=re.search(r'func (test\w+)\(',line)
        if t and suite: required.add(suite+'.'+t[1])
missing=sorted(required-set(records))
counts={outcome:sum(v==outcome for v in records.values()) for outcome in ('passed','failed','skipped')}
passed=int(sys.argv[2])==0 and bool(required) and not missing and counts['failed']==counts['skipped']==0
summary={'status':'PASS' if passed else 'FAIL','executed':len(records),**counts,
         'requiredCases':len(required),'missing':missing,'cases':records,'xcodeExit':int(sys.argv[2])}
(out/'ios-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
if not passed: sys.exit('iOS acceptance failed or required evidence missing; see ios-summary.json and ios-tests.log.')
IOS
}
run_gate B gate_b

gate_c() {
    local test_exit=0
    touch "$s63_out/backend-start"
    (cd services/api && mvn \
        -Dtest=LearningProfileControllerTest,LearningProfileApplicationServiceTest,PrimarySolveControllerTest,PrimarySolveIntegrationTest,SolutionCandidateValidatorTest,CanonicalSolutionServiceTest,VerificationPolicyTest,VerificationArbitrationServiceTest,VerificationDecisionControllerTest,SolverReleaseRegistryTest,SolverReleaseEvidenceTest \
        test) > "$s63_out/backend-tests.log" 2>&1 || test_exit=$?
    python3 - "$s63_out" "$test_exit" <<'BACKEND'
import json,shutil,sys,xml.etree.ElementTree as ET
from pathlib import Path
out=Path(sys.argv[1]); started=(out/'backend-start').stat().st_mtime_ns
required={'LearningProfileControllerTest','LearningProfileApplicationServiceTest','PrimarySolveControllerTest',
 'PrimarySolveIntegrationTest','SolutionCandidateValidatorTest','CanonicalSolutionServiceTest','VerificationPolicyTest',
 'VerificationArbitrationServiceTest','VerificationDecisionControllerTest','SolverReleaseRegistryTest','SolverReleaseEvidenceTest'}
seen={}; counts={'passed':0,'failed':0,'skipped':0}
(out/'maven-reports').mkdir(exist_ok=True)
for path in Path('services/api/target/surefire-reports').glob('TEST-*.xml'):
    if path.stat().st_mtime_ns < started: continue
    root=ET.parse(path).getroot(); name=root.get('name','').split('.')[-1]
    if name not in required: continue
    shutil.copy2(path,out/'maven-reports'/path.name)
    cases=root.findall('testcase'); seen[name]=len(cases)
    for case in cases:
        outcome='failed' if case.find('failure') is not None or case.find('error') is not None else ('skipped' if case.find('skipped') is not None else 'passed')
        counts[outcome]+=1
missing=sorted(required-set(seen))
passed=int(sys.argv[2])==0 and not missing and all(seen.values()) and counts['failed']==counts['skipped']==0
summary={'status':'PASS' if passed else 'FAIL','suites':seen,'missing':missing,**counts,'mavenExit':int(sys.argv[2])}
(out/'backend-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
if not passed: sys.exit('Backend acceptance failed, skipped, or lacks fresh reports; see backend-summary.json.')
BACKEND
}
run_gate C gate_c

gate_d() {
    python3 - "$s63_out" <<'RECONCILE'
import json,sys
from pathlib import Path
out=Path(sys.argv[1])
for gate in ('prerequisites','A','B','C'):
    if (out/f'{gate}.status').read_text().strip()!='PASS': raise SystemExit(f'Missing gate: {gate}')
for name in ('ios-summary.json','backend-summary.json'):
    report=json.loads((out/name).read_text())
    if report['status']!='PASS' or report['failed'] or report['skipped']: raise SystemExit(f'Invalid evidence: {name}')
if not (out/'VerifiedAI.xcresult').is_dir(): raise SystemExit('Missing Xcode result bundle')
print('Mandatory local evidence reconciled. Hosted CI and production remain separate gates.')
RECONCILE
}
run_gate D gate_d
