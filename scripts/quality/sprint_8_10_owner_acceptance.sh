#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PYTHON_BIN="python3"
if ! "$PYTHON_BIN" --version >/dev/null 2>&1; then
  for candidate in \
    /Library/Frameworks/Python.framework/Versions/3.14/bin/python3 \
    /usr/bin/python3; do
    if [[ -x "$candidate" ]] && "$candidate" --version >/dev/null 2>&1; then
      PYTHON_BIN="$candidate"
      break
    fi
  done
fi

MODE="full"
STAGE=""
REQUIRE_DOCKER="false"
SIM_DESTINATION="${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}"

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
      STAGE="${2:-}"
      shift 2
      ;;
    --require-docker)
      REQUIRE_DOCKER="true"
      shift
      ;;
    *)
      echo "Usage: $0 [--full|--quick|--stage <1..7>] [--require-docker]"
      exit 2
      ;;
  esac
done

log_step() {
  printf "\n==> [Sprint 8.10 Acceptance] %s\n" "$1"
}

run_stage_1() {
  log_step "Stage 1: Docs, Contracts, Prompt Immutability, Localization & Zero-Jackson-2 Checks"
  find . -name ".DS_Store" -delete 2>/dev/null || true
  "$PYTHON_BIN" scripts/quality/docs_check.py
  "$PYTHON_BIN" scripts/quality/check_contracts.py
  "$PYTHON_BIN" scripts/quality/check_prompt_immutability.py --base-ref HEAD
  "$PYTHON_BIN" scripts/quality/check_sprint_6_10_localization.py /tmp/sprint_8_10_localization_report.json

  if rg -n "com\.fasterxml\.jackson" \
    services/api/src/main/java/com/verifiedai/studyplan/coldstart \
    services/api/src/main/java/com/verifiedai/studyplan/quality \
    services/api/src/main/java/com/verifiedai/studyplan/service/DefaultNextBestActionSignalProvider.java \
    services/api/src/main/java/com/verifiedai/studyplan/privacy/StudyPlanPrivacyLifecycleContributor.java; then
    echo "ERROR: Forbidden com.fasterxml.jackson import detected (Jackson 3 tools.jackson.* required)."
    exit 1
  fi
  echo "PASS: Zero Jackson 2 imports verified."
}

run_stage_2() {
  log_step "Stage 2: JSON Schema Validation, Negative Mutations & OpenAPI Mirror Parity"
  cmp -s packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml
  echo "PASS: public-api.yaml and verified-ai-core-api.yaml are byte-identical."
  if [[ -x "services/math-verifier/.venv/bin/python" ]] && services/math-verifier/.venv/bin/python -c "import jsonschema, yaml" >/dev/null 2>&1; then
    services/math-verifier/.venv/bin/python scripts/quality/validate_json_schemas.py
  else
    "$PYTHON_BIN" scripts/quality/validate_json_schemas.py
  fi
}

run_stage_3() {
  log_step "Stage 3: Math Verifier Deterministic Cold-Start Tests (\$0.00 LLM Cost)"
  if [[ -x "services/math-verifier/.venv/bin/pytest" ]] && services/math-verifier/.venv/bin/pytest --version >/dev/null 2>&1; then
    services/math-verifier/.venv/bin/pytest services/math-verifier/tests/test_cold_start_diagnostic_verifier.py -v
  else
    PYTHONPATH="services/math-verifier:services/math-verifier/.venv/lib/python3.11/site-packages" \
      "$PYTHON_BIN" -m pytest services/math-verifier/tests/test_cold_start_diagnostic_verifier.py -v
  fi
}

run_stage_4() {
  log_step "Stage 4: Backend Calibration, Quality Gate, Controller, Privacy & Modulith Tests"
  mvn -f services/api/pom.xml \
    -Dtest=ColdStartCalibrationServiceTest,PlannerQualityGateServiceTest,ColdStartAndQualityGateControllerTest,StudyPlanPrivacyLifecycleContributorTest,StudyPlanServiceIntegrationTest,ModularityTest,ModularityTests \
    test
}

run_stage_5() {
  log_step "Stage 5: PostgreSQL Testcontainers & Flyway V053 Migration Integration"
  if docker info >/dev/null 2>&1; then
    mvn -f services/api/pom.xml -Dtest=ColdStartAndQualityGatePostgresIntegrationTest test
  elif [[ "$REQUIRE_DOCKER" == "true" ]]; then
    echo "ERROR: Docker daemon is unavailable and --require-docker was specified."
    exit 1
  else
    echo "SKIP: Docker daemon not running; compile-only verification of ColdStartAndQualityGatePostgresIntegrationTest."
    mvn -f services/api/pom.xml test-compile
  fi
}

run_stage_6() {
  log_step "Stage 6: iOS Swift 6 Build, Unit Tests (Sprint810ColdStartCalibrationTests) & UI Tests"
  xcodebuild test \
    -project apps/ios/VerifiedAI.xcodeproj \
    -scheme VerifiedAI \
    -destination "$SIM_DESTINATION" \
    -only-testing:VerifiedAITests/Sprint810ColdStartCalibrationTests \
    -only-testing:VerifiedAITests/Sprint84CoachHubTests \
    -only-testing:VerifiedAIUITests/VerifiedAIUITests/testColdStartBaselineDiagnosticCardAndThreeStepFlow
}

run_stage_7() {
  log_step "Stage 7: Sprint 8.10 Gate Test Hygiene Audit (Zero Skips)"
  if rg -n "@Disabled|@Ignore|pytest\.mark\.skip|XCTSkip" \
    services/api/src/test/java/com/verifiedai/studyplan/coldstart \
    services/api/src/test/java/com/verifiedai/studyplan/quality \
    services/math-verifier/tests/test_cold_start_diagnostic_verifier.py \
    apps/ios/VerifiedAITests/Sprint810ColdStartCalibrationTests.swift; then
    echo "ERROR: Found disabled or skipped test annotation in Sprint 8.10 suite."
    exit 1
  fi
  echo "PASS: Zero skipped/disabled tests across all Sprint 8.10 suites."
}

if [[ -n "$STAGE" ]]; then
  "run_stage_${STAGE}"
  exit 0
fi

run_stage_1
run_stage_2
run_stage_3
run_stage_4
run_stage_7

if [[ "$MODE" == "full" ]]; then
  run_stage_5
  run_stage_6
fi

printf "\n==> [Sprint 8.10 Acceptance] ALL REQUESTED STAGES COMPLETED SUCCESSFULLY\n"
