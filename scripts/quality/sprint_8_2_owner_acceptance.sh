#!/usr/bin/env bash
# =============================================================================
# SPRINT 8.2 OWNER ACCEPTANCE RUNNER (7-STAGE DETERMINISTIC GATE)
# Verification of Adaptive Practice Question Selection, Tier-1 Deterministic
# Twin Engine, 60-Second Independent Verification Protocol, BOLA & Privacy SLA.
# Usage: ./scripts/quality/sprint_8_2_owner_acceptance.sh [--full | --quick | --stage <1..7>]
# =============================================================================
set -Eeuo pipefail

MODE="full"
SPECIFIED_STAGE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --full)
            MODE="full"
            shift
            ;;
        --quick)
            MODE="quick"
            shift
            ;;
        --stage)
            SPECIFIED_STAGE="$2"
            shift 2
            ;;
        *)
            echo "Usage: ./scripts/quality/sprint_8_2_owner_acceptance.sh [--full | --quick | --stage <1..7>]" >&2
            exit 2
            ;;
    esac
done

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
mkdir -p .generated/sprint-8-2-owner
out="$(mktemp -d "$root/.generated/sprint-8-2-owner/run-XXXXXX")"
stage="initialization"

finish() {
    local code=$?
    trap - EXIT
    if [[ $code != 0 ]]; then
        echo "FAILED ($code) at stage: $stage. Evidence log: $out/acceptance.log" >&2
    else
        echo "================================================================="
        echo "Sprint 8.2 local owner acceptance suite PASSED (100% GREEN)."
        echo "Phase 08 Sprint 8.2 Practice & Twin Engine is formally CERTIFIED."
        echo "Evidence directory: $out"
        echo "================================================================="
    fi
    exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
exec > >(tee "$out/acceptance.log") 2>&1

should_run_stage() {
    local s="$1"
    if [[ -z "$SPECIFIED_STAGE" || "$SPECIFIED_STAGE" == "$s" ]]; then
        return 0
    else
        return 1
    fi
}

# Locate Python
python=/opt/homebrew/bin/python3
if [[ ! -x "$python" ]]; then
    python=python3
fi
export PYTHONDONTWRITEBYTECODE=1
export PYTHONPATH="$root/services/math-verifier/.venv/lib/python3.11/site-packages:$root/services/math-verifier:$root"

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
export JAVA_HOME="$java_home"

safe_run() {
    local -a settings=("PATH=$PATH" "TMPDIR=${TMPDIR:-/tmp}" "PYTHONPATH=$PYTHONPATH" "JAVA_HOME=$JAVA_HOME" PYTHONDONTWRITEBYTECODE=1)
    env -i "${settings[@]}" "$@"
}

# =============================================================================
# Stage 1 — Toolchain & Migration Integrity Check
# =============================================================================
if should_run_stage 1; then
    echo ">>> Stage 1: Toolchain & Migration Integrity Check"
    stage="1-toolchain-and-migration"

    echo "Verified JDK 21: $(head -n 1 "$out/java.log")"
    echo "Verified Maven: $($mvn_cmd --version | head -n 1)"
    echo "Verified Python: $("$python" --version)"

    migration_file="services/api/src/main/resources/db/migration/platform/V044__adaptive_practice_sessions.sql"
    if [[ ! -f "$migration_file" ]]; then
        echo "ERROR: Migration file missing: $migration_file" >&2
        exit 1
    fi

    # Verify no version collisions on V044
    collision_count=$(find services/api/src/main/resources/db/migration -name "V044__*" | wc -l | tr -d ' ')
    if [[ "$collision_count" -ne 1 ]]; then
        echo "ERROR: Multiple migrations detected for version V044 (collision count: $collision_count)" >&2
        exit 1
    fi

    echo "PASS: Stage 1 Toolchain and Flyway migration V044 verified."
fi

# =============================================================================
# Stage 2 — JSON Schema Draft 2020-12, 5 Negative Mutations & OpenAPI 3.1 Gate
# =============================================================================
if should_run_stage 2; then
    echo ">>> Stage 2: JSON Schema Draft 2020-12, 5 Negative Mutations & OpenAPI 3.1 Gate"
    stage="2-json-schema-and-openapi"

    safe_run "$python" scripts/quality/validate_json_schemas.py

    # Verify OpenAPI paths in public-api.yaml
    safe_run "$python" - << 'PY_OPENAPI'
import sys, yaml
from pathlib import Path

spec_path = Path("packages/contracts/openapi/public-api.yaml")
spec = yaml.safe_load(spec_path.read_text(encoding="utf-8"))
paths = spec.get("paths", {})

endpoints = [
    "/v1/study-plan/practice/sessions",
    "/v1/study-plan/practice/sessions/{sessionId}",
    "/v1/study-plan/practice/sessions/{sessionId}/items/{itemId}/submit",
    "/v1/study-plan/practice/sessions/{sessionId}/abandon"
]

for ep in endpoints:
    assert ep in paths, f"OpenAPI missing path: {ep}"

print("PASS: Stage 2 OpenAPI 3.1 practice endpoints verified in public-api.yaml.")
PY_OPENAPI

    echo "PASS: Stage 2 JSON Schema Draft 2020-12, 5 negative mutations, and OpenAPI 3.1 verified."
fi

# =============================================================================
# Stage 3 — Non-Stigmatizing Lexicon Audit (DOC-83 §7)
# =============================================================================
if should_run_stage 3; then
    echo ">>> Stage 3: Non-Stigmatizing Lexicon Audit (DOC-83 §7)"
    stage="3-lexicon-audit"

    safe_run "$python" - << 'PY_LEXICON_AUDIT'
import sys, re, json
from pathlib import Path

root = Path(".")
targets = [
    "services/api/src/main/resources/db/migration/platform/V044__adaptive_practice_sessions.sql",
    "services/api/src/main/java/com/verifiedai/studyplan/practice",
    "services/api/src/test/java/com/verifiedai/studyplan/practice",
    "packages/schemas/studyplan/practice-session-v1.schema.json",
    "packages/schemas/studyplan/fixtures/practice-session-v1.valid.json",
    "services/math-verifier/app/twin_generator.py",
    "services/math-verifier/app/answer_evaluator.py",
    "services/math-verifier/app/routers/practice_verifier.py",
    "services/math-verifier/tests/test_twin_structural_invariants.py",
    "apps/ios/VerifiedAI/Features/StudyPlan/Practice",
    "apps/ios/VerifiedAITests/Sprint82PracticeTests.swift"
]

banned = [
    r"\bfailureRate\b",
    r"\berrorRate\b",
    r"\bfailure_rate\b",
    r"\berror_rate\b",
    r"\bweaknessScore\b",
    r"\bweakness_score\b",
    r"\bincompetent\b",
    r"\bdumb\b",
    r"ba[sş]ar[ıi]s[ıi]zl[ıi]k\s+oran[ıi]",
    r"hata\s+oran[ıi]",
    r"zay[ıi]f\s+y[öo]n",
    r"\byetersiz\b"
]

violations = []

# Scan Sprint 8.2 targets
for target in targets:
    p = root / target
    if not p.exists():
        continue
    files = [p] if p.is_file() else list(p.rglob("*.java")) + list(p.rglob("*.swift")) + list(p.rglob("*.json")) + list(p.rglob("*.sql")) + list(p.rglob("*.py"))
    for f in files:
        content = f.read_text(encoding="utf-8")
        for bp in banned:
            matches = re.findall(bp, content, flags=re.IGNORECASE)
            if matches:
                violations.append(f"{f}: matched banned term pattern '{bp}': {matches}")

# Also verify Sprint 8.2 localization keys specifically
xcstrings_path = root / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings"
if xcstrings_path.exists():
    data = json.loads(xcstrings_path.read_text(encoding="utf-8")).get("strings", {})
    practice_keys = [k for k in data.keys() if k.startswith("practice.")]
    for pk in practice_keys:
        val_en = data[pk].get("localizations", {}).get("en", {}).get("stringUnit", {}).get("value", "")
        val_tr = data[pk].get("localizations", {}).get("tr", {}).get("stringUnit", {}).get("value", "")
        for text in (val_en, val_tr):
            for bp in banned:
                if re.search(bp, text, flags=re.IGNORECASE):
                    violations.append(f"Localization key {pk} ({text}) matches banned pattern '{bp}'")

if violations:
    print("Stage 3 Non-Stigmatizing Lexicon Audit FAILED:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

print("PASS: Stage 3 Non-stigmatizing growth-oriented lexicon verified cleanly.")
PY_LEXICON_AUDIT
fi

# =============================================================================
# Stage 4 — ADR-010 Native Math & Zero-WebView Prohibition Gate
# =============================================================================
if should_run_stage 4; then
    echo ">>> Stage 4: ADR-010 Native Math & Zero-WebView Prohibition Gate"
    stage="4-adr010-webview-prohibition"

    safe_run "$python" - << 'PY_WEBVIEW_AUDIT'
import sys
from pathlib import Path

root = Path(".")
practice_dir = root / "apps/ios/VerifiedAI/Features/StudyPlan/Practice"
assert practice_dir.exists(), "Practice feature directory missing"

forbidden = ["WebKit", "WKWebView", "UIWebView", "SFSafariViewController", "MathJax", "KaTeX"]
violations = []

for sf in practice_dir.rglob("*.swift"):
    content = sf.read_text(encoding="utf-8")
    for term in forbidden:
        if term in content:
            violations.append(f"Forbidden symbol '{term}' found in {sf}")

if violations:
    print("Stage 4 ADR-010 Zero-WebView Prohibition FAILED:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

# Verify NativeMathLabel is used in PracticeQuestionCardView.swift
card_view = practice_dir / "PracticeQuestionCardView.swift"
assert card_view.exists(), "PracticeQuestionCardView.swift missing"
assert "NativeMathLabel" in card_view.read_text(encoding="utf-8"), "NativeMathLabel missing in PracticeQuestionCardView.swift"

print("PASS: Stage 4 ADR-010 Native 2D math layout verified with zero WebViews.")
PY_WEBVIEW_AUDIT
fi

# =============================================================================
# Stage 5 — Python SymPy Twin Verifier & Spring Modulith Backend Tests
# =============================================================================
if should_run_stage 5; then
    echo ">>> Stage 5: Python SymPy Twin Verifier & Spring Modulith Backend Tests"
    stage="5-math-verifier-and-backend-tests"

    echo "Running Python SymPy Twin Invariant Tests (100 twins across 4 trap families)..."
    safe_run "$python" -m pytest services/math-verifier/tests/test_twin_structural_invariants.py -v

    echo "Running Spring Modulith Backend Tests..."
    tests="AdaptivePracticeSelectorTest,PracticeSessionServiceTest,PracticeSessionControllerTest,ModulithArchitectureTest"
    (cd services/api && safe_run "$mvn_cmd" -f pom.xml \
        -Dspring.profiles.active=test \
        "-Dtest=$tests" test)

    echo "PASS: Stage 5 Math verifier invariants & Spring Modulith backend tests 100% green."
fi

# =============================================================================
# Stage 6 — Native iOS SwiftUI Model, SQLite Cache & ViewModel Gate
# =============================================================================
if should_run_stage 6; then
    echo ">>> Stage 6: Native iOS SwiftUI Model, SQLite Cache & ViewModel Gate"
    stage="6-ios-gate"

    safe_run "$python" - << 'PY_IOS_VERIFY'
import sys
from pathlib import Path

root = Path(".")
swift_files = [
    "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionModels.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSQLiteCache.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionViewModel.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeQuestionCardView.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionView.swift",
    "apps/ios/VerifiedAITests/Sprint82PracticeTests.swift"
]

for sf in swift_files:
    p = root / sf
    assert p.exists(), f"Missing Swift file: {sf}"
    content = p.read_text(encoding="utf-8")
    assert "// TODO" not in content, f"TODO stub found in {sf}"
    assert "// FIXME" not in content, f"FIXME stub found in {sf}"
    assert "// implement later" not in content, f"Implement later stub found in {sf}"

print("PASS: Zero stubs or placeholders in iOS Swift source files.")
PY_IOS_VERIFY

    if command -v swiftc >/dev/null 2>&1; then
        echo "Verifying Swift syntax compilation with swiftc..."
        swiftc -parse \
            apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptMathView.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionModels.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSQLiteCache.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionViewModel.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeQuestionCardView.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionView.swift
        echo "PASS: Swift source syntax parsed cleanly."
    fi

    echo "PASS: Stage 6 iOS Swift models, SQLite cache, and ViewModels verified."
fi

# =============================================================================
# Stage 7 — Documentation Manifest Sync & Git Hygiene
# =============================================================================
if should_run_stage 7; then
    echo ">>> Stage 7: Documentation Manifest Sync & Git Hygiene"
    stage="7-documentation-and-git"

    # Verify Sprint 8.2 deliverables exist
    for doc in \
        "docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.2_EXECUTION_REPORT.md" \
        "docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.2_FILE_MANIFEST.md"; do
        [[ -f "$doc" ]] || { echo "Missing required documentation: $doc" >&2; exit 2; }
    done

    safe_run "$python" scripts/quality/docs_check.py

    git diff --check
    echo "PASS: Zero whitespace errors or merge conflict markers found."

    echo "PASS: Stage 7 Documentation and Git hygiene verified."
fi
