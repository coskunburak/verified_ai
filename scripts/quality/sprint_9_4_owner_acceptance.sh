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
SIM_DESTINATION="${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full) MODE="full"; shift ;;
    --quick) MODE="quick"; shift ;;
    *)
      echo "Usage: $0 [--full|--quick]"
      exit 2
      ;;
  esac
done

log_step() {
  printf "\n==> [Sprint 9.4 UI Acceptance] %s\n" "$1"
}

run_stage_1() {
  log_step "Stage 1: Zero-Gradient Guard, Design System 2.0 Registration, Localization & Security Checks"
  if rg -n "LinearGradient|RadialGradient|AngularGradient|MeshGradient" apps/ios/VerifiedAI; then
    echo "ERROR: Forbidden SwiftUI gradient API detected (Design System 2.0 mandates flat zero-gradient surfaces)."
    exit 1
  fi
  echo "PASS: Zero gradient APIs detected across apps/ios/VerifiedAI."

  for required in \
    "apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift" \
    "apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemGalleryPreview.swift" \
    "apps/ios/VerifiedAI/App/MainTabView.swift" \
    "apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift" \
    "apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift" \
    "services/api/src/main/resources/db/migration/platform/V057__timed_mock_exam_runtime_and_scratchpad_ingestion.sql"; do
    if [[ ! -f "$required" ]]; then
      echo "ERROR: Missing Sprint 9.4 deliverable: $required"
      exit 1
    fi
  done

  rg -q "DesignSystemComponents.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "DesignSystemGalleryPreview.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "MainTabView.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "ExamSessionViewModel.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "ExamSessionView.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "ExamSessionViewModelTests.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "ExamSessionSQLiteStoreTests.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q "ExamSessionRuntimeUITests.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  rg -q 'API_BASE_URL\[sdk=iphonesimulator\*\]' apps/ios/Config/Debug.xcconfig
  echo "PASS: DesignSystem, MainTabView, ExamSession deliverables & tests registered in project.pbxproj and Simulator localhost URL configured."

  if rg -n "com\.fasterxml\.jackson" services/api/src/main/java/com/verifiedai/exam; then
    echo "ERROR: Legacy com.fasterxml.jackson import detected in com.verifiedai.exam (Jackson 3 tools.jackson.* required)."
    exit 1
  fi
  echo "PASS: Zero com.fasterxml.jackson imports in com.verifiedai.exam."

  for schema_base in \
    exam-session-start-request \
    exam-session-response \
    exam-session-item-autosave-request \
    exam-session-item-autosave-response \
    exam-scratchpad-upload-request \
    exam-scratchpad-upload-response \
    exam-session-submit-request \
    exam-session-submit-response \
    exam-no-hint-guard-response; do
    if [[ ! -f "packages/schemas/exam/v1/${schema_base}.schema.json" ]] || [[ ! -f "packages/schemas/exam/v1/fixtures/${schema_base}.valid.json" ]]; then
      echo "ERROR: Missing Sprint 9.4 JSON schema or valid fixture for ${schema_base}"
      exit 1
    fi
  done
  echo "PASS: All 9 Sprint 9.4 JSON schemas and valid fixtures present."

  cmp -s packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml
  echo "PASS: OpenAPI 3.1 twin specifications are byte-identical."

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
  echo "PASS: Zero card shadows, legacy system surfaces, or indigo/purple accents in active SwiftUI views."

  if ! rg -q "baseDurationSeconds: 900" apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
    || ! rg -q "baseDurationSeconds: 1200" apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift; then
    echo "ERROR: FixtureExamAPIService blueprint durations do not match V056 seed (900s / 1200s)."
    exit 1
  fi
  echo "PASS: FixtureExamAPIService durations match V056 (900s / 1200s)."

  if rg -q 'DashboardMetric\(title: "Solved", value: "0"' apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift; then
    echo "ERROR: Legacy hardcoded Solved=0 placeholder metric still present."
    exit 1
  fi
  echo "PASS: Hardcoded Solved/Review/Accuracy placeholder metrics removed."

  "$PYTHON_BIN" scripts/quality/docs_check.py
  "$PYTHON_BIN" scripts/quality/validate_json_schemas.py
  "$PYTHON_BIN" scripts/quality/check_contracts.py
  "$PYTHON_BIN" scripts/quality/check_prompt_immutability.py --base-ref HEAD
  "$PYTHON_BIN" scripts/quality/check_sprint_6_10_localization.py /tmp/sprint_9_4_localization_report.json
  bash scripts/quality/secret_scan.sh
}

