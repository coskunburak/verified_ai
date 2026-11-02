#!/usr/bin/env bash
# Owner execution only. Synthetic fixtures; no paid evaluation, deployment or Git mutation.
set -Eeuo pipefail
s66_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s66_root"
mkdir -p .generated/sprint-6-6-owner
s66_out="$(mktemp -d "$s66_root/.generated/sprint-6-6-owner/$(TZ=Europe/Istanbul date +%Y%m%d-%H%M%S)-XXXXXX")"
ln -sfn "$s66_out" "$s66_root/.generated/sprint-6-6-owner/latest"
exec > >(tee "$s66_out/acceptance.log") 2>&1
# Keep the terminal/log stream available when errexit fires inside a redirected gate.
exec 3>&1 4>&2
s66_stage=prerequisites
s66_failure=FAIL
s66_simulator=""
for gate in prerequisites A B C E D cleanup; do printf 'NOT_RUN\n' > "$s66_out/$gate.status"; done
printf 'UNKNOWN\n' > "$s66_out/head.txt"
printf 'UNKNOWN\n' > "$s66_out/branch.txt"

finish() {
    local result=$?
    trap - EXIT ERR INT TERM
    exec 1>&3 2>&4
    set +e
    if [[ "$result" -ne 0 ]]; then
        printf 'Gate %s %s. Diagnostic log: %s/%s.log\n' "$s66_stage" "$s66_failure" "$s66_out" "$s66_stage"
        printf '%s\n' "$s66_failure" > "$s66_out/$s66_stage.status"
        if [[ "$s66_stage" == prerequisites ]]; then
            for gate in A B C E D; do printf 'BLOCKED\n' > "$s66_out/$gate.status"; done
        fi
    fi
    if [[ -n "$s66_simulator" ]]; then
        xcrun simctl shutdown "$s66_simulator" >> "$s66_out/cleanup.log" 2>&1
        if xcrun simctl delete "$s66_simulator" >> "$s66_out/cleanup.log" 2>&1; then
            printf 'PASS\n' > "$s66_out/cleanup.status"
        else
            printf 'FAIL\n' > "$s66_out/cleanup.status"
            result=1
        fi
    else
        printf 'PASS\n' > "$s66_out/cleanup.status"
    fi
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$s66_out" "$result" <<'SUMMARY'
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
evaluation = evidence('synthetic-evaluation.json')
hints = evidence('hint-evaluation.json')
passed = int(sys.argv[2]) == 0 and all(gates.get(g) == 'PASS' for g in ('prerequisites','A','B','C','E','D','cleanup'))
passed = passed and ios.get('status') == 'PASS' and backend.get('status') == 'PASS' and evaluation.get('status') == 'PASS' and hints.get('status') == 'PASS'
status = 'PASS' if passed else ('BLOCKED' if 'BLOCKED' in gates.values() else 'FAIL')
summary = {
    'version': 'sprint-6-6-owner-v1', 'sprint': '6.6',
    'baselineSha': 'c9f1b5837ed1584dded8afeafe91fe07c1acf118',
    'branch': (out/'branch.txt').read_text().strip(), 'head': (out/'head.txt').read_text().strip(),
    'gates': gates, 'ios': ios, 'backend': backend, 'syntheticEvaluation': evaluation, 'hintEvaluation': hints,
    'missingMandatoryEvidence': {'ios': ios.get('missing', ['NOT_RUN']), 'backendSuites': backend.get('missing', ['NOT_RUN']), 'backendCases': backend.get('missingCases', ['NOT_RUN'])},
    'migration': {'version': 'V029', 'freshDatabase': backend.get('status')=='PASS' and 'ProgressiveHintIntegrationTest.freshV029PersistsThreeLevelsWithoutChangingLegacyMessages' in backend.get('caseNames',[]), 'v028Compatibility': backend.get('status')=='PASS' and 'ProgressiveHintIntegrationTest.v028ForwardMigrationPreservesLegacyHistory' in backend.get('caseNames',[]), 'sharedUpgrade': 'BLOCKED'},
    'failureCount': ios.get('failed',0) + backend.get('failed',0),
    'skipCount': ios.get('skipped',0) + backend.get('skipped',0),
    'failedGateCount': sum(v == 'FAIL' for v in gates.values()),
    'localAcceptance': 'LOCAL_ACCEPTANCE_PASSED' if passed else status,
    'overall': status, 'exitCode': 0 if passed else max(1,int(sys.argv[2])),
    'hostedCI': 'NOT_VERIFIED', 'paidModelEvaluation': 'NOT_RUN', 'liveAiEvaluation': 'NOT_EVALUATED',
    'providerStatus': 'TUTOR_DISABLED', 'securityPrivacy': 'LOCAL_SYNTHETIC_CHECKS_PASSED' if passed else 'NOT_VERIFIED',
    'aiEvaluationApplicability': 'Deterministic synthetic policy only; no live model or verifier calls. Expert pedagogical quality NOT_EVALUATED.',
    'productionRelease': 'BLOCKED', 'productionRollout': 'DISABLED',
    'limitations': [
        'Local tests do not approve hosted CI or production rollout.',
        'Owner-directed local Sprint 6.6 scheduling exception does not complete V6 commercial-wedge prerequisites.',
        'V024, V025 and V026 are reserved; V027/V028/V029 acceptance is limited to disposable PostgreSQL and synthetic forward-upgrade schemas; see migration evidence fields.',
        'Shared database migration ordering, hosted CI and production remain blocked.',
        'Live tutor generation, trusted step evidence, verified answer reveal and expert pedagogy remain unavailable.',
        'Manual VoiceOver, Dynamic Type and physical-device review remains pending.',
        'Only bounded two-step linear hints; explicit reveal unavailable without an authorized verified reference.'
    ]
}
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary,indent=2))
sys.exit(summary['exitCode'])
SUMMARY
        result=$?
    else
        printf '{"version":"sprint-6-6-owner-v1","sprint":"6.6","branch":null,"head":null,"overall":"BLOCKED","localAcceptance":"BLOCKED","exitCode":2,"gates":{"prerequisites":"BLOCKED"},"ios":{"status":"NOT_RUN","executed":0,"passed":0,"failed":0,"skipped":0},"backend":{"status":"NOT_RUN","executed":0,"passed":0,"failed":0,"skipped":0},"failureCount":0,"skipCount":0,"missingMandatoryEvidence":true,"migration":{"version":"V029","freshDatabase":false,"v028Compatibility":false,"sharedUpgrade":"BLOCKED"},"providerStatus":"TUTOR_DISABLED","syntheticEvaluation":{"status":"NOT_RUN"},"aiEvaluationApplicability":"Synthetic deterministic policy only","liveAiEvaluation":"NOT_EVALUATED","hostedCI":"NOT_VERIFIED","securityPrivacy":"NOT_VERIFIED","productionRelease":"BLOCKED","productionRollout":"DISABLED","limitations":["python3 missing; full evidence unavailable"]}\n' > "$s66_out/summary.json"
        result=2
    fi
    printf '\nEvidence: %s/summary.json\n' "$s66_out"
    exit "$result"
}
trap finish EXIT
trap 'printf "Failure at gate=%s line=%s\n" "$s66_stage" "$LINENO" >&2' ERR
trap 'exit 130' INT
trap 'exit 143' TERM
block() { s66_failure=BLOCKED; printf '%s\n' "$1" >&2; exit 2; }
run_gate() {
    s66_stage="$1"; shift
    printf 'Gate %s RUNNING. Log: %s/%s.log\n' "$s66_stage" "$s66_out" "$s66_stage"
    # Run in the parent shell: simulator identity must survive failure for EXIT cleanup.
    "$@" > "$s66_out/$s66_stage.log" 2>&1
    printf 'PASS\n' > "$s66_out/$s66_stage.status"
    printf 'Gate %s PASS\n' "$s66_stage"
}

