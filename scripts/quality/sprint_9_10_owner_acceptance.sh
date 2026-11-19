#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."

case "${1:---VerifyHost}" in
  --VerifyHost|--verify-host|--full) ;;
  *)
    echo 'Usage: sprint_9_10_owner_acceptance.sh --VerifyHost'
    exit 2
    ;;
esac

RUN_ID="s910-$(date -u +%Y%m%dT%H%M%SZ)"
COMMIT_SHA="$(git rev-parse HEAD 2>/dev/null || echo 'UNKNOWN')"
REPORT_DIR=".generated/sprint-9-10-owner/$RUN_ID"
mkdir -p "$REPORT_DIR"

echo "[1/7] Validating Sprint 9.10 contracts, JSON Schemas, and OpenAPI mirrors..."
python3 scripts/quality/validate_sprint_9_10_contracts.py | tee "$REPORT_DIR/contracts_9_10.log"
python3 scripts/quality/validate_json_schemas.py | tee "$REPORT_DIR/json_schemas.log"
python3 scripts/quality/check_contracts.py | tee "$REPORT_DIR/check_contracts.log"

echo "[2/7] Validating canonical Markdown docs and manifest..."
python3 scripts/quality/docs_check.py | tee "$REPORT_DIR/docs_check.log"

echo "[3/7] Running backend Java unit and Modulith architecture tests..."
./mvnw -f services/api/pom.xml \
  -Dtest=ExamIntegrityCanonicalizerTest,AssessmentIntegrityGuardTest,AdminExamQualityGateControllerTest,ExamSessionRuntimeServiceTest,ExamSessionRuntimeControllerTest,ExamPrivacyLifecycleContributorTest,ExamAnalyticsPrivacyLifecycleIntegrationTest,ModulithArchitectureTest \
  test | tee "$REPORT_DIR/maven_unit_modulith.log"

echo "[4/7] Running backend PostgreSQL/Flyway V054..V064 & privacy integration tests..."
./mvnw -f services/api/pom.xml \
  -Dtest=ExamIntegrityAuditRepositoryIntegrationTest,ExamSessionMigrationAndRepositoryTest \
  test | tee "$REPORT_DIR/maven_postgres_it.log"

echo "[5/7] Running Sprint 9.10 iOS static, localization, Concept B & pbxproj checks..."
bash scripts/quality/sprint_9_10_swift_check.sh | tee "$REPORT_DIR/swift_static.log"

echo "[6/7] Running iOS XCTest suite (50 spoken formulas, 320pt/AX3 VoiceOver, durable intent recovery)..."
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -destination "${IOS_SIM_DESTINATION:-platform=iOS Simulator,name=iPhone 16 Pro}" \
  -derivedDataPath services/api/target/sprint910/DerivedData \
  -only-testing:VerifiedAITests/SpokenMathTurkishAccessibilityFormatterTests \
  -only-testing:VerifiedAITests/ExamTurkishYksAccessibilityTests \
  -only-testing:VerifiedAITests/ExamSessionViewModelTests \
  -only-testing:VerifiedAITests/ExamSessionSQLiteStoreTests \
  | tee "$REPORT_DIR/xcode_test.log"

echo "[7/7] Recording local run metadata (never fabricates device/staging sign-off)..."
cat > "$REPORT_DIR/local-run-metadata.json" <<EOF
{
  "runId": "$RUN_ID",
  "commitSha": "$COMMIT_SHA",
  "localAutomatedGatesPassed": true,
  "pendingGates": [
    "PENDING_OWNER_DEVICE_EVIDENCE",
    "PENDING_STAGING_LATENCY_EVIDENCE"
  ],
  "logs": {
    "contracts": "$REPORT_DIR/contracts_9_10.log",
    "schemas": "$REPORT_DIR/json_schemas.log",
    "docs": "$REPORT_DIR/docs_check.log",
    "mavenUnit": "$REPORT_DIR/maven_unit_modulith.log",
    "mavenPostgres": "$REPORT_DIR/maven_postgres_it.log",
    "swiftStatic": "$REPORT_DIR/swift_static.log",
    "xcodeTest": "$REPORT_DIR/xcode_test.log"
  }
}
EOF

echo "RUN_ID=$RUN_ID"
echo "COMMIT_SHA=$COMMIT_SHA"
echo "LOCAL_REPORT_DIR=$REPORT_DIR"
echo "PENDING_OWNER_DEVICE_EVIDENCE"
echo "PENDING_STAGING_LATENCY_EVIDENCE"
echo 'SPRINT_9_10_LOCAL_GATES_PASSED_STAGING_SLA_AND_VOICEOVER_DEVICE_EVIDENCE_STILL_REQUIRED'
