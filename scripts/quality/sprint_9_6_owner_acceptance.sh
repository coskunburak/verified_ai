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
VERIFY_HOST="false"
SIM_DESTINATION="${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full) MODE="full"; shift ;;
    --quick) MODE="quick"; shift ;;
    --VerifyHost|--verify-host) MODE="full"; VERIFY_HOST="true"; shift ;;
    *)
      echo "Usage: $0 [--full|--quick|--VerifyHost]"
      exit 2
      ;;
  esac
done

log_step() {
  printf "\n==> [Sprint 9.6 Acceptance] %s\n" "$1"
}

run_stage_1() {
  log_step "Stage 1: Concept B Guards, Deliverables, V059 SQL, Jackson 3, Contracts & Quality Scripts"

  if rg -n "LinearGradient|RadialGradient|AngularGradient|MeshGradient" apps/ios/VerifiedAI; then
    echo "ERROR: Forbidden SwiftUI gradient API detected (Design System 2.0 mandates flat zero-gradient surfaces)."
    exit 1
  fi
  echo "PASS: Zero gradient APIs across apps/ios/VerifiedAI."

  if rg -n "UIColor\.system|UIColor\.secondary|Color\.indigo|Color\.purple|\.tint\(\.indigo\)|\.tint\(\.purple\)|\.shadow\(|systemBackground|secondarySystemBackground|systemGroupedBackground" \
    apps/ios/VerifiedAI/App \
    apps/ios/VerifiedAI/Core/DesignSystem \
    apps/ios/VerifiedAI/Features \
    --glob '!**/TutorSharePacketPDFRenderer.swift' \
    --glob '!**/Features/StudyPlan/SpacedReview/**' \
    --glob '!**/Features/Home/Views/CoachHubView.swift'; then
    echo "ERROR: Forbidden legacy shadow, system UIKit surface, or indigo/purple accent detected in active SwiftUI views."
    exit 1
  fi
  echo "PASS: Concept B Design System 2.0 surface and palette checks passed."

  for required in \
    "services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql" \
    "services/api/src/main/java/com/verifiedai/exam/config/ExamProperties.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessConfidenceTier.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessRationaleCode.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/ActiveTrapImpactItem.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/SectionReadinessBreakdown.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessTopicAllocation.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessProjection.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessRepository.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessCalculator.java" \
    "services/api/src/main/java/com/verifiedai/exam/events/ExamReadinessComputedEvent.java" \
    "services/api/src/main/java/com/verifiedai/exam/application/ExamReadinessSignalReader.java" \
    "services/api/src/main/java/com/verifiedai/exam/application/ExamReadinessProjectionService.java" \
    "services/api/src/main/java/com/verifiedai/exam/infrastructure/persistence/JdbcExamReadinessRepository.java" \
    "services/api/src/main/java/com/verifiedai/exam/api/ExamReadinessResponse.java" \
    "services/api/src/main/java/com/verifiedai/exam/api/ExamReadinessController.java" \
    "services/api/src/test/java/com/verifiedai/exam/domain/ExamReadinessCalculatorTest.java" \
    "services/api/src/test/java/com/verifiedai/exam/persistence/V059MigrationSqlConsistencyTest.java" \
    "services/api/src/test/java/com/verifiedai/exam/integration/ExamReadinessMigrationAndRepositoryTest.java" \
    "services/api/src/test/java/com/verifiedai/exam/api/ExamReadinessControllerWebMvcTest.java" \
    "services/api/src/test/java/com/verifiedai/exam/infrastructure/privacy/ExamReadinessPrivacyLifecycleContributorTest.java" \
    "apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessViewModel.swift" \
    "apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift" \
    "apps/ios/VerifiedAITests/Exam/Sprint96ExamReadinessTests.swift" \
    "apps/ios/VerifiedAIUITests/ExamReadinessHubUITests.swift"; do
    if [[ ! -f "$required" ]]; then
      echo "ERROR: Missing Sprint 9.6 deliverable: $required"
      exit 1
    fi
  done
  echo "PASS: All Sprint 9.6 backend, SQL, iOS, and test deliverables exist."

  plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj >/dev/null
  rg -q "ExamReadinessViewModel.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "ExamReadinessHubView.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "Sprint96ExamReadinessTests.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "ExamReadinessHubUITests.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  echo "PASS: project.pbxproj valid and Sprint 9.6 files registered in build phases."

  rg -qF "CREATE TABLE IF NOT EXISTS exam_readiness_projections" services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql
  rg -qF "CREATE TABLE IF NOT EXISTS exam_readiness_topic_allocations" services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql
  rg -qF "chk_exam_readiness_preliminary_guard" services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql
  rg -qF "training_eligible = FALSE" services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql
  rg -qF "llm_tokens_used = 0" services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql
  rg -qF "inference_cost_usd = 0.000000" services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql
  rg -qF 'readiness-enabled: ${VERIFIEDAI_EXAM_READINESS_ENABLED:false}' services/api/src/main/resources/application.yml
  echo "PASS: V059 CHECK constraints and ADR-011 default=false feature flag verified."

  if rg -n "com\.fasterxml\.jackson" services/api/src/main/java/com/verifiedai/exam; then
    echo "ERROR: Legacy com.fasterxml.jackson import detected in com.verifiedai.exam (Jackson 3 tools.jackson.* required)."
    exit 1
  fi
  echo "PASS: Zero com.fasterxml.jackson imports in com.verifiedai.exam."

  for req_file in \
    "packages/schemas/exam/v1/exam-readiness-projection.schema.json" \
    "packages/schemas/exam/v1/exam-readiness-recompute-request.schema.json" \
    "packages/schemas/exam/v1/fixtures/exam-readiness-projection-valid.json" \
    "packages/schemas/exam/v1/fixtures/exam-readiness-projection-preliminary-valid.json" \
    "packages/schemas/exam/v1/fixtures/exam-readiness-recompute-request-valid.json"; do
    if [[ ! -f "$req_file" ]]; then
      echo "ERROR: Missing Sprint 9.6 schema/fixture: $req_file"
      exit 1
    fi
  done
  cmp -s packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml
  echo "PASS: Sprint 9.6 JSON schemas, fixtures, and byte-identical OpenAPI 3.1 twins verified."

  rg -q '"exam\.readiness\.screen"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
  rg -q '"exam\.readiness\.intervalCard"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
  rg -q '"exam\.readiness\.preliminaryBanner"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
  rg -q '"exam\.readiness\.trapsCard"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
  rg -q '"exam\.readiness\.simulatorCard"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
  rg -q '"exam\.readiness\.roiList"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
  rg -q '"exam\.hub\.openReadinessButton"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift
  rg -q '"exam\.score\.openReadinessButton"' apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift
  rg -q '"home\.examReadiness\.button"' apps/ios/VerifiedAI/App/MainTabView.swift
  echo "PASS: Sprint 9.6 accessibility identifiers and navigation entry points verified."

  "$PYTHON_BIN" scripts/quality/docs_check.py
  "$PYTHON_BIN" scripts/quality/validate_json_schemas.py
  "$PYTHON_BIN" scripts/quality/check_contracts.py
  "$PYTHON_BIN" scripts/quality/check_prompt_immutability.py --base-ref HEAD
  "$PYTHON_BIN" scripts/quality/check_sprint_6_10_localization.py /tmp/sprint_9_6_localization_report.json
  bash scripts/quality/secret_scan.sh
}