# Interactive terminals may not inherit the bundled Codex tool directory.
# Preserve an existing rg; otherwise expose an installed executable to child gates too.
if ! command -v rg >/dev/null 2>&1; then
    for s66_rg_dir in \
        /Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path \
        /opt/homebrew/bin /usr/local/bin; do
        if [[ -x "$s66_rg_dir/rg" ]]; then
            export PATH="$s66_rg_dir:$PATH"
            break
        fi
    done
fi
s66_java_home="${JAVA21_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
if [[ -x "$s66_java_home/bin/java" ]]; then
    export JAVA_HOME="$s66_java_home"
    export PATH="$s66_java_home/bin:$PATH"
fi
if command -v git >/dev/null 2>&1; then
    git rev-parse HEAD > "$s66_out/head.txt"
    git branch --show-current > "$s66_out/branch.txt"
    git status --short > "$s66_out/working-tree.txt"
fi
for tool in git python3 java mvn docker xcrun xcodebuild rg; do
    command -v "$tool" >/dev/null 2>&1 || block "Required tool missing: $tool"
done
java -version > "$s66_out/java-version.log" 2>&1
java -XshowSettings:properties -version 2>&1 | rg 'java.specification.version = 21$' >/dev/null || block 'Java 21 is required on PATH.'
docker info > "$s66_out/docker-info.log" 2>&1 || block "Docker engine is unreachable. Start Docker Desktop and wait until its engine is running, then rerun this script. If Docker is already running, inspect $s66_out/docker-info.log for the connection error."
xcodebuild -version > "$s66_out/xcode-version.log" 2>&1 || block 'Full Xcode is required.'
xcrun simctl list --json > "$s66_out/simulators.json" || block 'Simulator inventory unavailable.'
s66_failure=BLOCKED
python3 - "$s66_out/simulators.json" > "$s66_out/simulator-selection.txt" <<'SIMULATOR'
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
read -r s66_runtime s66_device < "$s66_out/simulator-selection.txt"
s66_failure=FAIL
printf 'PASS\n' > "$s66_out/prerequisites.status"

