# Sprint 8.10 File Manifest

## Database & Privacy (Slice 1)
- `services/api/src/main/resources/db/migration/platform/V053__cold_start_diagnostics_and_quality_gates.sql`
- `services/api/src/main/java/com/verifiedai/studyplan/privacy/StudyPlanPrivacyLifecycleContributor.java`
- `services/api/src/test/java/com/verifiedai/studyplan/privacy/StudyPlanPrivacyLifecycleContributorTest.java`

## Python Math Verifier (Slice 2)
- `services/math-verifier/app/verifiers/cold_start_diagnostic.py`
- `services/math-verifier/app/verifiers/attempt_learning.py`
- `services/math-verifier/tests/test_cold_start_diagnostic_verifier.py`

## Spring Backend & Quality Gates (Slice 3)
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/application-test.yml`
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coldstart/ColdStartModels.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coldstart/ColdStartDiagnosticRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coldstart/ColdStartDiagnosticJdbcRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coldstart/ColdStartCalibrationService.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coldstart/ColdStartCalibrationController.java`
- `services/api/src/main/java/com/verifiedai/studyplan/quality/QualityGateModels.java`
- `services/api/src/main/java/com/verifiedai/studyplan/quality/PlannerQualityGateService.java`
- `services/api/src/main/java/com/verifiedai/studyplan/quality/AdminQualityGateController.java`
- `services/api/src/main/java/com/verifiedai/studyplan/service/DefaultNextBestActionSignalProvider.java`
- `services/api/src/test/java/com/verifiedai/studyplan/coldstart/ColdStartCalibrationServiceTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/quality/PlannerQualityGateServiceTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/coldstart/ColdStartAndQualityGateControllerTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/coldstart/ColdStartAndQualityGatePostgresIntegrationTest.java`

## Schemas, Fixtures, OpenAPI & Quality Validators (Slice 4)
- `packages/schemas/studyplan/cold-start-request-v1.schema.json`
- `packages/schemas/studyplan/cold-start-v1.schema.json`
- `packages/schemas/analytics/study-plan-quality-v1.schema.json`
- `packages/schemas/studyplan/fixtures/cold-start-request-v1.valid.json`
- `packages/schemas/studyplan/fixtures/cold-start-v1.valid.json`
- `packages/schemas/analytics/fixtures/study-plan-quality-v1.valid.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`
- `scripts/quality/validate_json_schemas.py`

## iOS Client, Offline Cache, Localization & Tests (Slice 5)
- `apps/ios/VerifiedAI/Features/Home/Domain/ColdStartDiagnosticModels.swift`
- `apps/ios/VerifiedAI/Features/Home/Data/ColdStartSQLiteCache.swift`
- `apps/ios/VerifiedAI/Features/Home/Data/ColdStartDiagnosticService.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/ColdStartDiagnosticViewModel.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/ColdStartDiagnosticCardView.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/ColdStartDiagnosticFlowView.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift`
- `apps/ios/VerifiedAI/App/AppDependencies.swift`
- `apps/ios/VerifiedAI/App/RootView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/Sprint810ColdStartCalibrationTests.swift`
- `apps/ios/VerifiedAIUITests/AttemptAnalysisUITests.swift`
- `apps/ios/VerifiedAIUITests/VerifiedAIUITests.swift`
- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`

## Acceptance Runner & Documentation (Slice 6)
- `scripts/quality/sprint_8_10_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.10_PERSONALIZATION_CALIBRATION_COLD_START_AND_RECOMMENDATION_QUALITY_GATES.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.10_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.10_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
