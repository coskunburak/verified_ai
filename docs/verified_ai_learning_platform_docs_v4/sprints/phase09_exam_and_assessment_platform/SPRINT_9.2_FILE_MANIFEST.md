# Sprint 9.2 File Manifest

## Database Migrations & Configuration
- `services/api/src/main/resources/db/migration/platform/V055__candidate_profiles_accommodations_and_syllabus_ingestion.sql`
- `services/api/src/main/resources/application.yml`
- `services/api/src/test/resources/application-test.yml`

## Backend Java (`services/api/src/main/java/com/verifiedai/`)
- `sharedkernel/error/ApiErrorCode.java` (Sprint 9.2 error codes)
- `exam/events/package-info.java` (`@NamedInterface("events")`)
- `exam/events/CandidateGoalUpdatedEvent.java`
- `exam/domain/CandidateProfileModels.java`
- `exam/domain/CandidatePaceCalculator.java`
- `exam/domain/SyllabusMediaValidator.java`
- `exam/domain/CurriculumTopicDisambiguator.java`
- `exam/application/CandidateProfileRepository.java`
- `exam/application/SyllabusVisionGateway.java`
- `exam/application/CandidateProfileService.java`
- `exam/application/SyllabusIngestionService.java`
- `exam/infrastructure/persistence/CandidateProfileJdbcRepository.java`
- `exam/infrastructure/vision/DeterministicSyllabusVisionGateway.java`
- `exam/infrastructure/vision/GeminiSyllabusVisionGateway.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributor.java`
- `exam/api/CandidateProfileController.java`
- `studyplan/package-info.java` (declares `exam::events`)
- `studyplan/rebalance/CandidateGoalRebalanceListener.java`

## Backend Tests (`services/api/src/test/java/com/verifiedai/`)
- `exam/application/CandidateProfileServiceTest.java`
- `exam/application/SyllabusIngestionServiceTest.java`
- `exam/domain/V055MigrationSqlConsistencyTest.java`
- `exam/api/CandidateProfileControllerWebMvcTest.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributorTest.java`
- `exam/integration/ExamMigrationV055IntegrationTest.java`

## JSON Schemas, Fixtures & OpenAPI Contracts
- `packages/schemas/exam/v1/candidate-profile.schema.json`
- `packages/schemas/exam/v1/candidate-profile-history-response.schema.json`
- `packages/schemas/exam/v1/syllabus-ingestion.schema.json`
- `packages/schemas/exam/v1/upsert-candidate-profile-request.schema.json`
- `packages/schemas/exam/v1/syllabus-confirm-request.schema.json`
- `packages/schemas/exam/v1/syllabus-ingest-json-request.schema.json`
- `packages/schemas/exam/v1/fixtures/candidate-profile-yks-tyt.example.json`
- `packages/schemas/exam/v1/fixtures/candidate-profile-history-response.fixture.json`
- `packages/schemas/exam/v1/fixtures/syllabus-ingestion-yks-tyt.example.json`
- `packages/schemas/exam/v1/fixtures/upsert-candidate-profile-request.fixture.json`
- `packages/schemas/exam/v1/fixtures/syllabus-confirm-request.fixture.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`

## iOS (`apps/ios/`)
- `VerifiedAI/Core/Networking/HTTPRequest.swift`
- `VerifiedAI/Core/Networking/APIClient.swift`
- `VerifiedAI/Features/Exam/Domain/ExamModels.swift`
- `VerifiedAI/Features/Exam/Data/ExamAPIService.swift`
- `VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift`
- `VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/CandidateGoalSetupView.swift`
- `VerifiedAI/Features/Exam/Presentation/SyllabusSnapToPlanSheet.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift`
- `VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift`
- `VerifiedAI/Features/Home/Presentation/CoachHubView.swift`
- `VerifiedAI/App/AppDependencies.swift`
- `VerifiedAI/App/RootView.swift`
- `VerifiedAI/Resources/Localizable.xcstrings`
- `VerifiedAI.xcodeproj/project.pbxproj`
- `VerifiedAITests/Exam/CandidateProfileViewModelTests.swift`
- `VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift`
- `VerifiedAIUITests/ExamCatalogUITests.swift`

## Quality Gates & Documentation
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_9_2_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.2_LEARNER_EXAM_GOAL_SETUP_AND_CANDIDATE_PROFILE.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.2_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.2_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