# Do not inherit live inference activation. Never read provider secrets or .env files.
export TUTOR_SOCRATIC_ENABLED=false
export TUTOR_HINTS_ENABLED=false
export AI_GEMINI_ENABLED=false
export SOLVING_PRIMARY_ENABLED=false
export SOLVING_SECONDARY_ENABLED=false
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
    python3 scripts/quality/check_socratic_contracts.py
    python3 scripts/quality/check_hint_contracts.py
    # Bound the existing scanner away from all local credential stores, even if unignored.
    cat > "$s66_out/secret-scan.rg" <<'SCAN_SCOPE'
--glob=!**/.env*
--glob=!**/.aws/**
--glob=!**/.ssh/**
--glob=!**/.config/**
--glob=!**/.codex/**
--glob=!**/.claude/**
--glob=!**/.gemini/**
--glob=!**/.devcontainer/local-secrets/**
SCAN_SCOPE
    mkdir -p "$s66_out/secret-scan-temp"
    RIPGREP_CONFIG_PATH="$s66_out/secret-scan.rg" TMPDIR="$s66_out/secret-scan-temp" bash scripts/security/secret_scan.sh
    python3 -m unittest discover -s scripts/security -p test_secret_scan.py
    python3 scripts/quality/check_prompt_immutability.py --base-ref c9f1b5837ed1584dded8afeafe91fe07c1acf118
    python3 - <<'IMMUTABLE'
import subprocess
from pathlib import Path
# Inspected Sprint 6.6 implementation baseline, not an inherited Sprint 6.2 pin.
base='c9f1b5837ed1584dded8afeafe91fe07c1acf118'
roots=['packages/schemas','services/api/src/main/resources/db/migration/platform',
       'packages/test-fixtures/verification','evaluations/baselines','evaluations/golden-datasets/tutoring']
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
    s66_simulator="$(xcrun simctl create "VerifiedAI-Sprint66-$(basename "$s66_out")" "$s66_device" "$s66_runtime")"
    xcrun simctl boot "$s66_simulator"
    xcrun simctl bootstatus "$s66_simulator" -b
    local test_exit=0
    NSUnbufferedIO=YES xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
        -destination "platform=iOS Simulator,id=$s66_simulator" -destination-timeout 120 \
        -parallel-testing-enabled NO SWIFT_EMIT_LOC_STRINGS=NO \
        -derivedDataPath "$s66_out/DerivedData" -resultBundlePath "$s66_out/VerifiedAI.xcresult" \
        > "$s66_out/ios-tests.log" 2>&1 || test_exit=$?
    python3 - "$s66_out" "$test_exit" <<'IOS'
import json,re,sys
from pathlib import Path
out=Path(sys.argv[1]); log=(out/'ios-tests.log').read_text(errors='replace')
records={}
for m in re.finditer(r"Test Case '-\[(?P<target>[^.\s]+)\.(?P<suite>[^\s]+)\s+(?P<test>[^\]]+)\]' (?P<outcome>passed|failed|skipped)",log):
    key=m['suite']+'.'+m['test']
    # A later passing retry must not erase a failure/skip from this acceptance run.
    if records.get(key) not in ('failed','skipped'): records[key]=m['outcome']
paths=[
 'apps/ios/VerifiedAITests/Tutor/TutorTests.swift',
 'apps/ios/VerifiedAIUITests/TutorUITests.swift',
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
    local test_exit=0 backend_evidence_exit=0
    mkdir -p "$s66_out/migration"
    cp services/api/src/main/resources/db/migration/platform/V029__create_progressive_tutor_hints.sql "$s66_out/migration/"
    touch "$s66_out/backend-start"
    (cd services/api && mvn \
        -Dhint.evaluation.report="$s66_out/hint-evaluation.json" \
        -Dtutor.evaluation.report="$s66_out/synthetic-evaluation.json" \
        -Dtest=HintSourceTest,HintPolicyTest,HintEvaluationTest,ProgressiveHintIntegrationTest,SocraticPolicyTest,SocraticEvaluationTest,SocraticTurnIntegrationTest,PromptRegistryValidatorTest,ConfiguredAiRoutePlannerTest,ConfiguredAiModelGatewayTest,AiCapabilityRegistryTest,TutorPrivacyCapacityTest,TutorPolicyTest,TutorPersistenceIntegrationTest,TutorSessionControllerTest,ModularityTest,AccountPrivacyApplicationServiceTest,LearningProfileControllerTest,LearningProfileApplicationServiceTest,PrimarySolveControllerTest,PrimarySolveIntegrationTest,SolutionCandidateValidatorTest,CanonicalSolutionServiceTest,VerificationPolicyTest,VerificationArbitrationServiceTest,VerificationDecisionControllerTest,SolverReleaseRegistryTest,SolverReleaseEvidenceTest \
        test) > "$s66_out/backend-tests.log" 2>&1 || test_exit=$?
    python3 - "$s66_out" "$test_exit" <<'BACKEND' || backend_evidence_exit=$?
import json,re,shutil,sys,xml.etree.ElementTree as ET
from pathlib import Path
out=Path(sys.argv[1]); started=(out/'backend-start').stat().st_mtime_ns
required={'HintSourceTest','HintPolicyTest','HintEvaluationTest','ProgressiveHintIntegrationTest','SocraticPolicyTest','SocraticEvaluationTest','SocraticTurnIntegrationTest','PromptRegistryValidatorTest','ConfiguredAiRoutePlannerTest','ConfiguredAiModelGatewayTest','AiCapabilityRegistryTest','TutorPrivacyCapacityTest','TutorPolicyTest','TutorPersistenceIntegrationTest','TutorSessionControllerTest','ModularityTest','AccountPrivacyApplicationServiceTest','LearningProfileControllerTest','LearningProfileApplicationServiceTest','PrimarySolveControllerTest',
 'PrimarySolveIntegrationTest','SolutionCandidateValidatorTest','CanonicalSolutionServiceTest','VerificationPolicyTest',
 'VerificationArbitrationServiceTest','VerificationDecisionControllerTest','SolverReleaseRegistryTest','SolverReleaseEvidenceTest'}
seen={}; executed_cases=set(); counts={'passed':0,'failed':0,'skipped':0}
failures=[]; postgres_initializer_suites=set(); ryuk_unreachable=False
(out/'maven-reports').mkdir(exist_ok=True)
for path in Path('services/api/target/surefire-reports').glob('TEST-*.xml'):
    if path.stat().st_mtime_ns < started: continue
    root=ET.parse(path).getroot(); name=root.get('name','').split('.')[-1]
    shutil.copy2(path,out/'maven-reports'/path.name)
    cases=root.findall('testcase'); seen[name]=len(cases)
    for case in cases:
        executed_cases.add(name+'.'+case.get('name',''))
        outcome='failed' if case.find('failure') is not None or case.find('error') is not None else ('skipped' if case.find('skipped') is not None else 'passed')
        counts[outcome]+=1
        failure=case.find('failure')
        error=case.find('error')
        if failure is not None or error is not None:
            detail=error if error is not None else failure
            text=detail.text or ''
            initializer=error is not None and detail.get('type') in ('java.lang.ExceptionInInitializerError','java.lang.NoClassDefFoundError')
            failures.append((name,initializer))
            if initializer and 'com.verifiedai.integration.PostgresIntegrationTestSupport' in text:
                postgres_initializer_suites.add(name)
            if initializer and 'Could not connect to Ryuk at ' in text:
                ryuk_unreachable=True
missing=sorted(required-set(seen))
required_cases=set()
for source in Path('services/api/src/test/java/com/verifiedai/tutoring').rglob('*Test.java'):
    required_cases.update(source.stem+'.'+name for name in re.findall(r'@Test\s+void\s+(\w+)\s*\(',source.read_text()))
missing_cases=sorted(required_cases-executed_cases)
passed=int(sys.argv[2])==0 and not missing and all(seen.values()) and bool(required_cases) and not missing_cases and counts['failed']==counts['skipped']==0
# Classify only a proven shared-container bootstrap failure. Preserve every test error;
# an assertion failure, unrelated error or missing suite still makes the gate FAIL.
blocked=not passed and int(sys.argv[2])!=0 and not missing and not missing_cases and counts['skipped']==0 and ryuk_unreachable and bool(failures) and all(initializer and name in postgres_initializer_suites for name,initializer in failures)
summary={'status':'PASS' if passed else ('BLOCKED' if blocked else 'FAIL'),'suites':seen,'missing':missing,**counts,'mavenExit':int(sys.argv[2]),'executed':sum(seen.values()),'missingCases':missing_cases,'requiredCases':len(required_cases),'caseNames':sorted(executed_cases)}
summary['infrastructure']={
    'status':'BLOCKED' if blocked else ('NOT_VERIFIED' if ryuk_unreachable else 'NO_RECORDED_RYUK_FAILURE'),
    'reason':'TESTCONTAINERS_RYUK_UNREACHABLE' if ryuk_unreachable else None,
    'affectedInitializationErrors':sum(initializer and name in postgres_initializer_suites for name,initializer in failures),
    'recovery':'Restart Docker Desktop, wait until its engine is running, then rerun the same acceptance command. If it recurs, inspect backend-tests.log; the underlying port-forwarding/reset cause is not established.' if ryuk_unreachable else None
}
(out/'backend-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
if blocked:
    print('BLOCKED: Testcontainers could not connect to Ryuk; PostgreSQL-dependent tests did not initialize. Original errors and Maven exit are preserved.',file=sys.stderr)
    sys.exit(2)
if not passed: sys.exit('Backend acceptance failed, skipped, or lacks fresh reports; see backend-summary.json.')
BACKEND
    if [[ "$backend_evidence_exit" -eq 2 ]]; then
        block 'Testcontainers Ryuk is unreachable. Restart Docker Desktop, wait until the engine is running, and rerun this same command. See backend-tests.log and backend-summary.json. Ryuk and required suites remain enabled.'
    elif [[ "$backend_evidence_exit" -ne 0 ]]; then
        return "$backend_evidence_exit"
    fi
}
run_gate C gate_c

gate_e() {
    python3 - "$s66_out" <<'EVALUATION'
import hashlib,json,sys
from pathlib import Path
out=Path(sys.argv[1])
dataset=Path('evaluations/golden-datasets/tutoring/socratic-policy-v1.json')
source=json.loads(dataset.read_text())
report=json.loads((out/'synthetic-evaluation.json').read_text())
backend=json.loads((out/'backend-summary.json').read_text())
expected={case['id'] for case in source['cases']}
actual={case['id'] for case in report.get('cases',[])}
if report.get('status')!='PASS' or report.get('provenance')!='SYNTHETIC': raise SystemExit('Missing synthetic policy evidence')
if expected!=actual or report.get('executed')!=len(expected): raise SystemExit('Missing or duplicated synthetic cases')
if report.get('datasetSha256')!=hashlib.sha256(dataset.read_bytes()).hexdigest(): raise SystemExit('Evaluation dataset mismatch')
for evidence in source['integrationCoverage'].values():
    if evidence not in backend.get('caseNames',[]): raise SystemExit('Missing integration evidence: '+evidence)
for field in ('answerLeakageViolations','falseCorrectnessClaims','unverifiedCertaintyViolations'):
    if report.get(field)!=0: raise SystemExit('Unsafe synthetic evidence: '+field)
if report.get('pedagogicalProgressionQuality')!='NOT_EVALUATED': raise SystemExit('Synthetic results cannot establish teaching quality')
hint_dataset=Path('evaluations/golden-datasets/tutoring/progressive-hints-v1.json')
hint_source=json.loads(hint_dataset.read_text())
hint_report=json.loads((out/'hint-evaluation.json').read_text())
expected_hints={case['id'] for case in hint_source['cases']}
actual_hints={case['id'] for case in hint_report.get('cases',[])}
if hint_report.get('status')!='PASS' or hint_report.get('provenance')!='SYNTHETIC': raise SystemExit('Missing hint evaluation evidence')
if expected_hints!=actual_hints or hint_report.get('executed')!=len(expected_hints): raise SystemExit('Missing or duplicated hint cases')
if any(case.get('outcome')!='PASS' for case in hint_report.get('cases',[])): raise SystemExit('Failed hint case')
if hint_report.get('datasetSha256')!=hashlib.sha256(hint_dataset.read_bytes()).hexdigest(): raise SystemExit('Hint dataset mismatch')
if hint_report.get('answerLeakageViolations')!=0 or hint_report.get('inferenceCalls')!=0: raise SystemExit('Unsafe hint evidence')
if hint_report.get('liveModelQuality')!='NOT_EVALUATED': raise SystemExit('Synthetic hints cannot establish live quality')
for evidence in hint_source['integrationCoverage'].values():
    if evidence not in backend.get('caseNames',[]): raise SystemExit('Missing hint integration evidence: '+evidence)
print('Socratic and progressive hint structural evidence reconciled; live AI and expert pedagogy NOT_EVALUATED.')
EVALUATION
}
run_gate E gate_e

gate_d() {
    python3 - "$s66_out" <<'RECONCILE'
import json,sys
from pathlib import Path
out=Path(sys.argv[1])
for gate in ('prerequisites','A','B','C','E'):
    if (out/f'{gate}.status').read_text().strip()!='PASS': raise SystemExit(f'Missing gate: {gate}')
for name in ('ios-summary.json','backend-summary.json'):
    report=json.loads((out/name).read_text())
    if report['status']!='PASS' or report['failed'] or report['skipped']: raise SystemExit(f'Invalid evidence: {name}')
backend=json.loads((out/'backend-summary.json').read_text())
for case in ('ProgressiveHintIntegrationTest.freshV029PersistsThreeLevelsWithoutChangingLegacyMessages',
             'ProgressiveHintIntegrationTest.v028ForwardMigrationPreservesLegacyHistory'):
    if case not in backend.get('caseNames',[]): raise SystemExit('Missing V029 migration evidence: '+case)
if not (out/'VerifiedAI.xcresult').is_dir(): raise SystemExit('Missing Xcode result bundle')
print('Mandatory local evidence reconciled. Hosted CI and production remain separate gates.')
RECONCILE
}
run_gate D gate_d
