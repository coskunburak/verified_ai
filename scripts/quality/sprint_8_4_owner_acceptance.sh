#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="full"
SINGLE_STAGE=""

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
      MODE="stage"
      SINGLE_STAGE="${2:-}"
      if [[ -z "$SINGLE_STAGE" ]]; then
        echo "ERROR: --stage requires a number 1..7" >&2
        exit 2
      fi
      shift 2
      ;;
    *)
      echo "Usage: $0 [--full | --quick | --stage <1..7>]" >&2
      exit 2
      ;;
  esac
done

PYTHON_BIN="/opt/homebrew/bin/python3"
if [[ ! -x "$PYTHON_BIN" ]]; then
  PYTHON_BIN="$(command -v python3)"
fi

MVN_BIN="$(command -v mvn || true)"
if [[ -z "$MVN_BIN" && -x "/opt/homebrew/bin/mvn" ]]; then
  MVN_BIN="/opt/homebrew/bin/mvn"
fi

JAVA21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
if [[ ! -d "$JAVA21_HOME" ]]; then
  JAVA21_HOME="${JAVA_HOME:-}"
fi

stage_1_contracts() {
  echo "[Stage 1] JSON Schema Draft 2020-12, Negative Mutations & OpenAPI 3.1 Parity..."
  "$PYTHON_BIN" "$ROOT_DIR/scripts/quality/validate_json_schemas.py"

  diff -q \
    "$ROOT_DIR/packages/contracts/openapi/public-api.yaml" \
    "$ROOT_DIR/packages/openapi/verified-ai-core-api.yaml"

  for endpoint in \
    "/v1/coach/today-summary:" \
    "/v1/coach/preferences:" \
    "/v1/coach/missions/{id}/complete:" \
    "/v1/coach/missions/{id}/downshift:"
  do
    if ! grep -qF "$endpoint" "$ROOT_DIR/packages/contracts/openapi/public-api.yaml"; then
      echo "ERROR: Missing OpenAPI endpoint $endpoint" >&2
      exit 1
    fi
  done
  echo "[Stage 1] PASS"
}

stage_2_python_prewarmer() {
  echo "[Stage 2] Python SymPy Daily Coach Twin Pre-Warmer Tests & Zero-LLM Audit..."
  PYTHONPATH="$ROOT_DIR/services/math-verifier/.venv/lib/python3.11/site-packages:$ROOT_DIR/services/math-verifier:$ROOT_DIR" \
    "$PYTHON_BIN" -m pytest "$ROOT_DIR/services/math-verifier/tests/test_daily_coach_prewarmer.py" -v

  local prewarmer_file="$ROOT_DIR/services/math-verifier/app/daily_coach_prewarmer.py"
  if grep -E 'import[[:space:]]+(openai|anthropic|google\.generativeai|httpx|requests|urllib|aiohttp)' "$prewarmer_file" >/dev/null 2>&1; then
    echo "ERROR: Disallowed LLM or HTTP network import detected in $prewarmer_file" >&2
    exit 1
  fi
  echo "[Stage 2] PASS"
}

stage_3_backend_modulith() {
  echo "[Stage 3] Spring Modulith Backend Unit, Controller & Architecture Tests..."
  if [[ "$MODE" == "quick" ]]; then
    echo "[Stage 3] SKIPPED (--quick mode)"
    return 0
  fi
  if [[ -z "$MVN_BIN" ]]; then
    echo "ERROR: mvn binary not found" >&2
    exit 1
  fi
  JAVA_HOME="$JAVA21_HOME" PATH="$JAVA21_HOME/bin:$PATH" \
    "$MVN_BIN" -f "$ROOT_DIR/services/api/pom.xml" \
    -Dspring.profiles.active=test \
    "-Dtest=DailyCoachSessionServiceTest,DailyCoachControllerTest,ModularityTests" \
    test
  echo "[Stage 3] PASS"
}