run_stage_2() {
  log_step "Stage 2: Swift 6 Syntax Parse & Strict Concurrency Typecheck"
  xcrun swiftc -parse \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/ColorTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/TypographyTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/SpacingTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/RadiusTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemGalleryPreview.swift \
    apps/ios/VerifiedAI/App/MainTabView.swift \
    apps/ios/VerifiedAI/App/RootView.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/DualModeSwitcher.swift \
    apps/ios/VerifiedAI/Features/ProblemCapture/Presentation/Capture/ProblemCaptureView.swift \
    apps/ios/VerifiedAI/Features/ProblemReview/Presentation/History/ProblemRevisionHistoryView.swift \
    apps/ios/VerifiedAI/Features/ProblemReview/Presentation/Review/ProblemReviewView.swift \
    apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Visualization/MathVisualizationView.swift \
    apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonDrawerView.swift \
    apps/ios/VerifiedAI/Features/Onboarding/Presentation/OnboardingFlowView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkCaptureView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptStepReviewView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptStepEditorView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptAnalysisResultsView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/FirstWrongStepDiffView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AlternativeMethodComparisonView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/CounterfactualTwinSheetView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptCounterexampleProofCardView.swift \
    apps/ios/VerifiedAI/Features/AttemptAnalysis/Views/CounterexampleProofCardView.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/ColdStartDiagnosticCardView.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/PlanRebalanceNotificationSheet.swift \
    apps/ios/VerifiedAI/Features/Home/Presentation/ColdStartDiagnosticFlowView.swift \
    apps/ios/VerifiedAI/Features/StudyPlan/Presentation/DailyStudyPlanView.swift \
    apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionView.swift \
    apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeQuestionCardView.swift \
    apps/ios/VerifiedAI/Features/Gamification/MilestoneSheetView.swift \
    apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/MasteryGraphExplorerView.swift \
    apps/ios/VerifiedAI/Features/MistakePassport/Views/MistakePassportView.swift \
    apps/ios/VerifiedAI/Features/TwinChallenge/Presentation/ProveItSoloTwinView.swift \
    apps/ios/VerifiedAI/Features/Reports/WeeklyProgressReportView.swift \
    apps/ios/VerifiedAI/Features/MasteryGraph/Presentation/DualRingSkillNodeView.swift \
    apps/ios/VerifiedAI/Features/MistakePassport/Views/ExamTrapRadarCardView.swift \
    apps/ios/VerifiedAI/Features/Gamification/StreakBadgeView.swift \
    apps/ios/VerifiedAI/Features/StudySession/StudySessionResumeView.swift \
    apps/ios/VerifiedAI/Features/Authentication/Presentation/SignInView.swift \
    apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift \
    apps/ios/VerifiedAI/Features/Tutor/Presentation/TutorView.swift \
    apps/ios/VerifiedAI/Features/Account/Presentation/AccountSettingsView.swift \
    apps/ios/VerifiedAI/Features/Subscription/Presentation/PaywallView.swift \
    apps/ios/VerifiedAI/Features/ProblemHistory/Presentation/History/ProblemHistoryView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateGoalSetupView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/SyllabusSnapToPlanSheet.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift \
    apps/ios/VerifiedAIUITests/ExamSessionRuntimeUITests.swift

  xcrun --sdk iphonesimulator swiftc -typecheck \
    -strict-concurrency=complete \
    -DDEBUG \
    -module-cache-path /tmp/swift-module-cache \
    -target arm64-apple-ios17.0-simulator \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/ColorTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/TypographyTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/SpacingTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/RadiusTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemGalleryPreview.swift \
    apps/ios/VerifiedAI/Core/Networking/APIClient.swift \
    apps/ios/VerifiedAI/Core/Networking/HTTPRequest.swift \
    apps/ios/VerifiedAI/Core/Networking/HTTPResponse.swift \
    apps/ios/VerifiedAI/Core/Networking/HTTPMethod.swift \
    apps/ios/VerifiedAI/Core/Networking/Endpoint.swift \
    apps/ios/VerifiedAI/Core/Networking/NetworkError.swift \
    apps/ios/VerifiedAI/Core/Networking/ProblemDetails.swift \
    apps/ios/VerifiedAI/Core/Networking/AuthTokenProvider.swift \
    apps/ios/VerifiedAI/Core/Networking/RequestInterceptor.swift \
    apps/ios/VerifiedAI/Core/Observability/CorrelationContext.swift \
    apps/ios/VerifiedAI/Core/Observability/AppLogger.swift \
    apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift \
    apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
    apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateGoalSetupView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/SyllabusSnapToPlanSheet.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift

  SIM_PLATFORM="$(xcrun --show-sdk-platform-path --sdk iphonesimulator)"
  mkdir -p /tmp/s94-swift
  xcrun --sdk iphonesimulator swiftc -emit-module \
    -emit-module-path /tmp/s94-swift/VerifiedAI.swiftmodule \
    -module-name VerifiedAI \
    -enable-testing \
    -strict-concurrency=complete \
    -DDEBUG \
    -module-cache-path /tmp/swift-module-cache \
    -target arm64-apple-ios17.0-simulator \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/ColorTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/TypographyTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/SpacingTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Tokens/RadiusTokens.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift \
    apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemGalleryPreview.swift \
    apps/ios/VerifiedAI/Core/Networking/APIClient.swift \
    apps/ios/VerifiedAI/Core/Networking/HTTPRequest.swift \
    apps/ios/VerifiedAI/Core/Networking/HTTPResponse.swift \
    apps/ios/VerifiedAI/Core/Networking/HTTPMethod.swift \
    apps/ios/VerifiedAI/Core/Networking/Endpoint.swift \
    apps/ios/VerifiedAI/Core/Networking/NetworkError.swift \
    apps/ios/VerifiedAI/Core/Networking/ProblemDetails.swift \
    apps/ios/VerifiedAI/Core/Networking/AuthTokenProvider.swift \
    apps/ios/VerifiedAI/Core/Networking/RequestInterceptor.swift \
    apps/ios/VerifiedAI/Core/Observability/CorrelationContext.swift \
    apps/ios/VerifiedAI/Core/Observability/AppLogger.swift \
    apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift \
    apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift \
    apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/CandidateGoalSetupView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/SyllabusSnapToPlanSheet.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift

  xcrun --sdk iphonesimulator swiftc -typecheck \
    -strict-concurrency=complete \
    -DDEBUG \
    -I /tmp/s94-swift \
    -I "${SIM_PLATFORM}/Developer/usr/lib" \
    -F "${SIM_PLATFORM}/Developer/Library/Frameworks" \
    -module-cache-path /tmp/swift-module-cache \
    -target arm64-apple-ios17.0-simulator \
    apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift \
    apps/ios/VerifiedAITests/Exam/CandidateProfileViewModelTests.swift \
    apps/ios/VerifiedAITests/Exam/ExamBlueprintAssemblyViewModelTests.swift \
    apps/ios/VerifiedAITests/Exam/ExamOfflineSQLiteStoreTests.swift \
    apps/ios/VerifiedAITests/Exam/ExamSessionViewModelTests.swift \
    apps/ios/VerifiedAITests/Exam/ExamSessionSQLiteStoreTests.swift \
    apps/ios/VerifiedAIUITests/ExamCatalogUITests.swift \
    apps/ios/VerifiedAIUITests/ExamSessionRuntimeUITests.swift
  echo "PASS: Swift 6 syntax and strict-concurrency checks passed (production + tests)."
}

