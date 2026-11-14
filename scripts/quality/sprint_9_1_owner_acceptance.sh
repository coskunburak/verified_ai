#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

PYTHON_BIN="python3"
if ! "$PYTHON_BIN" --version >/dev/null 2>&1; then
  for candidate in \
    /Library/Frameworks/Python.framework/Versions/3.14/bin/python3 \
    /Library/Frameworks/Python.framework/Versions/3.11/bin/python3 \
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
  printf "\n==> [Sprint 9.1 Acceptance] %s\n" "$1"
}

run_stage_1() {
  log_step "Stage 1: Docs, Contracts, Prompt Immutability, Localization, Secrets & Zero-Jackson-2 Checks"
  find . -name ".DS_Store" -delete 2>/dev/null || true
  "$PYTHON_BIN" scripts/quality/docs_check.py
  "$PYTHON_BIN" scripts/quality/check_contracts.py
  "$PYTHON_BIN" scripts/quality/check_prompt_immutability.py --base-ref HEAD
  "$PYTHON_BIN" scripts/quality/check_sprint_6_10_localization.py /tmp/sprint_9_1_localization_report.json
  bash scripts/security/secret_scan.sh

  if rg -n "com\.fasterxml\.jackson" services/api/src/main/java/com/verifiedai/exam; then
    echo "ERROR: Forbidden com.fasterxml.jackson import detected in com.verifiedai.exam (Jackson 3 tools.jackson.* required)."
    exit 1
  fi
  echo "PASS: Zero Jackson 2 imports verified in com.verifiedai.exam."
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
  log_step "Stage 3: Exam Domain Core, Weight Conservation, Scoring Policies & V054 SQL Seed Integrity"
  mvn -f services/api/pom.xml \
    -Dtest=ExamBlueprintValidatorAndScoringTest,V054SeedSqlConsistencyTest \
    test
}

run_stage_4() {
  log_step "Stage 4: Backend Exam Controller, Privacy Lifecycle & Spring Modulith Boundary Tests"
  mvn -f services/api/pom.xml \
    -Dtest=ExamPlatformControllerTest,ExamPrivacyLifecycleContributorTest,ModularityTest,ModulithArchitectureTest,RepositoryHierarchyTest \
    test
}

run_stage_5() {
  log_step "Stage 5: PostgreSQL Testcontainers & Flyway V054 Migration Integration"
  if docker info >/dev/null 2>&1; then
    mvn -f services/api/pom.xml -Dtest=ExamPlatformPostgresIntegrationTest test
  elif [[ "$REQUIRE_DOCKER" == "true" ]]; then
    echo "ERROR: Docker daemon is unavailable and --require-docker was specified."
    exit 1
  else
    echo "SKIP: Docker daemon not running; compile-only verification of ExamPlatformPostgresIntegrationTest."
    mvn -f services/api/pom.xml test-compile
  fi
}

run_stage_6() {
  log_step "Stage 6: iOS Swift 6 Build, Unit Tests (Sprint91ExamCatalogAndTargetTests) & UI Tests (ExamCatalogUITests)"
  xcodebuild test \
    -project apps/ios/VerifiedAI.xcodeproj \
    -scheme VerifiedAI \
    -destination "$SIM_DESTINATION" \
    -only-testing:VerifiedAITests/Sprint91ExamCatalogAndTargetTests \
    -only-testing:VerifiedAIUITests/ExamCatalogUITests
}

run_stage_7() {
  log_step "Stage 7: Sprint 9.1 Test Hygiene Audit (Zero Skips) & Cost/Training Boundary Check"
  if rg -n "@Disabled|@Ignore|pytest\.mark\.skip|XCTSkip|assumeTrue\(false\)" \
    services/api/src/test/java/com/verifiedai/exam \
    apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift \
    apps/ios/VerifiedAIUITests/ExamCatalogUITests.swift; then
    echo "ERROR: Found disabled or skipped test annotation in Sprint 9.1 suite."
    exit 1
  fi
  echo "PASS: Zero skipped/disabled tests across all Sprint 9.1 suites."
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

printf "\n==> [Sprint 9.1 Acceptance] ALL REQUESTED STAGES COMPLETED SUCCESSFULLY\n"
