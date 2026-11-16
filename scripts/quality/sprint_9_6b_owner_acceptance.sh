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
  printf "\n==> [Sprint 9.6B Acceptance] %s\n" "$1"
}

run_stage_1() {
  log_step "Stage 1: Concept B Guards, Deliverables, V060 SQL, SymPy Benchmark Gate, Docs & Quality Scripts"

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
    "services/api/src/main/resources/db/migration/platform/V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql" \
    "services/api/src/main/java/com/verifiedai/curriculum/application/StaticCurriculumTaxonomyCatalog.java" \
    "services/api/src/main/java/com/verifiedai/mistake/MistakeTaxonomy.java" \
    "services/api/src/main/java/com/verifiedai/exam/domain/DeterministicAssessmentAssemblyEngine.java" \
    "services/api/src/test/java/com/verifiedai/curriculum/application/StaticCurriculumTaxonomyCatalogTest.java" \
    "services/api/src/test/java/com/verifiedai/mistake/MistakeTaxonomyTest.java" \
    "services/api/src/test/java/com/verifiedai/exam/domain/ExamBlueprintValidatorTest.java" \
    "services/api/src/test/java/com/verifiedai/exam/persistence/V060MigrationSqlConsistencyTest.java" \
    "services/api/src/test/java/com/verifiedai/exam/integration/Sprint96BYksQuestionBankIntegrationTest.java" \
    "evaluations/golden-datasets/parsing/yks-tr-ingestion-v1/manifest.yaml" \
    "evaluations/golden-datasets/parsing/yks-tr-ingestion-v1/regression.jsonl" \
    "evaluations/golden-datasets/parsing/yks-tr-ingestion-v1/hard-tail.jsonl" \
    "evaluations/golden-datasets/solving/yks-tr-hard-v1/manifest.json" \
    "evaluations/golden-datasets/solving/yks-tr-hard-v1/cases.json" \
    "scripts/quality/validate_yks_tr_benchmarks.py" \
    "apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift" \
    "apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift" \
    "apps/ios/VerifiedAITests/Exam/ExamTurkishYksAccessibilityTests.swift" \
    "docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.6B_EXECUTION_REPORT.md" \
    "docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.6B_FILE_MANIFEST.md"; do
    if [[ ! -f "$required" ]]; then
      echo "ERROR: Missing Sprint 9.6B deliverable: $required"
      exit 1
    fi
  done
  echo "PASS: All Sprint 9.6B backend, SQL, benchmark, iOS, and documentation deliverables exist."

  plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj >/dev/null
  rg -q "ExamTurkishYksAccessibilityTests.swift in Sources" apps/ios/VerifiedAI.xcodeproj/project.pbxproj
  echo "PASS: project.pbxproj valid and ExamTurkishYksAccessibilityTests.swift registered in build phases."

  V060_SQL="services/api/src/main/resources/db/migration/platform/V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql"
  rg -qF "superseded_by_version = '2026.2'" "$V060_SQL"
  rg -qF "BP-TYT-MATH-WORD-PROBLEMS-V2" "$V060_SQL"
  rg -qF "BP-TYT-MATH-FULL-SIM-2026-V2" "$V060_SQL"
  rg -qF "BP-AYT-MATH-DIAG-2026-V2" "$V060_SQL"
  rg -qF "BP-AYT-MATH-LTI-DRILL-2026-V2" "$V060_SQL"
  rg -qF "BP-AYT-MATH-FULL-SIM-2026-V2" "$V060_SQL"
  echo "PASS: V060 2026.2 transition and 5 V2 blueprints present."

  "$PYTHON_BIN" scripts/quality/validate_yks_tr_benchmarks.py
  "$PYTHON_BIN" scripts/quality/docs_check.py
  "$PYTHON_BIN" scripts/quality/validate_json_schemas.py
  "$PYTHON_BIN" scripts/quality/check_contracts.py
  "$PYTHON_BIN" scripts/quality/check_prompt_immutability.py --base-ref HEAD
  bash scripts/quality/secret_scan.sh

  VENV_PY="services/math-verifier/.venv/bin/python3"
  if "$PYTHON_BIN" -c "import jsonschema" >/dev/null 2>&1; then
    "$PYTHON_BIN" evaluations/runners/solver_evaluation.py --mode local
  elif [[ -x "$VENV_PY" ]] && "$VENV_PY" -c "import jsonschema" >/dev/null 2>&1; then
    "$VENV_PY" evaluations/runners/solver_evaluation.py --mode local
  else
    echo "SKIP: jsonschema module not accessible in sandbox python; run 'services/math-verifier/.venv/bin/python3 evaluations/runners/solver_evaluation.py --mode local' on host."
  fi
}

