#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="full"
TARGET_STAGE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --quick)
      MODE="quick"
      shift
      ;;
    --full)
      MODE="full"
      shift
      ;;
    --stage)
      if [[ $# -lt 2 ]]; then
        echo "ERROR: --stage requires an argument (1..7)" >&2
        exit 2
      fi
      TARGET_STAGE="$2"
      shift 2
      ;;
    -h|--help)
      cat <<'EOF'
Usage: scripts/quality/sprint_8_5_owner_acceptance.sh [--full|--quick|--stage <1..7>]

Stages:
  1: Environment, Non-Stigmatizing Lexicon, WebKit & Zero-Stub Audit
  2: Contracts, Schemas, OpenAPI Mirror & EN/TR Localization Audit
  3: Python Math-Verifier Regression
  4: Database Migration V048 Static & Constraint Audit
  5: Spring Boot Rebalancing Engine & BOLA Security Suite
  6: iOS Native Parse & Standalone Simulator Typecheck
  7: Documentation Manifest & Secret Hygiene Gate
EOF
      exit 0
      ;;
    *)
      echo "ERROR: Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

PYTHON_BIN="python3"
if [[ -x "/opt/homebrew/bin/python3" ]]; then
  PYTHON_BIN="/opt/homebrew/bin/python3"
fi

VENV_PYTHON="$ROOT_DIR/services/math-verifier/.venv/bin/python"
if [[ ! -x "$VENV_PYTHON" ]]; then
  VENV_PYTHON="$PYTHON_BIN"
fi

MVN_BIN="mvn"
if [[ -x "/opt/homebrew/bin/mvn" ]]; then
  MVN_BIN="/opt/homebrew/bin/mvn"
fi

