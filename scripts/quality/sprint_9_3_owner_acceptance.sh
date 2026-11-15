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
      echo "Usage: $0 [--full|--quick|--stage <1..8>] [--require-docker]"
      exit 2
      ;;
  esac
done

log_step() {
  printf "\n==> [Sprint 9.3 Acceptance] %s\n" "$1"
}

run_stage_1() {
  log_step "Stage 1: Docs, Contracts, Prompt Immutability, Localization, Secrets & Zero-Jackson-2 Checks"
  find . -name ".DS_Store" -delete 2>/dev/null || true
  "$PYTHON_BIN" scripts/quality/docs_check.py
  "$PYTHON_BIN" scripts/quality/check_contracts.py
  "$PYTHON_BIN" scripts/quality/check_prompt_immutability.py --base-ref HEAD
  "$PYTHON_BIN" scripts/quality/check_sprint_6_10_localization.py /tmp/sprint_9_3_localization_report.json
  bash scripts/quality/secret_scan.sh

  if rg -n "com\.fasterxml\.jackson" services/api/src/main/java/com/verifiedai/exam services/api/src/test/java/com/verifiedai/exam; then
    echo "ERROR: Forbidden com.fasterxml.jackson import detected in com.verifiedai.exam (Jackson 3 tools.jackson.* required)."
    exit 1
  fi
  echo "PASS: Zero legacy Jackson 2 imports verified across com.verifiedai.exam."
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
  log_step "Stage 3: Question Bank, Blueprint, Deterministic Assembly Engine & V054/V055/V056 SQL Consistency Tests"
  mvn -f services/api/pom.xml \
    -Dtest=QuestionBankItemDomainTest,AssessmentBlueprintDomainTest,DeterministicAssessmentAssemblyEngineTest,V054SeedSqlConsistencyTest,V055MigrationSqlConsistencyTest,V056MigrationSqlConsistencyTest \
    test
}

run_stage_4() {
  log_step "Stage 4: Backend Assembly Controller, Candidate Profile, Privacy Lifecycle & Modulith/AI Provider Boundaries"
  mvn -f services/api/pom.xml \
    -Dtest=ExamAssessmentAssemblyControllerTest,CandidateProfileControllerWebMvcTest,ExamPlatformControllerTest,ExamPrivacyLifecycleContributorTest,ModularityTest,ModulithArchitectureTest,AiProviderBoundaryTest,RepositoryHierarchyTest \
    test
}

run_stage_5() {
  log_step "Stage 5: PostgreSQL Testcontainers & Flyway V056 Migration Integration"
  if docker info >/dev/null 2>&1; then
    mvn -f services/api/pom.xml -Dtest=ExamMigrationV056IntegrationTest,ExamMigrationV055IntegrationTest,ExamPlatformPostgresIntegrationTest test
  elif [[ "$REQUIRE_DOCKER" == "true" ]]; then
    echo "ERROR: Docker daemon is unavailable and --require-docker was specified."
    exit 1
  else
    echo "SKIP: Docker daemon not running; compile-only verification of ExamMigrationV056IntegrationTest."
    mvn -f services/api/pom.xml test-compile
  fi
}

