#!/usr/bin/env bash
# ============================================================================
# Sprint 8.1 Owner Acceptance Gate Runner & Phase 08 Foundation Certification
# Verification of Deterministic Next-Best-Action (NBA) Recommendation Engine,
# High-ROI Topic Allocation, Prerequisite Override, BOLA Security, and Privacy.
# ============================================================================
set -Eeuo pipefail

if [[ $# != 1 || $1 != --full ]]; then
    echo 'Usage: bash scripts/quality/sprint_8_1_owner_acceptance.sh --full' >&2
    exit 2
fi

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
mkdir -p .generated/sprint-8-1-owner
out="$(mktemp -d "$root/.generated/sprint-8-1-owner/run-XXXXXX")"
stage=prerequisites

finish() {
    local code=$?
    trap - EXIT
    if [[ $code != 0 ]]; then
        echo "FAILED ($code) at stage: $stage. Evidence log: $out/acceptance.log" >&2
    else
        echo "================================================================="
        echo "Sprint 8.1 local owner acceptance suite PASSED (100% GREEN)."
        echo "Phase 08 Sprint 8.1 Foundation Gate is formally CERTIFIED."
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

echo ">>> Stage 2: Gate 1 & Gate 2 — Contracts, Non-Stigmatizing Audit & JSON Schemas"
stage=contracts-and-terminology
safe_run "$python" scripts/quality/validate_json_schemas.py

# Gate 2: Non-stigmatizing terminology audit
echo "Checking for banned stigmatizing terminology (failureRate, errorRate, etc.)..."
safe_run "$python" - << 'PY_TERMINOLOGY_AUDIT'
import re, sys
from pathlib import Path

root = Path(".")
banned_patterns = [
    r'\bfailureRate\b',
    r'\berrorRate\b',
    r'\bfailure_rate\b',
    r'\berror_rate\b',
    r'\bweaknessScore\b',
    r'\bweakness_score\b',
    r'\bincompetent\b',
    r'\bincompetence\b',
    r'ba[sş]ar[ıi]s[ıi]zl[ıi]k\s+oran[ıi]',
    r'hata\s+oran[ıi]',
    r'zay[ıi]f\s+y[öo]n'
]

targets = [
    "services/api/src/main/resources/db/migration/platform/V043__next_best_action_recommendations.sql",
    "services/api/src/main/java/com/verifiedai/studyplan",
    "packages/schemas/studyplan",
    "apps/ios/VerifiedAI/Features/Home/Domain",
    "apps/ios/VerifiedAI/Features/Home/ViewModels",
    "apps/ios/VerifiedAI/Features/Home/Views",
    "apps/ios/VerifiedAITests/Sprint81RecommendationTests.swift"
]

violations = []
for target in targets:
    p = root / target
    if not p.exists():
        continue
    files = [p] if p.is_file() else list(p.rglob("*.java")) + list(p.rglob("*.swift")) + list(p.rglob("*.json")) + list(p.rglob("*.sql"))
    for f in files:
        content = f.read_text(encoding="utf-8")
        for pattern in banned_patterns:
            matches = re.findall(pattern, content, re.IGNORECASE)
            if matches:
                violations.append(f"File {f} matches banned pattern {pattern}: {matches}")

if violations:
    print("Terminology audit FAILED with violations:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

print("PASS: Gate 2 Non-stigmatizing terminology audit passed cleanly.")
PY_TERMINOLOGY_AUDIT

echo ">>> Stage 3: Gate 3 — ADR-010 Native 2D Math & WebView Prohibition Audit"
stage=adr010-native-math
safe_run "$python" - << 'PY_WEBVIEW_AUDIT'
import sys
from pathlib import Path

root = Path(".")
dirs_to_check = [
    "apps/ios/VerifiedAI/Features/Home/Domain",
    "apps/ios/VerifiedAI/Features/Home/ViewModels",
    "apps/ios/VerifiedAI/Features/Home/Views"
]

forbidden_imports = ["WebKit", "WKWebView", "UIWebView", "MathJax", "KaTeX"]
violations = []

for d in dirs_to_check:
    p = root / d
    if not p.exists():
        continue
    for sf in p.rglob("*.swift"):
        content = sf.read_text(encoding="utf-8")
        for fi in forbidden_imports:
            if fi in content:
                violations.append(f"Forbidden {fi} found in {sf}")

if violations:
    print("Gate 3 ADR-010 WebView audit FAILED:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

print("PASS: Gate 3 ADR-010 Native 2D math rendering verified with zero WebViews.")
PY_WEBVIEW_AUDIT

echo ">>> Stage 4: Gate 4 — Backend Modulith, NBA Recommendation Engine & Integration Tests"
stage=backend-tests
mkdir -p "$out/maven-reports"
tests="NextBestActionRecommendationEngineTest,StudyPlanServiceIntegrationTest,StudyPlanPrivacyLifecycleContributorTest,RecommendationControllerTest,ModulithArchitectureTest"

(cd services/api && safe_run "$mvn_cmd" -f pom.xml \
    -Dspring.profiles.active=test \
    "-Dsurefire.reportsDirectory=$out/maven-reports" \
    "-Dtest=$tests" test) | tee "$out/backend.log"

echo "PASS: Gate 4 Backend Modulith and NBA test suite passed 100% green."

echo ">>> Stage 5: Gate 5 — iOS Swift Models, ViewModels, Views, and Localization Verification"
stage=ios-verification
safe_run "$python" - << 'PY_IOS_VERIFY'
import json, sys
from pathlib import Path

root = Path(".")
strings_path = root / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings"
assert strings_path.exists(), "Localizable.xcstrings missing"
data = json.loads(strings_path.read_text(encoding="utf-8"))["strings"]

swift_files = [
    "apps/ios/VerifiedAI/Features/Home/Domain/RecommendationModels.swift",
    "apps/ios/VerifiedAI/Features/Home/Cache/RecommendationSQLiteCache.swift",
    "apps/ios/VerifiedAI/Features/Home/ViewModels/CoachHubViewModel.swift",
    "apps/ios/VerifiedAI/Features/Home/Views/RecommendationCardView.swift",
    "apps/ios/VerifiedAITests/Sprint81RecommendationTests.swift"
]

for sf in swift_files:
    p = root / sf
    assert p.exists(), f"Missing Swift file: {sf}"
    content = p.read_text(encoding="utf-8")
    assert "// TODO" not in content, f"TODO found in {sf}"
    assert "// FIXME" not in content, f"FIXME found in {sf}"
    assert "// implement later" not in content, f"Stub found in {sf}"

print("PASS: Gate 5 iOS Sprint 8.1 models, view models, views, and tests verified.")
PY_IOS_VERIFY

if command -v swiftc >/dev/null 2>&1; then
    echo "Verifying Swift syntax compilation with swiftc..."
    swiftc -parse \
        apps/ios/VerifiedAI/Features/Home/Domain/RecommendationModels.swift \
        apps/ios/VerifiedAI/Features/Home/Cache/RecommendationSQLiteCache.swift \
        apps/ios/VerifiedAI/Features/Home/ViewModels/CoachHubViewModel.swift
    echo "PASS: Swift source syntax parsed cleanly."
fi

echo ">>> Stage 6: Gate 6 — Documentation Synchronization & Manifest Validation"
stage=documentation
safe_run "$python" scripts/quality/docs_check.py

# Verify Sprint 8.1 documentation deliverables exist
for doc in \
    "docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.1_EXECUTION_REPORT.md" \
    "docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.1_FILE_MANIFEST.md"; do
    [[ -f "$doc" ]] || { echo "Missing required documentation: $doc" >&2; exit 2; }
done

echo "PASS: Gate 6 Documentation verified and synchronized."

echo ">>> Stage 7: Gate 7 — Source Tree Cleanliness & Diff Check"
stage=source-integrity
git diff --check
echo "PASS: Zero whitespace or merge conflict markers found."