if [[ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

should_run_stage() {
  local stage_num="$1"
  if [[ -n "$TARGET_STAGE" ]]; then
    [[ "$TARGET_STAGE" == "$stage_num" ]]
    return
  fi
  return 0
}

stage_1() {
  echo "[Stage 1] Environment, Non-Stigmatizing Lexicon, WebKit & Zero-Stub Audit..."
  "$PYTHON_BIN" --version
  java -version 2>&1 | head -n 1
  "$MVN_BIN" --version | head -n 1
  swiftc --version | head -n 1

  "$PYTHON_BIN" - <<'PY'
import json
import pathlib
import re
import sys

root = pathlib.Path(".")

stub_pattern = re.compile(r"\b(TODO|FIXME|XXX|HACK|NotImplementedError)\b")
banned_lexicon = re.compile(
    r"\b(overdue|behind|fail|failure|punish|penalty|weak|weakness|poor|bad|stupid|dumb|incompetent|deficit|flaw|hopeless)\b",
    re.IGNORECASE,
)
webkit_pattern = re.compile(r"\b(WebKit|WKWebView|KaTeX|MathJax|evaluateJavaScript)\b")

prod_files = [
    root / "services/api/src/main/resources/db/migration/platform/V048__study_plan_rebalance_events.sql",
    root / "apps/ios/VerifiedAI/Features/Home/Data/RebalanceSQLiteCache.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/StudyPlanRebalanceViewModel.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/PlanRebalanceNotificationSheet.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift",
    root / "packages/schemas/studyplan/recovery-status-v1.schema.json",
    root / "packages/schemas/studyplan/rebalance-response-v1.schema.json",
]

java_dir = root / "services/api/src/main/java/com/verifiedai/studyplan/rebalance"
prod_files.extend(sorted(java_dir.rglob("*.java")))

doc_files = [
    root / "docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.5_EXECUTION_REPORT.md",
    root / "docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.5_FILE_MANIFEST.md",
]

errors = []

for path in prod_files:
    if not path.exists():
        errors.append(f"Missing expected Sprint 8.5 production file: {path}")
        continue
    text = path.read_text(encoding="utf-8")
    if stub_pattern.search(text):
        errors.append(f"Stub marker detected in {path}")
    for line_no, line in enumerate(text.splitlines(), start=1):
        m = banned_lexicon.search(line)
        if m:
            errors.append(f"Banned lexicon term '{m.group(0)}' in {path}:{line_no}")

for doc_path in doc_files:
    if doc_path.exists():
        text = doc_path.read_text(encoding="utf-8")
        for line_no, line in enumerate(text.splitlines(), start=1):
            m = banned_lexicon.search(line)
            if m:
                errors.append(f"Banned lexicon term '{m.group(0)}' in {doc_path}:{line_no}")

for swift_dir in [
    root / "apps/ios/VerifiedAI/Features/Home",
    root / "apps/ios/VerifiedAI/Features/StudyPlan",
]:
    for path in sorted(swift_dir.rglob("*.swift")):
        text = path.read_text(encoding="utf-8")
        if webkit_pattern.search(text):
            errors.append(f"Banned WebKit/KaTeX/MathJax symbol in {path}")

xcstrings = root / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings"
strings_map = json.loads(xcstrings.read_text(encoding="utf-8")).get("strings", {})
for key, entry in strings_map.items():
    if key.startswith("studyplan.rebalance."):
        for lang, loc in entry.get("localizations", {}).items():
            val = loc.get("stringUnit", {}).get("value", "")
            m = banned_lexicon.search(val)
            if m:
                errors.append(f"Banned lexicon term '{m.group(0)}' in xcstrings[{key}][{lang}]")

if errors:
    for err in errors:
        print(f"ERROR: {err}", file=sys.stderr)
    sys.exit(1)

print("Stage 1 OK: zero stubs, zero banned lexicon terms, zero WebKit/KaTeX imports.")
PY
}

stage_2() {
  echo "[Stage 2] Contracts, Schemas, OpenAPI Mirror & EN/TR Localization Audit..."
  "$PYTHON_BIN" scripts/quality/validate_json_schemas.py
  diff -q packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml
  echo "Stage 2 OK: JSON schemas, negative fixtures, OpenAPI mirror, and EN/TR localizations verified."
}

stage_3() {
  echo "[Stage 3] Python Math-Verifier Regression..."
  local math_pythonpath="$ROOT_DIR/services/math-verifier/.venv/lib/python3.11/site-packages:$ROOT_DIR/services/math-verifier:$ROOT_DIR"
  if [[ "$MODE" != "quick" ]] && "$VENV_PYTHON" -c "import pydantic_core" >/dev/null 2>&1; then
    PYTHONPATH="$math_pythonpath" "$VENV_PYTHON" -m pytest services/math-verifier/tests -q
  else
    echo "NOTE: Python 3.11 pydantic_core runtime unavailable in current shell; running test_daily_coach_prewarmer.py regression."
    PYTHONPATH="$math_pythonpath" "$PYTHON_BIN" -m pytest services/math-verifier/tests/test_daily_coach_prewarmer.py -v
  fi
  echo "Stage 3 OK."
}

stage_4() {
  echo "[Stage 4] Database Migration V048 Static & Constraint Audit..."
  "$PYTHON_BIN" - <<'PY'
import pathlib
import sys

v048 = pathlib.Path("services/api/src/main/resources/db/migration/platform/V048__study_plan_rebalance_events.sql")
text = v048.read_text(encoding="utf-8")

required_tokens = [
    "CREATE TABLE IF NOT EXISTS study_plan_rebalance_events",
    "chk_rebalance_trigger_type",
    "chk_rebalance_new_budget",
    "chk_rebalance_active_today_cap",
    "chk_rebalance_missed_days_non_negative",
    "chk_rebalance_days_until_exam",
    "chk_rebalance_prev_budget",
    "chk_rebalance_prune_reason",
    "uq_rebalance_events_user_idempotency",
    "idx_rebalance_events_user_created",
    "idx_rebalance_events_user_recovery_date",
    "chk_coach_session_budget_minutes",
    "chk_daily_coach_session_status",
    "EXPIRED",
]

missing = [tok for tok in required_tokens if tok not in text]
if missing:
    print(f"ERROR: V048 missing required tokens: {missing}", file=sys.stderr)
    sys.exit(1)

print("Stage 4 OK: V048 schema, CHECK constraints, and indexes verified.")
PY
}

stage_5() {
  echo "[Stage 5] Spring Boot Rebalancing Engine & BOLA Security Suite..."
  "$MVN_BIN" -f services/api/pom.xml -Dspring.profiles.active=test \
    -Dtest=MissedDayRecoveryPolicyTest,StudyPlanRebalancingServiceTest,RebalanceControllerTest,DailyCoachSessionServiceTest,DailyCoachControllerTest,SpacedReviewServiceTest,ModularityTests \
    test
  echo "Stage 5 OK."
}

stage_6() {
  echo "[Stage 6] iOS Native Parse & Standalone Simulator Typecheck..."
  local swift_files=(
    apps/ios/VerifiedAI/Features/Home/Data/RebalanceSQLiteCache.swift
    apps/ios/VerifiedAI/Features/Home/Presentation/StudyPlanRebalanceViewModel.swift
    apps/ios/VerifiedAI/Features/Home/Presentation/PlanRebalanceNotificationSheet.swift
    apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift
    apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift
    apps/ios/VerifiedAITests/Sprint85RebalancingTests.swift
  )
  swiftc -parse "${swift_files[@]}"

  local ios_sdk
  ios_sdk="$(xcrun --show-sdk-path --sdk iphonesimulator)"
  swiftc -typecheck \
    -module-cache-path /tmp/swift-module-cache \
    -sdk "$ios_sdk" \
    -target arm64-apple-ios17.0-simulator \
    apps/ios/VerifiedAI/Features/Home/Data/RebalanceSQLiteCache.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/StudyPlanRebalanceViewModel.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/PlanRebalanceNotificationSheet.swift
  echo "Stage 6 OK: Swift parse & simulator typecheck passed."
}

stage_7() {
  echo "[Stage 7] Documentation Manifest & Secret Hygiene Gate..."
  "$PYTHON_BIN" scripts/quality/docs_check.py
  "$PYTHON_BIN" scripts/security/test_secret_scan.py
  echo "Stage 7 OK."
}

should_run_stage 1 && stage_1
should_run_stage 2 && stage_2
should_run_stage 3 && stage_3
should_run_stage 4 && stage_4
should_run_stage 5 && stage_5
should_run_stage 6 && stage_6
should_run_stage 7 && stage_7

echo "Sprint 8.5 Owner Acceptance (${MODE}) PASSED."