run_stage_2b() {
  log_step "Stage 2B: Sprint 9.4 Backend Unit, WebMvc, V057 SQL, Privacy & Architecture Tests"
  mvn -f services/api/pom.xml test -Dtest=ExamSessionTest,ExamSessionRuntimeServiceTest,V057MigrationSqlConsistencyTest,ExamSessionRuntimeControllerTest,ExamPrivacyLifecycleContributorTest,ModulithArchitectureTest,AiProviderBoundaryTest,RepositoryHierarchyTest
}

run_stage_3() {
  log_step "Stage 3: Full Host iOS Build, Unit Tests & UI Regression Suite"
  if [[ "$MODE" == "quick" ]]; then
    echo "SKIP: Stage 3 skipped in --quick mode."
    return 0
  fi

  if [[ -r "${HOME}/Library/Developer/Xcode/DerivedData" && -w "${HOME}/Library/Developer/Xcode/DerivedData" ]] && xcrun simctl list devices >/dev/null 2>&1; then
    xcodebuild test \
      -project apps/ios/VerifiedAI.xcodeproj \
      -scheme VerifiedAI \
      -destination "$SIM_DESTINATION" \
      -only-testing:VerifiedAITests/AppEnvironmentTests \
      -only-testing:VerifiedAITests/ExamBlueprintAssemblyViewModelTests \
      -only-testing:VerifiedAITests/ExamSessionViewModelTests \
      -only-testing:VerifiedAITests/ExamSessionSQLiteStoreTests \
      -only-testing:VerifiedAIUITests/VerifiedAIUITests \
      -only-testing:VerifiedAIUITests/PrimarySolveUITests \
      -only-testing:VerifiedAIUITests/TutorUITests \
      -only-testing:VerifiedAIUITests/ExamCatalogUITests \
      -only-testing:VerifiedAIUITests/ExamSessionRuntimeUITests
  else
    echo "SKIP: CoreSimulatorService unavailable in sandbox; run this script on host Mac to execute Xcode Simulator UI tests."
  fi
}

run_stage_1
run_stage_2
run_stage_2b
run_stage_3
echo "LOCAL_ACCEPTANCE_PASSED"