run_stage_2() {
  log_step "Stage 2: Swift 6 Syntax Parse, Module Emit & Strict Concurrency Typecheck"
  rm -rf /tmp/s96-swift /tmp/swift-module-cache
  mkdir -p /tmp/s96-swift /tmp/swift-module-cache

  xcrun swiftc -parse \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/ColorTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/TypographyTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/SpacingTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/RadiusTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift \
    apps/ios/VerifiedAI/App/AppDependencies.swift \
    apps/ios/VerifiedAI/App/MainTabView.swift \
    apps/ios/VerifiedAI/App/RootView.swift \
    apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift \
    apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
    apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift \
    apps/ios/VerifiedAITests/Exam/Sprint96ExamReadinessTests.swift \
    apps/ios/VerifiedAIUITests/ExamReadinessHubUITests.swift

  SIM_PLATFORM="$(xcrun --show-sdk-platform-path --sdk iphonesimulator)"
  CORE_EXAM_SOURCES=(
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/ColorTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/TypographyTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/SpacingTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/RadiusTokens.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemGalleryPreview.swift
    apps/ios/VerifiedAI/Core/Networking/APIClient.swift
    apps/ios/VerifiedAI/Core/Networking/HTTPRequest.swift
    apps/ios/VerifiedAI/Core/Networking/HTTPResponse.swift
    apps/ios/VerifiedAI/Core/Networking/HTTPMethod.swift
    apps/ios/VerifiedAI/Core/Networking/Endpoint.swift
    apps/ios/VerifiedAI/Core/Networking/NetworkError.swift
    apps/ios/VerifiedAI/Core/Networking/ProblemDetails.swift
    apps/ios/VerifiedAI/Core/Networking/AuthTokenProvider.swift
    apps/ios/VerifiedAI/Core/Networking/RequestInterceptor.swift
    apps/ios/VerifiedAI/Core/Observability/CorrelationContext.swift
    apps/ios/VerifiedAI/Core/Observability/AppLogger.swift
    apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift
    apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift
    apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateGoalSetupView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/SyllabusSnapToPlanSheet.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessViewModel.swift
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift
  )

  xcrun --sdk iphonesimulator swiftc -typecheck \
    -strict-concurrency=complete \
    -DDEBUG \
    -module-cache-path /tmp/swift-module-cache \
    -target arm64-apple-ios17.0-simulator \
    "${CORE_EXAM_SOURCES[@]}"

  xcrun --sdk iphonesimulator swiftc -emit-module \
    -emit-module-path /tmp/s96-swift/VerifiedAI.swiftmodule \
    -module-name VerifiedAI \
    -enable-testing \
    -strict-concurrency=complete \
    -DDEBUG \
    -module-cache-path /tmp/swift-module-cache \
    -target arm64-apple-ios17.0-simulator \
    "${CORE_EXAM_SOURCES[@]}"

  xcrun --sdk iphonesimulator swiftc -typecheck \
    -strict-concurrency=complete \
    -DDEBUG \
    -I /tmp/s96-swift \
    -I "${SIM_PLATFORM}/Developer/usr/lib" \
    -F "${SIM_PLATFORM}/Developer/Library/Frameworks" \
    -module-cache-path /tmp/swift-module-cache \
    -target arm64-apple-ios17.0-simulator \
    apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift \
    apps/ios/VerifiedAITests/Exam/Sprint95ExamScoringAndEvidenceTests.swift \
    apps/ios/VerifiedAITests/Exam/Sprint96ExamReadinessTests.swift \
    apps/ios/VerifiedAIUITests/ExamCatalogUITests.swift \
    apps/ios/VerifiedAIUITests/ExamSessionRuntimeUITests.swift \
    apps/ios/VerifiedAIUITests/ExamReadinessHubUITests.swift

  echo "PASS: Swift 6 syntax and strict-concurrency checks passed (production + unit/UI tests)."
}

