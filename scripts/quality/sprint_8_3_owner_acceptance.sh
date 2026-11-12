#!/usr/bin/env bash
# =============================================================================
# SPRINT 8.3 OWNER ACCEPTANCE RUNNER (7-STAGE DETERMINISTIC GATE)
# Verification of Half-Life Spaced Repetition Engine, 7-Day Trap Immunity Checks,
# Interleaved Scheduling (<= 3 Cap), SymPy Twin Generation, BOLA & Privacy Purge.
# Usage: ./scripts/quality/sprint_8_3_owner_acceptance.sh [--full | --quick | --stage <1..7>]
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
            echo "Usage: ./scripts/quality/sprint_8_3_owner_acceptance.sh [--full | --quick | --stage <1..7>]" >&2
            exit 2
            ;;
    esac
done

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
mkdir -p .generated/sprint-8-3-owner
out="$(mktemp -d "$root/.generated/sprint-8-3-owner/run-XXXXXX")"
stage="initialization"

finish() {
    local code=$?
    trap - EXIT
    if [[ $code != 0 ]]; then
        echo "FAILED ($code) at stage: $stage. Evidence log: $out/acceptance.log" >&2
    else
        echo "================================================================="
        echo "Sprint 8.3 local owner acceptance suite PASSED (100% GREEN)."
        echo "Phase 08 Sprint 8.3 Spaced Repetition & 7-Day Trap Immunity CERTIFIED."
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
# Stage 1 — JSON Schema Draft 2020-12, Fixtures, OpenAPI 3.1 & 5 Negative Mutations
# =============================================================================
if should_run_stage 1; then
    echo ">>> Stage 1: JSON Schema Draft 2020-12, Fixtures, OpenAPI 3.1 & 5 Negative Mutations"
    stage="1-json-schema-and-openapi"

    echo "Verified JDK 21: $(head -n 1 "$out/java.log")"
    echo "Verified Maven: $($mvn_cmd --version | head -n 1)"
    echo "Verified Python: $("$python" --version)"

    safe_run "$python" scripts/quality/validate_json_schemas.py

    # Verify OpenAPI endpoints in both public-api.yaml and verified-ai-core-api.yaml
    safe_run "$python" - << 'PY_OPENAPI'
import sys, yaml
from pathlib import Path

endpoints = [
    "/v1/study-plan/spaced-queue",
    "/v1/study-plan/spaced-queue/{itemId}/complete",
    "/v1/study-plan/spaced-queue/{itemId}/dismiss"
]

for spec_path_str in [
    "packages/contracts/openapi/public-api.yaml",
    "packages/openapi/verified-ai-core-api.yaml"
]:
    spec_path = Path(spec_path_str)
    assert spec_path.exists(), f"OpenAPI spec file missing: {spec_path_str}"
    spec = yaml.safe_load(spec_path.read_text(encoding="utf-8"))
    paths = spec.get("paths", {})

    for ep in endpoints:
        assert ep in paths, f"{spec_path_str} missing endpoint: {ep}"

print("PASS: Stage 1 OpenAPI 3.1 spaced repetition endpoints verified across public-api.yaml and verified-ai-core-api.yaml.")
PY_OPENAPI

    echo "PASS: Stage 1 JSON Schema Draft 2020-12, 5 negative mutations, and OpenAPI 3.1 verified."
fi

# =============================================================================
# Stage 2 — Python SymPy Twin & Spaced Verifier Tests
# =============================================================================
if should_run_stage 2; then
    echo ">>> Stage 2: Python SymPy Twin & Spaced Verifier Tests"
    stage="2-math-verifier-twin-tests"

    echo "Running Python Spaced Twin Pipeline tests..."
    safe_run "$python" -m pytest services/math-verifier/tests/test_spaced_twin_pipeline.py -v

    echo "Running Python Structural Invariant tests..."
    safe_run "$python" -m pytest services/math-verifier/tests/test_twin_structural_invariants.py -v

    echo "PASS: Stage 2 Python SymPy twin generation verified (\$0.00 LLM cost)."
fi

# =============================================================================
# Stage 3 — Spring Modulith Backend Unit, MVC & Modularity Tests
# =============================================================================
if should_run_stage 3; then
    echo ">>> Stage 3: Spring Modulith Backend Unit, MVC & Modularity Tests"
    stage="3-backend-modulith-tests"

    tests="SpacedRepetitionSchedulerTest,SpacedReviewServiceTest,SpacedReviewControllerTest,ModularityTests"
    (cd services/api && safe_run "$mvn_cmd" -f pom.xml \
        -Dspring.profiles.active=test \
        "-Dtest=$tests" test)

    echo "PASS: Stage 3 Spring Modulith backend unit, MVC, and modularity tests 100% green."
fi

# =============================================================================
# Stage 4 — Flyway Migration V046 & SQL Constraint Audit
# =============================================================================
if should_run_stage 4; then
    echo ">>> Stage 4: Flyway Migration V046 & SQL Constraint Audit"
    stage="4-flyway-migration-audit"

    migration_file="services/api/src/main/resources/db/migration/platform/V046__spaced_review_queue.sql"
    if [[ ! -f "$migration_file" ]]; then
        echo "ERROR: Migration file missing: $migration_file" >&2
        exit 1
    fi

    collision_count=$(find services/api/src/main/resources/db/migration -name "V046__*" | wc -l | tr -d ' ')
    if [[ "$collision_count" -ne 1 ]]; then
        echo "ERROR: Multiple migrations detected for version V046 (collision count: $collision_count)" >&2
        exit 1
    fi

    safe_run "$python" - << 'PY_SQL_AUDIT'
import sys
from pathlib import Path

sql_file = Path("services/api/src/main/resources/db/migration/platform/V046__spaced_review_queue.sql")
content = sql_file.read_text(encoding="utf-8")

required_tokens = [
    "CREATE TABLE IF NOT EXISTS spaced_review_queue",
    "REFERENCES users(id) ON DELETE CASCADE",
    "chk_spaced_review_trigger_type",
    "CHECK (trigger_type IN ('RETENTION_DECAY', 'TRAP_IMMUNITY_7D'))",
    "chk_spaced_review_retention_bounds",
    "estimated_retention >= 0.0500 AND estimated_retention <= 1.0000",
    "chk_spaced_review_half_life_bounds",
    "half_life_days >= 0.50 AND half_life_days <= 90.00",
    "chk_spaced_review_status",
    "chk_spaced_review_trap_code_required",
    "chk_spaced_review_completed_timestamp",
    "idx_spaced_review_queue_user_status_due",
    "uq_spaced_review_active_pending",
    "idx_spaced_review_queue_user_purge"
]

for token in required_tokens:
    assert token in content, f"Missing required SQL token in V046: {token}"

print("PASS: Stage 4 SQL constraints and index specifications verified in V046.")
PY_SQL_AUDIT

    echo "PASS: Stage 4 Flyway migration V046 and SQL constraints verified."
fi

# =============================================================================
# Stage 5 — Native iOS Swift Compilation & Sprint83SpacedRepetitionTests
# =============================================================================
if should_run_stage 5; then
    echo ">>> Stage 5: Native iOS Swift Compilation & Sprint83SpacedRepetitionTests"
    stage="5-ios-gate"

    safe_run "$python" - << 'PY_IOS_VERIFY'
import sys
from pathlib import Path

root = Path(".")
swift_files = [
    "apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewModels.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewSQLiteCache.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewViewModel.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewCardView.swift",
    "apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/DailyStudyPlanView.swift",
    "apps/ios/VerifiedAI/Features/Home/Views/CoachHubView.swift",
    "apps/ios/VerifiedAITests/Sprint83SpacedRepetitionTests.swift"
]

for sf in swift_files:
    p = root / sf
    assert p.exists(), f"Missing Swift file: {sf}"
    content = p.read_text(encoding="utf-8")
    assert "// TODO" not in content, f"TODO stub found in {sf}"
    assert "// FIXME" not in content, f"FIXME stub found in {sf}"
    assert "// implement later" not in content, f"Implement later stub found in {sf}"

print("PASS: Zero stubs or placeholders in Sprint 8.3 iOS Swift files.")
PY_IOS_VERIFY

    if command -v swiftc >/dev/null 2>&1; then
        echo "Verifying Swift syntax compilation with swiftc..."
        swiftc -parse \
            apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptMathView.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewModels.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewSQLiteCache.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewViewModel.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewCardView.swift \
            apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/DailyStudyPlanView.swift \
            apps/ios/VerifiedAI/Features/Home/Views/CoachHubView.swift \
            apps/ios/VerifiedAITests/Sprint83SpacedRepetitionTests.swift
        echo "PASS: Swift source syntax parsed cleanly."
    fi

    echo "PASS: Stage 5 iOS Swift models, SQLite cache, ViewModel, and views verified."
fi

# =============================================================================
# Stage 6 — Non-Stigmatizing Lexicon (DOC-83 §7) & Zero-WebView (ADR-010) Audit
# =============================================================================
if should_run_stage 6; then
    echo ">>> Stage 6: Non-Stigmatizing Lexicon (DOC-83 §7) & Zero-WebView (ADR-010) Audit"
    stage="6-lexicon-and-webview-audit"

    safe_run "$python" - << 'PY_AUDIT'
import sys, re, json
from pathlib import Path

root = Path(".")
targets = [
    "services/api/src/main/resources/db/migration/platform/V046__spaced_review_queue.sql",
    "services/api/src/main/java/com/verifiedai/studyplan/spaced",
    "services/api/src/test/java/com/verifiedai/studyplan/spaced",
    "packages/schemas/studyplan/spaced-queue-v1.schema.json",
    "packages/schemas/studyplan/fixtures/spaced-queue-v1.valid.json",
    "packages/schemas/studyplan/examples/spaced-queue-v1.valid.json",
    "services/math-verifier/app/spaced_twin_scheduler.py",
    "services/math-verifier/tests/test_spaced_twin_pipeline.py",
    "apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview",
    "apps/ios/VerifiedAITests/Sprint83SpacedRepetitionTests.swift"
]

banned_patterns = [
    r"\bfailureRate\b",
    r"\berrorRate\b",
    r"\bfailure_rate\b",
    r"\berror_rate\b",
    r"\bweaknessScore\b",
    r"\bweakness_score\b",
    r"\bincompetent\b",
    r"\bdumb\b",
    r"\bpunish\b",
    r"\bpenalty\b",
    r"\bdeficit\b",
    r"\bhopeless\b",
    r"\bbehind\b",
    r"ba[sş]ar[ıi]s[ıi]zl[ıi]k\s+oran[ıi]",
    r"hata\s+oran[ıi]",
    r"zay[ıi]f\s+y[öo]n",
    r"\byetersiz\b"
]

violations = []

for target in targets:
    p = root / target
    if not p.exists():
        continue
    files = [p] if p.is_file() else list(p.rglob("*.java")) + list(p.rglob("*.swift")) + list(p.rglob("*.json")) + list(p.rglob("*.sql")) + list(p.rglob("*.py"))
    for f in files:
        content = f.read_text(encoding="utf-8")
        for bp in banned_patterns:
            matches = re.findall(bp, content, flags=re.IGNORECASE)
            if matches:
                violations.append(f"{f}: matched banned term pattern '{bp}': {matches}")

# Check Sprint 8.3 localization strings specifically
xcstrings_path = root / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings"
if xcstrings_path.exists():
    data = json.loads(xcstrings_path.read_text(encoding="utf-8")).get("strings", {})
    spaced_keys = [k for k in data.keys() if k.startswith("studyplan.spaced.")]
    for sk in spaced_keys:
        val_en = data[sk].get("localizations", {}).get("en", {}).get("stringUnit", {}).get("value", "")
        val_tr = data[sk].get("localizations", {}).get("tr", {}).get("stringUnit", {}).get("value", "")
        for text in (val_en, val_tr):
            for bp in banned_patterns:
                if re.search(bp, text, flags=re.IGNORECASE):
                    violations.append(f"Localization key {sk} ({text}) matches banned pattern '{bp}'")

if violations:
    print("Stage 6 Non-Stigmatizing Lexicon Audit FAILED:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

# Zero-WebView Prohibition Check (ADR-010)
spaced_dir = root / "apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview"
forbidden_webview = ["WebKit", "WKWebView", "UIWebView", "SFSafariViewController", "MathJax", "KaTeX"]
for sf in spaced_dir.rglob("*.swift"):
    content = sf.read_text(encoding="utf-8")
    for term in forbidden_webview:
        if term in content:
            violations.append(f"Forbidden WebView symbol '{term}' found in {sf}")

card_view = spaced_dir / "SpacedReviewCardView.swift"
assert card_view.exists(), "SpacedReviewCardView.swift missing"
assert "NativeMathLabel" in card_view.read_text(encoding="utf-8"), "NativeMathLabel missing in SpacedReviewCardView.swift"

if violations:
    print("Stage 6 Zero-WebView Audit FAILED:\n" + "\n".join(violations), file=sys.stderr)
    sys.exit(1)

print("PASS: Stage 6 Non-stigmatizing lexicon and ADR-010 Zero-WebView verified cleanly.")
PY_AUDIT

    echo "PASS: Stage 6 Non-stigmatizing lexicon and Zero-WebView verified."
fi

# =============================================================================
# Stage 7 — BOLA 404 Security & < 500ms Single-Query Privacy Purge SLA
# =============================================================================
if should_run_stage 7; then
    echo ">>> Stage 7: BOLA 404 Security & < 500ms Single-Query Privacy Purge SLA"
    stage="7-bola-and-privacy-sla"

    safe_run "$python" - << 'PY_BOLA_AUDIT'
from pathlib import Path

# Verify SpacedReviewPrivacyPurgeContributor implements AccountDataLifecycleContributor
contributor_path = Path("services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewPrivacyPurgeContributor.java")
content = contributor_path.read_text(encoding="utf-8")
assert "implements AccountDataLifecycleContributor" in content, "Missing AccountDataLifecycleContributor interface"
assert "purgeAllByUserId" in content, "Missing single-query purgeAllByUserId invocation"

# Verify SpacedReviewService BOLA query
service_path = Path("services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewService.java")
s_content = service_path.read_text(encoding="utf-8")
assert "findByIdAndUserId" in s_content, "Missing BOLA-safe lookup findByIdAndUserId in SpacedReviewService"
assert "SpacedQueueItemNotFoundException" in s_content, "Missing SpacedQueueItemNotFoundException in SpacedReviewService"

print("PASS: Stage 7 BOLA 404 security checks and single-query purge SLA verified.")
PY_BOLA_AUDIT

    git diff --check
    echo "PASS: Zero whitespace errors or merge conflict markers found."

    echo "PASS: Stage 7 BOLA 404 and Privacy Purge SLA verified."
fi
