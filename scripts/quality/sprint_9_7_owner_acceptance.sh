#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
MODE=host
case "${1:---VerifyHost}" in
  --VerifyHost|--verify-host|--full) ;;
  --quick) MODE=local ;;
  *) echo 'Usage: sprint_9_7_owner_acceptance.sh [--VerifyHost|--quick]'; exit 2 ;;
esac
ARTIFACTS=services/api/target/sprint97/acceptance
mkdir -p "$ARTIFACTS"
PYTEST_PY=services/math-verifier/.venv/bin/python3
if [[ ! -x "$PYTEST_PY" ]]; then PYTEST_PY=python3; fi

printf '\n==> Stage 1: static contracts, V061, privacy, flat surfaces, Bridge B4\n'
for path in \
  services/api/src/main/resources/db/migration/platform/V061__sprint_9_7_post_assessment_attribution.sql \
  services/api/src/main/java/com/verifiedai/exam/api/ExamAttributionController.java \
  services/api/src/main/java/com/verifiedai/exam/application/ExamAttributionReportService.java \
  services/api/src/main/java/com/verifiedai/studyplan/rebalance/ExamAttributionStudyPlanListener.java \
  apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionView.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamRemediationDestination.swift \
  apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionViewModel.swift \
  apps/ios/VerifiedAITests/Exam/PostExamAttributionViewModelTests.swift \
  evaluations/tests/test_yks_phase13_clean_seed_export.py; do
  test -f "$path" || { echo "ERROR: Missing $path"; exit 1; }
done
if rg -n 'LinearGradient|RadialGradient|AngularGradient|MeshGradient' apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionView.swift; then
  echo 'ERROR: forbidden gradient'; exit 1
fi
plutil -lint apps/ios/VerifiedAI.xcodeproj/project.pbxproj
rg -q 'PostExamAttributionViewModelTests.swift in Sources' apps/ios/VerifiedAI.xcodeproj/project.pbxproj
rg -q 'PostExamRemediationDestination.swift in Sources' apps/ios/VerifiedAI.xcodeproj/project.pbxproj
if rg -n 'openURL|verifiedai://' apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionView.swift apps/ios/VerifiedAI/Features/Exam/Presentation/PostExamAttributionViewModel.swift; then
  echo 'ERROR: remediation must use native destinations'; exit 1
fi
python3 scripts/quality/validate_json_schemas.py
python3 scripts/quality/check_contracts.py
"$PYTEST_PY" evaluations/runners/export_yks_phase13_clean_seed.py
"$PYTEST_PY" -m pytest -q evaluations/tests/test_yks_phase13_clean_seed_export.py
python3 scripts/quality/docs_check.py

printf '\n==> Stage 2: Maven unit, SQL consistency, StudyPlan and Modulith gates\n'
mvn -o -f services/api/pom.xml test \
  -Dtest=ExamPostAssessmentAttributionServiceTest,ExamNetGainRoiSimulatorServiceTest,V061MigrationSqlConsistencyTest,ExamAttributionStudyPlanListenerTest,ModulithArchitectureTest

printf '\n==> Stage 3: Swift 6 parsing and model/API strict-concurrency typecheck\n'
bash scripts/quality/sprint_9_7_swift_check.sh
if [[ "$MODE" == local ]]; then
  echo 'LOCAL_CHECKS_PASSED_HOST_NOT_VERIFIED'
  exit 0
fi

printf '\n==> Required host gates: PostgreSQL integration and iOS XCTest execution\n'
failures=0
if docker info >"$ARTIFACTS/docker-preflight.log" 2>&1; then
  if ! mvn -o -f services/api/pom.xml test -Dtest=ExamAttributionIntegrationTest; then failures=$((failures+1)); fi
else
  echo "ERROR: PostgreSQL integration cannot run: Docker access unavailable ($ARTIFACTS/docker-preflight.log)."
  failures=$((failures+1))
fi
if xcrun simctl list devices available >"$ARTIFACTS/simulator-preflight.log" 2>&1; then
  if ! xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI \
    -derivedDataPath services/api/target/sprint97/DerivedData \
    -destination "${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}" \
    -only-testing:VerifiedAITests/PostExamAttributionViewModelTests; then failures=$((failures+1)); fi
else
  echo "ERROR: XCTest cannot run: CoreSimulator unavailable ($ARTIFACTS/simulator-preflight.log)."
  failures=$((failures+1))
fi
if [[ "$failures" -ne 0 ]]; then
  echo "HOST_ACCEPTANCE_FAILED: $failures required host gates did not pass."
  exit 1
fi
echo 'SPRINT_9_7_HOST_ACCEPTANCE_PASSED'
