#!/usr/bin/env bash
# Owner-only, synthetic fixtures. Requires macOS, JDK 21, Maven, Docker, Xcode
# with an available iOS 17+ iPhone runtime, and the math-verifier dev dependencies.
set -Eeuo pipefail
if [[ $# != 1 || $1 != --full ]]; then
    echo 'Usage: bash scripts/quality/sprint_7_7_owner_acceptance.sh --full' >&2
    exit 2
fi
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
mkdir -p .generated/sprint-7-7-owner
out="$(mktemp -d "$root/.generated/sprint-7-7-owner/run-XXXXXX")"
simulator=''
stage=prerequisites
finish() {
    local code=$?
    trap - EXIT
    if [[ -n "$simulator" ]]; then
        xcrun simctl delete "$simulator" || { if [[ $code == 0 ]]; then code=1; fi; }
    fi
    if [[ $code != 0 ]]; then echo "FAILED ($code) at $stage. Evidence: $out" >&2
    else echo "Sprint 7.7 local owner suite passed. Evidence: $out"; fi
    exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
exec > >(tee "$out/acceptance.log") 2>&1
python=python3
if [[ -x "$root/services/math-verifier/.venv/bin/python" ]]; then python="$root/services/math-verifier/.venv/bin/python"; fi
export PYTHONDONTWRITEBYTECODE=1
for tool in "$python" mvn docker xcrun xcodebuild git; do
    command -v "$tool" >/dev/null || { echo "Required tool missing: $tool" >&2; exit 2; }
done
java_candidates=("${JAVA21_HOME:-}" "${JAVA_HOME:-}"
    /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
    /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)
if [[ -x /usr/libexec/java_home ]]; then
    if registered_jdk="$(/usr/libexec/java_home -F -v 21 2>/dev/null)"; then
        java_candidates+=("$registered_jdk")
    fi
fi
if path_java="$(type -P java)" && [[ "$path_java" != /usr/bin/java ]]; then
    path_jdk="$("$python" -c 'from pathlib import Path; import sys; print(Path(sys.argv[1]).resolve().parent.parent)' "$path_java")"
    java_candidates+=("$path_jdk")
fi
java_home=''
for candidate in "${java_candidates[@]}"; do
    [[ -n "$candidate" && -x "$candidate/bin/java" && -x "$candidate/bin/javac" ]] || continue
    if env -i PATH="$candidate/bin:$PATH" JAVA_HOME="$candidate" "$candidate/bin/java" -version > "$out/java.log" 2>&1 &&
        grep -Eq '^(openjdk|java) version "21([.+-]|")' "$out/java.log" &&
        env -i PATH="$candidate/bin:$PATH" JAVA_HOME="$candidate" "$candidate/bin/javac" -version > "$out/javac.log" 2>&1 &&
        grep -Eq '^javac 21([.+-]|$)' "$out/javac.log"; then
        java_home="$candidate"
        break
    fi
done
if [[ -z "$java_home" ]]; then
    echo 'JDK 21 not found. Set JAVA21_HOME to a JDK directory containing Java 21 and javac 21.' >&2
    exit 2
fi
export PATH="$java_home/bin:$PATH"
echo "Selected $(head -n 1 "$out/java.log")"
docker info >/dev/null
"$python" -c 'import pytest, jsonschema, yaml, openapi_spec_validator'
safe_run() {
    local -a settings=("PATH=$PATH" "TMPDIR=${TMPDIR:-/tmp}" PYTHONDONTWRITEBYTECODE=1
        APP_ENV=test AI_GEMINI_ENABLED=false SOLVING_PRIMARY_ENABLED=false SOLVING_SECONDARY_ENABLED=false
        AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE
        AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE ATTEMPT_PRODUCTION_AUTHORIZED=false
        MASTERY_DECAY_REASSESSMENT_ENABLED=false MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me)
    settings+=("JAVA_HOME=$java_home")
    env -i "${settings[@]}" "$@"
}
prior=scripts/quality/sprint_7_6_evidence.py
"$python" "$prior" fingerprint "$out/source-before.json"
git rev-parse HEAD > "$out/head.txt"
stage=contracts
for check in check_contracts check_attempt_contracts check_structured_work_contracts check_attempt_evaluation_contracts \
    check_attempt_mistake_contracts check_mistake_dna_contracts check_mastery_contracts; do
    safe_run "$python" "scripts/quality/$check.py"
done
stage=backend
mkdir -p "$out/maven-reports" "$out/verifier-reports"
printf '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>\n' > "$out/settings.xml"
tests=HalfLifeRetentionCalculatorTest,MasteryDecayReassessmentTest,MistakeRetentionResolutionTest
tests+=,EvidenceStratifiedMasteryPolicyTest,EvidenceStratifiedMasteryIntegrationTest,MasteryMigrationIntegrationTest
tests+=,MistakeDnaServiceTest,MistakeDisputeRollbackIntegrationTest,MasteryControllerTest,MistakeDnaControllerTest,ModularityTest
(cd services/api && safe_run mvn -s "$out/settings.xml" -gs "$out/settings.xml" -Dspring.profiles.active=test \
    "-Dsurefire.reportsDirectory=$out/maven-reports" "-Dtest=$tests" test) | tee "$out/backend.log"
"$python" - "$out" "$tests" <<'CASES'
import json, sys
from pathlib import Path
sys.path.insert(0, "scripts/quality")
from sprint_6_9_evidence import junit
out = Path(sys.argv[1])
summary = junit((out / "maven-reports").glob("*.xml"), sys.argv[2].split(","), kind="backend")
(out / "backend-summary.json").write_text(json.dumps(summary, indent=2))
if summary["status"] != "PASS":
    sys.exit("Backend failures, skipped cases or missing suites; see backend-summary.json")
executed = {case.removesuffix("()") for case in summary["caseNames"]}
required = {
    "HalfLifeRetentionCalculatorTest.halfLifeMeasuresElapsedUtcTimeWithoutMutatingMastery",
    "HalfLifeRetentionCalculatorTest.boundedHalfLifeAndAssistancePreserveTheV2RetrievalContract",
    "MasteryDecayReassessmentTest.oneVerifiedAnswerCanLoseRetentionWhileMasteryAndEvidenceIdentityStayFixed",
    "MistakeRetentionResolutionTest.sevenDayBoundaryRequiresIndependentSuccessOnSameHazardAndReplaysOnce",
    "MistakeRetentionResolutionTest.isolatedErrorOrInterruptedStreakCannotEstablishPending",
    "EvidenceStratifiedMasteryIntegrationTest.delayedTargetedCheckSurvivesReplayAndDisputeRetractsOnlyEffectiveResolution",
    "EvidenceStratifiedMasteryIntegrationTest.temporalReadsDoNotRepublishOrMoveProjectionTime",
}
if required - executed:
    sys.exit("Missing required backend cases: " + str(sorted(required - executed)))
CASES
stage=causal-evidence-regression
(cd services/math-verifier && safe_run "$python" -m pytest tests/test_attempt_mistake_classification.py --junitxml="$out/verifier-reports/verifier.xml")
"$python" - "$out/verifier-reports/verifier.xml" <<'VERIFIER'
import sys
from xml.etree import ElementTree
cases = list(ElementTree.parse(sys.argv[1]).iter("testcase"))
if not cases or any(case.find("skipped") is not None for case in cases):
    sys.exit("Missing or skipped causal evidence regression")
VERIFIER
stage=ios
xcrun simctl list -j > "$out/simulators.json"
"$python" - "$out/simulators.json" > "$out/device.txt" <<'SIM'
import json, sys
from pathlib import Path
inventory = json.loads(Path(sys.argv[1]).read_text())
choices = []
for runtime in inventory["runtimes"]:
    key = runtime["identifier"]
    if not runtime["isAvailable"] or ".iOS-" not in key:
        continue
    version = tuple(map(int, runtime["version"].split(".")))
    for device in inventory["devices"].get(key, []):
        kind = device.get("deviceTypeIdentifier", "")
        if device["isAvailable"] and version >= (17,) and ".iPhone-" in kind:
            choices.append((version, key, kind))
if not choices:
    sys.exit("Install an iOS 17+ iPhone simulator")
print(*max(choices)[1:])
SIM
read -r runtime device < "$out/device.txt"
simulator="$(xcrun simctl create "VerifiedAI-Sprint77-$(basename "$out")" "$device" "$runtime")"
xcrun simctl boot "$simulator"
xcrun simctl bootstatus "$simulator" -b
safe_run xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
    -only-testing:VerifiedAITests/MasteryPresentationTests -only-testing:VerifiedAITests/MistakeDnaTests \
    -only-testing:VerifiedAIUITests/AttemptAnalysisUITests/testMasteryEnglishEvidenceAccessibility \
    -only-testing:VerifiedAIUITests/AttemptAnalysisUITests/testMasteryTurkishEvidenceAccessibility \
    -destination "platform=iOS Simulator,id=$simulator" -parallel-testing-enabled NO \
    SWIFT_EMIT_LOC_STRINGS=NO CODE_SIGNING_ALLOWED=NO -derivedDataPath "$out/DerivedData" \
    -resultBundlePath "$out/VerifiedAI.xcresult" | tee "$out/ios-tests.log"
"$python" - "$out/ios-tests.log" <<'IOS'
import re, sys
from pathlib import Path
log = Path(sys.argv[1]).read_text()
passed = set(re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' passed", log))
required = {
    ("VerifiedAITests.MasteryPresentationTests", "testTemporalFixtureAndOlderResponse"),
    ("VerifiedAITests.MasteryPresentationTests", "testProblemReviewSkillsRecoverWithoutLosingParseAndResetOnAccountChange"),
    ("VerifiedAITests.MasteryPresentationTests", "testDisabledEmptyFailureRetryAndNoDuplicateRequests"),
    ("VerifiedAITests.MasteryPresentationTests", "testLoadingAndStaleResponseAfterSuspendAreFenced"),
    ("VerifiedAITests.MistakeDnaTests", "testLateDisputeCannotPublishAfterAccountSwitch"),
    ("VerifiedAIUITests.AttemptAnalysisUITests", "testMasteryEnglishEvidenceAccessibility"),
    ("VerifiedAIUITests.AttemptAnalysisUITests", "testMasteryTurkishEvidenceAccessibility"),
}
if required - passed or re.search(r"Test Case .* skipped", log):
    sys.exit("Skipped or missing iOS tests: " + str(sorted(required - passed)))
IOS
stage=documentation
safe_run "$python" scripts/quality/docs_check.py
git diff --check
stage=source-integrity
"$python" "$prior" fingerprint "$out/source-after.json"
cmp "$out/source-before.json" "$out/source-after.json"