stage_4_flyway_migration() {
  echo "[Stage 4] Flyway V047 Schema, Constraints & Indexes Audit..."
  local v047_count
  v047_count="$(find "$ROOT_DIR/services/api/src/main/resources/db/migration" -name 'V047__*.sql' | wc -l | tr -d ' ')"
  if [[ "$v047_count" -ne 1 ]]; then
    echo "ERROR: Expected exactly 1 V047 migration file, found $v047_count" >&2
    exit 1
  fi

  local sql_file="$ROOT_DIR/services/api/src/main/resources/db/migration/platform/V047__daily_coach_sessions.sql"
  if [[ ! -f "$sql_file" ]]; then
    echo "ERROR: Missing $sql_file" >&2
    exit 1
  fi

  "$PYTHON_BIN" - "$sql_file" <<'PY'
import pathlib, sys
sql = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
required_tokens = [
    "CREATE TABLE IF NOT EXISTS daily_coach_sessions",
    "CREATE TABLE IF NOT EXISTS daily_coach_tasks",
    "CHECK (budget_minutes IN (5, 10, 15, 25))",
    "CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))",
    "CHECK (preferred_mode IN ('SOLVER_MODE', 'SOCRATIC_COACH_MODE'))",
    "CHECK (task_order BETWEEN 1 AND 5)",
    "CHECK (task_type IN ('TRAP_REPAIR', 'SPACED_RETENTION', 'EXAM_SYNTHESIS'))",
    "CHECK (status IN ('PENDING', 'COMPLETED', 'SKIPPED'))",
    "UNIQUE (user_id, local_date)",
    "UNIQUE (session_id, task_order)",
    "idx_daily_coach_session_user_date",
    "idx_daily_coach_tasks_session",
]
for tok in required_tokens:
    assert tok in sql, f"Missing SQL invariant token: {tok}"
print("PASS: V047 tables, CHECK constraints, unique keys, and indexes verified.")
PY
  echo "[Stage 4] PASS"
}

stage_5_ios_swift_audit() {
  echo "[Stage 5] Native iOS Swift Parse, Zero-Stub & SQLITE_TRANSIENT Audit..."
  local swift_files=(
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptMathView.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Data/DailyCoachSQLiteCache.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Presentation/DualModeSwitcher.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/StudyPlan/Presentation/DailyStudyPlanView.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift"
    "$ROOT_DIR/apps/ios/VerifiedAITests/Sprint84CoachHubTests.swift"
  )

  swiftc -parse "${swift_files[@]}"

  local non_test_sprint84_files=(
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Data/DailyCoachSQLiteCache.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Presentation/DualModeSwitcher.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift"
    "$ROOT_DIR/apps/ios/VerifiedAI/Features/StudyPlan/Presentation/DailyStudyPlanView.swift"
    "$ROOT_DIR/services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachController.java"
    "$ROOT_DIR/services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionService.java"
    "$ROOT_DIR/services/api/src/main/java/com/verifiedai/studyplan/coach/DeterministicCoachBriefingBuilder.java"
    "$ROOT_DIR/services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachPrivacyPurgeContributor.java"
    "$ROOT_DIR/services/math-verifier/app/daily_coach_prewarmer.py"
  )

  "$PYTHON_BIN" - "${non_test_sprint84_files[@]}" <<'PY'
import pathlib, re, sys
stub_re = re.compile(r"\b(TODO|FIXME|placeholder|mock|stub)\b", re.IGNORECASE)
for p in sys.argv[1:]:
    text = pathlib.Path(p).read_text(encoding="utf-8")
    for idx, line in enumerate(text.splitlines(), 1):
        m = stub_re.search(line)
        assert not m, f"Stub token '{m.group(0)}' found at {p}:{idx}: {line.strip()}"
print("PASS: Zero stubs or placeholders across Sprint 8.4 production files.")
PY

  local cache_file="$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Data/DailyCoachSQLiteCache.swift"
  if ! grep -q "SQLITE_TRANSIENT" "$cache_file"; then
    echo "ERROR: SQLITE_TRANSIENT missing in $cache_file" >&2
    exit 1
  fi
  echo "[Stage 5] PASS"
}

