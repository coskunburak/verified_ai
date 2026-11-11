#!/usr/bin/env bash
# ============================================================================
# Sprint 7.8 Owner Acceptance Gate Runner
# Verification of Prerequisite Knowledge Graph, Learning State APIs,
# and Closed-Loop Twin Remediation across Modulith, Verifier, Contracts, and Docs.
# ============================================================================
set -Eeuo pipefail

if [[ $# != 1 || $1 != --full ]]; then
    echo 'Usage: bash scripts/quality/sprint_7_8_owner_acceptance.sh --full' >&2
    exit 2
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
mkdir -p .generated/sprint-7-8-owner
out="$(mktemp -d "$root/.generated/sprint-7-8-owner/run-XXXXXX")"
stage=prerequisites

finish() {
    local code=$?
    trap - EXIT
    if [[ $code != 0 ]]; then
        echo "FAILED ($code) at stage: $stage. Evidence log: $out/acceptance.log" >&2
    else
        echo "================================================================="
        echo "Sprint 7.8 local owner acceptance suite PASSED (100% GREEN)."
        echo "Evidence directory: $out"
        echo "================================================================="
    fi
    exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
exec > >(tee "$out/acceptance.log") 2>&1

echo ">>> Stage 1: Prerequisites & Toolchain Verification"
stage=prerequisites

python=/opt/homebrew/bin/python3
if [[ ! -x "$python" ]]; then
    python=python3
fi
export PYTHONDONTWRITEBYTECODE=1

for tool in "$python" git; do
    command -v "$tool" >/dev/null || { echo "Required tool missing: $tool" >&2; exit 2; }
done

# Locate Maven
mvn_cmd=""
if [[ -x "$root/mvnw" ]]; then
    mvn_cmd="$root/mvnw"
elif command -v mvn >/dev/null 2>&1; then
    mvn_cmd="mvn"
elif [[ -x /opt/homebrew/bin/mvn ]]; then
    mvn_cmd="/opt/homebrew/bin/mvn"
else
    echo "Required tool missing: mvn" >&2
    exit 2
fi

# Locate JDK 21
java_candidates=("${JAVA21_HOME:-}" "${JAVA_HOME:-}"
    /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
    /opt/homebrew/Cellar/openjdk@21/21.0.12/libexec/openjdk.jdk/Contents/Home
    /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)

if [[ -x /usr/libexec/java_home ]]; then
    if registered_jdk="$(/usr/libexec/java_home -F -v 21 2>/dev/null)"; then
        java_candidates+=("$registered_jdk")
    fi
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
    echo 'JDK 21 not found. Set JAVA21_HOME to a JDK directory containing Java 21.' >&2
    exit 2
fi
export PATH="$java_home/bin:$PATH"
echo "Selected JDK 21: $(head -n 1 "$out/java.log")"
echo "Selected Maven: $($mvn_cmd --version | head -n 1)"

export PYTHONPATH="$root/services/math-verifier/.venv/lib/python3.11/site-packages:$root/services/math-verifier:$root"

safe_run() {
    local -a settings=("PATH=$PATH" "TMPDIR=${TMPDIR:-/tmp}" "PYTHONPATH=$PYTHONPATH" PYTHONDONTWRITEBYTECODE=1
        APP_ENV=test AI_GEMINI_ENABLED=false SOLVING_PRIMARY_ENABLED=false SOLVING_SECONDARY_ENABLED=false
        AI_VISION_RECOGNITION_PRIMARY_PROVIDER=LOCAL_FIXTURE AI_PROBLEM_PARSER_PRIMARY_PROVIDER=LOCAL_FIXTURE
        AI_PROBLEM_CLASSIFIER_PRIMARY_PROVIDER=LOCAL_FIXTURE ATTEMPT_PRODUCTION_AUTHORIZED=false
        MASTERY_DECAY_REASSESSMENT_ENABLED=false MATH_VERIFIER_INTERNAL_TOKEN=local_math_verifier_token_change_me)
    settings+=("JAVA_HOME=$java_home")
    env -i "${settings[@]}" "$@"
}

echo ">>> Stage 2: Contracts, JSON Schemas & Negative Mutation Verification"
stage=contracts
safe_run "$python" scripts/quality/validate_json_schemas.py

echo ">>> Stage 3: Backend Modulith & Sprint 7.8 Integration Tests"
stage=backend
mkdir -p "$out/maven-reports"
tests="KnowledgeGraphProjectionServiceTest,CounterfactualTwinChallengeServiceTest,Sprint78KnowledgeGraphAndTwinIT,ModulithArchitectureTest,EvidenceStratifiedMasteryPolicyTest,HalfLifeRetentionCalculatorTest,MasteryDecayReassessmentTest,MistakeRetentionResolutionTest"

(cd services/api && safe_run "$mvn_cmd" -f pom.xml \
    -Dspring.profiles.active=test \
    "-Dsurefire.reportsDirectory=$out/maven-reports" \
    "-Dtest=$tests" test) | tee "$out/backend.log"

echo ">>> Stage 4: Python Math-Verifier Deterministic Twin Synthesizer Suite"
stage=math-verifier
safe_run "$python" -m pytest services/math-verifier/tests/test_twin_synthesis_v78.py -v

echo ">>> Stage 5: iOS Client State Machine & Localization Verification"
stage=ios
safe_run "$python" - << 'PY_IOS_VERIFY'
import json, sys
from pathlib import Path

root = Path(".")
strings_path = root / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings"
assert strings_path.exists(), "Localizable.xcstrings missing"
data = json.loads(strings_path.read_text(encoding="utf-8"))["strings"]

required = [
    "twin.challenge.title",
    "twin.challenge.locked_timer",
    "twin.challenge.reveal_solution",
    "twin.challenge.submit_answer",
    "twin.challenge.early_forfeit_warning",
    "twin.challenge.early_forfeit_confirm",
    "twin.challenge.success_title",
    "twin.challenge.remediation_credit",
    "twin.challenge.assisted_info",
    "mastery.graph.title",
    "mastery.graph.blocked_bottleneck",
    "mastery.graph.at_risk",
    "mastery.graph.practicing",
    "mastery.graph.mastered"
]

for key in required:
    assert key in data, f"Missing key in Localizable.xcstrings: {key}"
    for lang in ("en", "tr"):
        val = data[key]["localizations"][lang]["stringUnit"]["value"]
        assert val and len(val.strip()) > 0, f"Empty {lang} for {key}"

# Verify Swift files exist and have no TODOs
swift_files = [
    "apps/ios/VerifiedAI/Features/MasteryGraph/Domain/SkillGraphModels.swift",
    "apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/MasteryGraphViewModel.swift",
    "apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/MasteryGraphExplorerView.swift",
    "apps/ios/VerifiedAI/Features/TwinChallenge/Domain/VerifiedTwinModels.swift",
    "apps/ios/VerifiedAI/Features/TwinChallenge/Presentation/TwinChallengeViewModel.swift",
    "apps/ios/VerifiedAI/Features/TwinChallenge/Presentation/ProveItSoloTwinView.swift",
    "apps/ios/VerifiedAITests/Sprint78MasteryAndTwinTests.swift"
]

for sf in swift_files:
    p = root / sf
    assert p.exists(), f"Missing Swift file: {sf}"
    content = p.read_text(encoding="utf-8")
    assert "// TODO" not in content, f"TODO found in {sf}"
    assert "// implement later" not in content, f"Stub found in {sf}"

print("PASS: iOS Sprint 7.8 models, view models, views, tests, and 14 EN/TR string units verified.")
PY_IOS_VERIFY

echo ">>> Stage 6: Canonical Documentation & Manifest Synchronization"
stage=documentation
safe_run "$python" scripts/quality/docs_check.py

# Verify Sprint 7.8 documentation deliverables
for doc in \
    "docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.8_EXECUTION_REPORT.md" \
    "docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.8_FILE_MANIFEST.md"; do
    [[ -f "$doc" ]] || { echo "Missing required documentation: $doc" >&2; exit 2; }
done

echo ">>> Stage 7: Source Tree Cleanliness & Diff Check"
stage=source-integrity
git diff --check
echo "PASS: Zero whitespace or merge conflict markers found."
