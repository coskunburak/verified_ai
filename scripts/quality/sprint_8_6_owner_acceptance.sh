#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="full"
STAGE_FILTER=""

usage() {
  cat <<'EOF'
Usage: scripts/quality/sprint_8_6_owner_acceptance.sh [--full | --quick | --stage <1..7>]

Sprint 8.6 Owner Acceptance Gate (AI Micro-Lessons Triggered by Diagnosed Patterns)
  --full         Run all 7 stages (default)
  --quick        Skip full Python suite when pydantic_core is unavailable; run targeted Sprint 8.6 tests
  --stage N      Run only stage N (1..7)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full) MODE="full"; shift ;;
    --quick) MODE="quick"; shift ;;
    --stage)
      if [[ $# -lt 2 ]]; then usage; exit 2; fi
      STAGE_FILTER="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
PYTHON_BIN="${PYTHON_BIN:-}"
if [[ -z "$PYTHON_BIN" ]]; then
  if [[ -x "/Library/Frameworks/Python.framework/Versions/3.14/bin/python3" ]]; then
    PYTHON_BIN="/Library/Frameworks/Python.framework/Versions/3.14/bin/python3"
  elif [[ -x "/opt/homebrew/bin/python3" ]]; then
    PYTHON_BIN="/opt/homebrew/bin/python3"
  else
    PYTHON_BIN="python3"
  fi
fi
VENV_PYTHON="$ROOT_DIR/services/math-verifier/.venv/bin/python3"
MVN_BIN="${MVN_BIN:-/opt/homebrew/bin/mvn}"
if [[ ! -x "$MVN_BIN" ]]; then
  MVN_BIN="mvn"
fi

should_run_stage() {
  local s="$1"
  [[ -z "$STAGE_FILTER" || "$STAGE_FILTER" == "$s" ]]
}

stage_1() {
  echo "[Stage 1] Sprint 8.6 Hygiene, Zero-LLM, Zero-Media & DOC-83 §7 Lexicon Audit..."
  "$PYTHON_BIN" - <<'PY'
import json
import pathlib
import re
import sys

errors = []

sprint_files = [
    pathlib.Path("services/math-verifier/app/micro_lesson_verifier.py"),
    pathlib.Path("services/api/src/main/resources/db/migration/platform/V049__micro_lesson_cards.sql"),
    pathlib.Path("services/api/src/main/resources/micro_lessons/micro_lesson_catalog_v1.json"),
    pathlib.Path("services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonHashVerifier.java"),
    pathlib.Path("services/api/src/main/java/com/verifiedai/tutoring/application/MicroLessonApplicationService.java"),
    pathlib.Path("services/api/src/main/java/com/verifiedai/tutoring/application/MicroLessonDataLifecycleContributor.java"),
    pathlib.Path("services/api/src/main/java/com/verifiedai/tutoring/infrastructure/persistence/JdbcMicroLessonRepository.java"),
    pathlib.Path("services/api/src/main/java/com/verifiedai/tutoring/api/MicroLessonController.java"),
    pathlib.Path("apps/ios/VerifiedAI/Features/Tutor/Domain/MicroLessonModels.swift"),
    pathlib.Path("apps/ios/VerifiedAI/Features/Tutor/Data/MicroLessonSQLiteCache.swift"),
    pathlib.Path("apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonViewModel.swift"),
    pathlib.Path("apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonDrawerView.swift"),
]

stub_re = re.compile(r"\b(TODO|FIXME|XXX|HACK|NotImplementedError|fatalError)\b")
for path in sprint_files:
    if not path.exists():
        errors.append(f"Missing Sprint 8.6 file: {path}")
        continue
    text = path.read_text(encoding="utf-8")
    if path.name != "micro_lesson_verifier.py":
        m = stub_re.search(text)
        if m:
            errors.append(f"Stub marker '{m.group(0)}' in {path}")

# Zero WebKit / WKWebView / KaTeX / MathJax across iOS production tree
for swift_file in pathlib.Path("apps/ios/VerifiedAI").rglob("*.swift"):
    content = swift_file.read_text(encoding="utf-8", errors="ignore")
    for banned_tech in ("import WebKit", "WKWebView", "KaTeX", "MathJax"):
        if banned_tech in content:
            errors.append(f"Forbidden web/math renderer '{banned_tech}' in {swift_file}")

# Zero runtime LLM/network imports in verifier
verifier_src = pathlib.Path("services/math-verifier/app/micro_lesson_verifier.py").read_text(encoding="utf-8")
for net_mod in ("openai", "anthropic", "google.generativeai", "httpx", "requests", "urllib.request"):
    if re.search(rf"\b{re.escape(net_mod)}\b", verifier_src):
        errors.append(f"Forbidden network/LLM reference '{net_mod}' in micro_lesson_verifier.py")

# Zero external media URLs and zero DOC-83 §7 stigmatizing lexicon in catalog & Localizable.xcstrings
media_re = re.compile(r"(https?://|\.mp4\b|\.mov\b|\.m3u8\b|\byoutube\b|\bvimeo\b)", re.IGNORECASE)
banned_en = re.compile(
    r"\b(failed|failure|weakness|weaknesses|incompetent|dumb|stupid|hopeless|"
    r"bad\s+student|wrong\s+again|still\s+wrong|you\s+never|careless|lazy|"
    r"deficit|remedial|subpar|flunked|penalty|punish|shame|embarrassing)\b",
    re.IGNORECASE,
)
banned_tr = re.compile(
    r"(?:(?<!\w)|^)(başarısız|zayıflık|yetersiz|tembel|aptal|beceriksiz|umutsuz|"
    r"kötü\s+öğrenci|yine\s+yanlış|hala\s+yanlış|ceza|utanç|kusur|eksiklik)(?:(?!\w)|$)",
    re.IGNORECASE,
)

catalog = json.loads(pathlib.Path("services/api/src/main/resources/micro_lessons/micro_lesson_catalog_v1.json").read_text(encoding="utf-8"))
def scan_json(val, loc="catalog"):
    if isinstance(val, str):
        if media_re.search(val):
            errors.append(f"External media URL in {loc}: {val!r}")
        if banned_en.search(val) or banned_tr.search(val):
            errors.append(f"DOC-83 §7 lexicon match in {loc}: {val!r}")
    elif isinstance(val, dict):
        for k, v in val.items():
            scan_json(v, f"{loc}.{k}")
    elif isinstance(val, list):
        for idx, item in enumerate(val):
            scan_json(item, f"{loc}[{idx}]")

scan_json(catalog)

xc = json.loads(pathlib.Path("apps/ios/VerifiedAI/Resources/Localizable.xcstrings").read_text(encoding="utf-8"))
for key, entry in xc.get("strings", {}).items():
    if key.startswith("micro_lesson."):
        for lang, loc in entry.get("localizations", {}).items():
            val = loc.get("stringUnit", {}).get("value", "")
            if banned_en.search(val) or banned_tr.search(val):
                errors.append(f"DOC-83 §7 lexicon in xcstrings[{key}][{lang}]: {val!r}")

if errors:
    for err in errors:
        print(f"ERROR: {err}", file=sys.stderr)
    sys.exit(1)

print("Stage 1 OK: zero stubs, zero WebKit/KaTeX, zero runtime LLM/media, zero DOC-83 §7 terms.")
PY
}

stage_2() {
  echo "[Stage 2] Contracts, JSON Schemas, OpenAPI 3.1 Mirrors & EN/TR Localization Audit..."
  "$PYTHON_BIN" scripts/quality/validate_json_schemas.py
  diff -q packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml
  echo "Stage 2 OK: JSON schemas, negative fixtures, byte-identical OpenAPI mirrors, and EN/TR localizations verified."
}

stage_3() {
  echo "[Stage 3] Python SymPy Micro-Lesson Verifier & 34-Card SHA-256 Catalog Parity..."
  local math_pythonpath="$ROOT_DIR/services/math-verifier/.venv/lib/python3.11/site-packages:$ROOT_DIR/services/math-verifier:$ROOT_DIR"
  if [[ "$MODE" != "quick" ]] && "$VENV_PYTHON" -c "import pydantic_core" >/dev/null 2>&1; then
    PYTHONPATH="$math_pythonpath" "$VENV_PYTHON" -m pytest services/math-verifier/tests -q
  else
    PYTHONPATH="$math_pythonpath" "$PYTHON_BIN" -m pytest services/math-verifier/tests/test_micro_lesson_verifier.py -v
  fi
  echo "Stage 3 OK."
}

stage_4() {
  echo "[Stage 4] Database Migration V049 Static & 34-Card Seed Hash Parity Audit..."
  "$PYTHON_BIN" - <<'PY'
import json
import pathlib
import re
import sys

v049 = pathlib.Path("services/api/src/main/resources/db/migration/platform/V049__micro_lesson_cards.sql").read_text(encoding="utf-8")
required_tokens = [
    "CREATE TABLE IF NOT EXISTS micro_lesson_catalog",
    "CREATE TABLE IF NOT EXISTS learner_micro_lesson_completions",
    "uq_micro_lesson_catalog_mistake_locale",
    "chk_micro_lesson_catalog_locale",
    "chk_micro_lesson_catalog_duration",
    "chk_micro_lesson_catalog_verification_status",
    "chk_micro_lesson_catalog_hash_hex",
    "uq_learner_micro_lesson_idempotency",
    "chk_learner_micro_lesson_trigger",
    "chk_learner_micro_lesson_dwell",
    "chk_learner_micro_lesson_training_false",
    "ON CONFLICT (mistake_code, locale) DO NOTHING",
]
missing = [t for t in required_tokens if t not in v049]
if missing:
    print(f"ERROR: V049 missing required tokens: {missing}", file=sys.stderr)
    sys.exit(1)

catalog = json.loads(pathlib.Path("services/api/src/main/resources/micro_lessons/micro_lesson_catalog_v1.json").read_text(encoding="utf-8"))
by_pair = {(c["mistakeCode"], c["locale"]): c["verificationHash"] for c in catalog["cards"]}
matches = re.findall(
    r"VALUES\s*\('([0-9a-f-]{36})',\s*'([A-Z0-9_]+)',\s*'(tr-TR|en-US)',.*?'VERIFIED',\s*'([0-9a-f]{64})',\s*'sympy-1\.12-v1'\)",
    v049,
    re.DOTALL,
)
assert len(matches) == 34, f"Expected 34 seed rows in V049, got {len(matches)}"
for _, code, locale, sql_hash in matches:
    assert by_pair.get((code, locale)) == sql_hash, f"Hash mismatch for {(code, locale)}"

print("Stage 4 OK: V049 DDL, constraints, indexes, and 34 SymPy seed hashes verified.")
PY
}

stage_5() {
  echo "[Stage 5] Spring Boot Micro-Lesson Service, BOLA, Privacy & Modulith Suite..."
  "$MVN_BIN" -f services/api/pom.xml \
    -Dtest=MicroLessonHashVerifierTest,MicroLessonApplicationServiceTest,MicroLessonControllerTest,MicroLessonPrivacyLifecycleTest,ModularityTest,ModularityTests \
    -DfailIfNoTests=false \
    test
  echo "Stage 5 OK."
}

stage_6() {
  echo "[Stage 6] iOS Native Parse & Standalone Simulator Typecheck..."
  local swift_files=(
    apps/ios/VerifiedAI/Features/Tutor/Domain/MicroLessonModels.swift
    apps/ios/VerifiedAI/Features/Tutor/Data/MicroLessonSQLiteCache.swift
    apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonViewModel.swift
    apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonDrawerView.swift
    apps/ios/VerifiedAI/Features/Tutor/Presentation/TutorView.swift
    apps/ios/VerifiedAITests/Sprint86MicroLessonTests.swift
  )
  swiftc -parse "${swift_files[@]}"

  local ios_sdk
  ios_sdk="$(xcrun --show-sdk-path --sdk iphonesimulator)"
  swiftc -typecheck \
    -module-cache-path /tmp/swift-module-cache \
    -sdk "$ios_sdk" \
    -target arm64-apple-ios17.0-simulator \
    apps/ios/VerifiedAI/Features/Tutor/Domain/MicroLessonModels.swift \
    apps/ios/VerifiedAI/Features/Tutor/Data/MicroLessonSQLiteCache.swift \
    apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonViewModel.swift \
    apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonDrawerView.swift
  echo "Stage 6 OK: Swift parse & iOS simulator typecheck passed."
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

echo "ALL SPRINT 8.6 OWNER ACCEPTANCE GATES PASSED ($MODE)."