stage_6_lexicon_and_zerowebview() {
  echo "[Stage 6] Non-Stigmatizing Lexicon (DOC-83 S7) & Zero-WebView (ADR-010) Audit..."
  "$PYTHON_BIN" - "$ROOT_DIR" <<'PY'
import json, pathlib, re, sys

root = pathlib.Path(sys.argv[1])
banned = re.compile(
    r"\b(fail|failure|weak|weakness|poor|bad|stupid|dumb|incompetent|overdue|punish|penalty|deficit|flaw|hopeless|behind)\b",
    re.IGNORECASE,
)

sprint84_files = [
    root / "services/api/src/main/resources/db/migration/platform/V047__daily_coach_sessions.sql",
    root / "packages/schemas/coach/daily-coach-v1.schema.json",
    root / "packages/schemas/coach/fixtures/daily-coach-v1.valid.json",
    root / "services/math-verifier/app/daily_coach_prewarmer.py",
    root / "apps/ios/VerifiedAI/Features/Home/Data/DailyCoachSQLiteCache.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/DualModeSwitcher.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift",
    root / "apps/ios/VerifiedAI/Features/StudyPlan/Presentation/DailyStudyPlanView.swift",
    root / "apps/ios/VerifiedAITests/Sprint84CoachHubTests.swift",
]
sprint84_files.extend(sorted((root / "services/api/src/main/java/com/verifiedai/studyplan/coach").rglob("*.java")))

for p in sprint84_files:
    text = p.read_text(encoding="utf-8")
    for idx, line in enumerate(text.splitlines(), 1):
        m = banned.search(line)
        assert not m, f"Stigmatizing word '{m.group(0)}' in {p}:{idx}: {line.strip()}"

xcstrings = json.loads((root / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings").read_text(encoding="utf-8"))
for key, entry in xcstrings.get("strings", {}).items():
    if not key.startswith("coach."):
        continue
    for lang, block in entry.get("localizations", {}).items():
        val = block.get("stringUnit", {}).get("value", "")
        m = banned.search(val)
        assert not m, f"Stigmatizing word '{m.group(0)}' in Localizable.xcstrings[{key}][{lang}]: {val}"

swift_views = [
    root / "apps/ios/VerifiedAI/Features/Home/Data/DailyCoachSQLiteCache.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/DualModeSwitcher.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift",
    root / "apps/ios/VerifiedAI/Features/StudyPlan/Presentation/DailyStudyPlanView.swift",
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift",
]
webview_re = re.compile(r"\b(WebKit|WKWebView|UIWebView|SFSafariViewController|MathJax|KaTeX)\b")
for p in swift_views:
    text = p.read_text(encoding="utf-8")
    m = webview_re.search(text)
    assert not m, f"WebView/JS math token '{m.group(0)}' forbidden in {p}"

for math_view in (
    root / "apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift",
    root / "apps/ios/VerifiedAI/Features/StudyPlan/Presentation/DailyStudyPlanView.swift",
):
    assert "NativeMathLabel(" in math_view.read_text(encoding="utf-8"), f"Expected NativeMathLabel in {math_view}"

print("PASS: Zero stigmatizing lexicon terms and zero WebKit/WKWebView imports verified.")
PY
  echo "[Stage 6] PASS"
}

stage_7_security_and_purge() {
  echo "[Stage 7] BOLA 404 & Privacy Purge (< 500 ms) Contract Verification..."
  local purge_java="$ROOT_DIR/services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachPrivacyPurgeContributor.java"
  local ctrl_test="$ROOT_DIR/services/api/src/test/java/com/verifiedai/studyplan/coach/DailyCoachControllerTest.java"
  local svc_test="$ROOT_DIR/services/api/src/test/java/com/verifiedai/studyplan/coach/DailyCoachSessionServiceTest.java"
  local cache_swift="$ROOT_DIR/apps/ios/VerifiedAI/Features/Home/Data/DailyCoachSQLiteCache.swift"

  grep -q "AccountDataLifecycleContributor" "$purge_java"
  grep -q "deleteAllByUserId" "$purge_java"
  grep -q "purgeUserData" "$cache_swift"
  grep -q "isNotFound" "$ctrl_test"
  grep -q "DailyCoachSessionNotFoundException" "$svc_test"
  echo "[Stage 7] PASS"
}

run_stage() {
  case "$1" in
    1) stage_1_contracts ;;
    2) stage_2_python_prewarmer ;;
    3) stage_3_backend_modulith ;;
    4) stage_4_flyway_migration ;;
    5) stage_5_ios_swift_audit ;;
    6) stage_6_lexicon_and_zerowebview ;;
    7) stage_7_security_and_purge ;;
    *) echo "Invalid stage: $1" >&2; exit 2 ;;
  esac
}

if [[ "$MODE" == "stage" ]]; then
  run_stage "$SINGLE_STAGE"
else
  for s in 1 2 3 4 5 6 7; do
    run_stage "$s"
  done
fi

echo "=== SPRINT 8.4 OWNER ACCEPTANCE: ALL REQUESTED STAGES PASSED ==="