run_stage_2() {
  log_step "Stage 2: Swift 6 Syntax Parse, Module Emit & Strict Concurrency Typecheck"
  rm -rf /tmp/s96b-swift /tmp/swift-module-cache
  mkdir -p /tmp/s96b-swift /tmp/swift-module-cache

  xcrun swiftc -parse \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift \
    apps/ios/VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift \
    apps/ios/VerifiedAITests/Exam/ExamTurkishYksAccessibilityTests.swift

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
    -emit-module-path /tmp/s96b-swift/VerifiedAI.swiftmodule \
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
    -I /tmp/s96b-swift \
    -I "${SIM_PLATFORM}/Developer/usr/lib" \
    -F "${SIM_PLATFORM}/Developer/Library/Frameworks" \
    -module-cache-path /tmp/swift-module-cache \
    -target arm64-apple-ios17.0-simulator \
    apps/ios/VerifiedAITests/Exam/ExamSessionViewModelTests.swift \
    apps/ios/VerifiedAITests/Exam/ExamTurkishYksAccessibilityTests.swift

  echo "PASS: Swift 6 syntax and strict-concurrency checks passed (production + AX3 accessibility tests)."
}

run_stage_2b() {
  log_step "Stage 2B: Sprint 9.6B Backend Taxonomy, Validator, V060 SQL & Architecture Tests"
  mvn -f services/api/pom.xml test -Dtest=StaticCurriculumTaxonomyCatalogTest,MistakeTaxonomyTest,AttemptMistakeClassifierTest,ExamBlueprintValidatorTest,ExamBlueprintValidatorAndScoringTest,V054SeedSqlConsistencyTest,V056MigrationSqlConsistencyTest,V060MigrationSqlConsistencyTest,DeterministicAssessmentAssemblyEngineTest,ModulithArchitectureTest,AiProviderBoundaryTest,RepositoryHierarchyTest
}

run_stage_3() {
  log_step "Stage 3: Full Host PostgreSQL Testcontainers (V001..V060) & iOS Simulator Verification (VERIFY_HOST=${VERIFY_HOST})"
  if [[ "$MODE" == "quick" ]]; then
    echo "SKIP: Stage 3 skipped in --quick mode."
    return 0
  fi

  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    mvn -f services/api/pom.xml test -Dtest=Sprint96BYksQuestionBankIntegrationTest,ExamPlatformPostgresIntegrationTest,ExamMigrationV055IntegrationTest,ExamMigrationV056IntegrationTest
  else
    echo "SKIP: Docker daemon unavailable in sandbox; run 'mvn -f services/api/pom.xml test -Dtest=Sprint96BYksQuestionBankIntegrationTest,ExamPlatformPostgresIntegrationTest,ExamMigrationV055IntegrationTest,ExamMigrationV056IntegrationTest' on host Mac with Docker running."
  fi

  if [[ -r "${HOME}/Library/Developer/Xcode/DerivedData" && -w "${HOME}/Library/Developer/Xcode/DerivedData" ]] && xcrun simctl list devices >/dev/null 2>&1; then
    xcodebuild test \
      -project apps/ios/VerifiedAI.xcodeproj \
      -scheme VerifiedAI \
      -destination "$SIM_DESTINATION" \
      -only-testing:VerifiedAITests/ExamTurkishYksAccessibilityTests \
      -only-testing:VerifiedAITests/ExamSessionViewModelTests
  else
    echo "SKIP: CoreSimulatorService unavailable in sandbox; run this script on host Mac to execute Xcode Simulator tests."
  fi
}

run_stage_1
run_stage_2
run_stage_2b
run_stage_3
echo "LOCAL_ACCEPTANCE_PASSED"