run_stage_2b() {
  log_step "Stage 2B: Sprint 9.6 Backend Unit, WebMvc, V059 SQL, Privacy & Architecture Tests"
  mvn -f services/api/pom.xml test -Dtest=ExamReadinessCalculatorTest,V059MigrationSqlConsistencyTest,ExamReadinessControllerWebMvcTest,ExamPrivacyLifecycleContributorTest,ExamReadinessPrivacyLifecycleContributorTest,ModulithArchitectureTest,AiProviderBoundaryTest,RepositoryHierarchyTest
}

run_stage_3() {
  log_step "Stage 3: Full Host PostgreSQL Testcontainers & iOS Simulator Verification (VERIFY_HOST=${VERIFY_HOST})"
  if [[ "$MODE" == "quick" ]]; then
    echo "SKIP: Stage 3 skipped in --quick mode."
    return 0
  fi

  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    mvn -f services/api/pom.xml test -Dtest=ExamReadinessMigrationAndRepositoryTest
  else
    echo "SKIP: Docker daemon unavailable in sandbox; run 'mvn -f services/api/pom.xml test -Dtest=ExamReadinessMigrationAndRepositoryTest' on host Mac with Docker running."
  fi

  if [[ -r "${HOME}/Library/Developer/Xcode/DerivedData" && -w "${HOME}/Library/Developer/Xcode/DerivedData" ]] && xcrun simctl list devices >/dev/null 2>&1; then
    xcodebuild test \
      -project apps/ios/VerifiedAI.xcodeproj \
      -scheme VerifiedAI \
      -destination "$SIM_DESTINATION" \
      -only-testing:VerifiedAITests/Sprint96ExamReadinessTests \
      -only-testing:VerifiedAIUITests/ExamReadinessHubUITests
  else
    echo "SKIP: CoreSimulatorService unavailable in sandbox; run this script on host Mac to execute Xcode Simulator tests."
  fi
}

run_stage_1
run_stage_2
run_stage_2b
run_stage_3
echo "LOCAL_ACCEPTANCE_PASSED"
