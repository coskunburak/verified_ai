# Sprint 9.1 File Manifest

## Database, Config, Error Taxonomy & Privacy Lifecycle (Slice 1)
- `services/api/src/main/resources/db/migration/platform/V054__exam_definitions_curriculum_mapping_and_versioning.sql`
- `services/api/src/main/resources/application.yml`
- `services/api/src/test/resources/application-test.yml`
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/java/com/verifiedai/exam/package-info.java`
- `services/api/src/main/java/com/verifiedai/exam/infrastructure/privacy/ExamPrivacyLifecycleContributor.java`

## Exam Domain, Application, Persistence & REST API (Slices 2 & 3)
- `services/api/src/main/java/com/verifiedai/exam/domain/ExamModels.java`
- `services/api/src/main/java/com/verifiedai/exam/domain/ExamBlueprintHashCalculator.java`
- `services/api/src/main/java/com/verifiedai/exam/domain/ScoringFormulaPolicy.java`
- `services/api/src/main/java/com/verifiedai/exam/domain/ExamBlueprintValidator.java`
- `services/api/src/main/java/com/verifiedai/exam/application/ExamRepository.java`
- `services/api/src/main/java/com/verifiedai/exam/application/ExamCatalogService.java`
- `services/api/src/main/java/com/verifiedai/exam/application/StudentExamTargetService.java`
- `services/api/src/main/java/com/verifiedai/exam/infrastructure/persistence/ExamJdbcRepository.java`
- `services/api/src/main/java/com/verifiedai/exam/api/ExamPlatformController.java`

## Backend Automated Tests (Slices 1–3)
- `services/api/src/test/java/com/verifiedai/exam/domain/ExamBlueprintValidatorAndScoringTest.java`
- `services/api/src/test/java/com/verifiedai/exam/domain/V054SeedSqlConsistencyTest.java`
- `services/api/src/test/java/com/verifiedai/exam/infrastructure/privacy/ExamPrivacyLifecycleContributorTest.java`
- `services/api/src/test/java/com/verifiedai/exam/api/ExamPlatformControllerTest.java`
- `services/api/src/test/java/com/verifiedai/exam/integration/ExamPlatformPostgresIntegrationTest.java`

## JSON Schemas, Fixtures, OpenAPI 3.1 Mirrors & Schema Validator (Slice 4)
- `packages/schemas/exam/v1/exam-definition-catalog-response.schema.json`
- `packages/schemas/exam/v1/exam-definition-detail-response.schema.json`
- `packages/schemas/exam/v1/upsert-student-exam-target-request.schema.json`
- `packages/schemas/exam/v1/student-exam-target-response.schema.json`
- `packages/schemas/exam/v1/student-exam-target-list-response.schema.json`
- `packages/schemas/exam/v1/fixtures/exam-catalog-response.fixture.json`
- `packages/schemas/exam/v1/fixtures/exam-detail-tyt-math.fixture.json`
- `packages/schemas/exam/v1/fixtures/exam-detail-ayt-math.fixture.json`
- `packages/schemas/exam/v1/fixtures/exam-detail-sat-math.fixture.json`
- `packages/schemas/exam/v1/fixtures/upsert-student-exam-target-request.fixture.json`
- `packages/schemas/exam/v1/fixtures/student-exam-target-response.fixture.json`
- `packages/schemas/exam/v1/fixtures/student-exam-target-list-response.fixture.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`
- `scripts/quality/validate_json_schemas.py`

## iOS Swift 6 Client, Offline SQLite Cache, Localization & Tests (Slice 5)
- `apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift`
- `apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift`
- `apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift`
- `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift`
- `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift`
- `apps/ios/VerifiedAI/App/AppDependencies.swift`
- `apps/ios/VerifiedAI/App/RootView.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift`
- `apps/ios/VerifiedAIUITests/ExamCatalogUITests.swift`
- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`

## Acceptance Runner & Phase 9 Documentation Reconciliation (Slice 6)
- `scripts/quality/sprint_9_1_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.1_EXAM_DEFINITIONS_CURRICULUM_MAPPING_AND_VERSIONING.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.4_TIMED_MOCK_EXAM_RUNTIME.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.6_EXAM_READINESS_MODEL_AND_CONFIDENCE_SEMANTICS.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.7_WEAK_AREA_PRIORITIZATION_AFTER_ASSESSMENTS.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.10_ASSESSMENT_INTEGRITY_ACCESSIBILITY_FAILURE_RECOVERY_AND_RELIABILITY.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.1_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.1_FILE_MANIFEST.md`