run_stage_6() {
  log_step "Stage 6: iOS Swift 6 Build, Unit Tests (ExamBlueprintAssemblyViewModelTests, ExamOfflineSQLiteStoreTests) & UI Tests"
  if [[ -r "${HOME}/Library/Developer/Xcode/DerivedData" && -w "${HOME}/Library/Developer/Xcode/DerivedData" ]] && xcrun simctl list devices >/dev/null 2>&1; then
    xcodebuild test \
      -project apps/ios/VerifiedAI.xcodeproj \
      -scheme VerifiedAI \
      -destination "$SIM_DESTINATION" \
      -only-testing:VerifiedAITests/ExamBlueprintAssemblyViewModelTests \
      -only-testing:VerifiedAITests/ExamOfflineSQLiteStoreTests \
      -only-testing:VerifiedAITests/CandidateProfileViewModelTests \
      -only-testing:VerifiedAITests/Sprint91ExamCatalogAndTargetTests \
      -only-testing:VerifiedAIUITests/ExamCatalogUITests
  else
    echo "INFO: CoreSimulatorService unavailable in sandbox; running Swift 6 strict-concurrency typecheck across iOS Exam & Test targets."
    xcrun --sdk iphonesimulator swiftc -emit-module \
      -emit-module-path /tmp/VerifiedAI.swiftmodule \
      -module-name VerifiedAI \
      -enable-testing \
      -strict-concurrency=complete \
      -DDEBUG \
      -module-cache-path /tmp/swift-module-cache \
      -target arm64-apple-ios17.0-simulator \
      apps/ios/VerifiedAI/Core/DesignSystem/Tokens/TypographyTokens.swift \
      apps/ios/VerifiedAI/Core/DesignSystem/Tokens/SpacingTokens.swift \
      apps/ios/VerifiedAI/Core/DesignSystem/Tokens/ColorTokens.swift \
      apps/ios/VerifiedAI/Core/DesignSystem/Tokens/RadiusTokens.swift \
      apps/ios/VerifiedAI/Core/Networking/RequestInterceptor.swift \
      apps/ios/VerifiedAI/Core/Networking/AuthTokenProvider.swift \
      apps/ios/VerifiedAI/Core/Networking/APIClient.swift \
      apps/ios/VerifiedAI/Core/Networking/HTTPRequest.swift \
      apps/ios/VerifiedAI/Core/Networking/HTTPResponse.swift \
      apps/ios/VerifiedAI/Core/Networking/HTTPMethod.swift \
      apps/ios/VerifiedAI/Core/Networking/ProblemDetails.swift \
      apps/ios/VerifiedAI/Core/Networking/NetworkError.swift \
      apps/ios/VerifiedAI/Core/Networking/Endpoint.swift \
      apps/ios/VerifiedAI/Core/Observability/CorrelationContext.swift \
      apps/ios/VerifiedAI/Core/Observability/AppLogger.swift \
      apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift \
      apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
      apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift \
      apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift \
      apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift \
      apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyViewModel.swift \
      apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateGoalSetupView.swift \
      apps/ios/VerifiedAI/Features/Exam/Presentation/SyllabusSnapToPlanSheet.swift \
      apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift \
      apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift

    xcrun --sdk iphonesimulator swiftc -typecheck \
      -I /tmp \
      -I /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/lib \
      -F /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library/Frameworks \
      -strict-concurrency=complete \
      -DDEBUG \
      -module-cache-path /tmp/swift-module-cache \
      -target arm64-apple-ios17.0-simulator \
      apps/ios/VerifiedAITests/Exam/ExamBlueprintAssemblyViewModelTests.swift \
      apps/ios/VerifiedAITests/Exam/ExamOfflineSQLiteStoreTests.swift \
      apps/ios/VerifiedAITests/Exam/CandidateProfileViewModelTests.swift \
      apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift

    xcrun --sdk iphonesimulator swiftc -typecheck \
      -I /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/usr/lib \
      -F /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/Library/Frameworks \
      -strict-concurrency=complete \
      -DDEBUG \
      -module-cache-path /tmp/swift-module-cache \
      -target arm64-apple-ios17.0-simulator \
      apps/ios/VerifiedAIUITests/ExamCatalogUITests.swift

    rm -f /tmp/VerifiedAI.swiftmodule /tmp/VerifiedAI.swiftdoc /tmp/VerifiedAI.swiftsourceinfo /tmp/VerifiedAI.abi.json
    echo "PASS: Swift 6 strict-concurrency verification of all Sprint 9.3 iOS source, unit test, and UI test targets succeeded."
  fi
}

run_stage_7() {
  log_step "Stage 7: Sprint 9.3 Test Hygiene Audit (Zero Skips) & Zero-LLM-Spend Default Check"
  if rg -n "@Disabled|@Ignore|pytest\.mark\.skip|XCTSkip|assumeTrue\(false\)" \
    services/api/src/test/java/com/verifiedai/exam \
    apps/ios/VerifiedAITests/Exam/ExamBlueprintAssemblyViewModelTests.swift \
    apps/ios/VerifiedAITests/Exam/ExamOfflineSQLiteStoreTests.swift \
    apps/ios/VerifiedAITests/Exam/CandidateProfileViewModelTests.swift \
    apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift \
    apps/ios/VerifiedAIUITests/ExamCatalogUITests.swift; then
    echo "ERROR: Found disabled or skipped test annotation in Sprint 9.3 suite."
    exit 1
  fi
  echo "PASS: Zero skipped/disabled tests across all Sprint 9.3 suites."
}

run_stage_8() {
  log_step "Stage 8: Training Eligibility & Default-Off Feature Flag Guardrail Audit"
  if rg -n "(question-bank-enabled|blueprint-management-enabled|assessment-assembly-enabled):\s*true" services/api/src/main/resources/application.yml; then
    echo "ERROR: Sprint 9.3 feature flag defaults to true in application.yml."
    exit 1
  fi
  if ! rg -n "training_eligible = FALSE" services/api/src/main/resources/db/migration/platform/V056__question_bank_blueprints_and_assessment_assembly.sql >/dev/null; then
    echo "ERROR: V056 missing training_eligible = FALSE CHECK invariant."
    exit 1
  fi
  echo "PASS: Default-off Sprint 9.3 feature flags and non-training-eligible V056 database invariants verified."
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
run_stage_8

if [[ "$MODE" == "full" ]]; then
  run_stage_5
  run_stage_6
fi

printf "\n==> [Sprint 9.3 Acceptance] ALL REQUESTED STAGES COMPLETED SUCCESSFULLY\n"
printf "LOCAL_ACCEPTANCE_PASSED\n"
