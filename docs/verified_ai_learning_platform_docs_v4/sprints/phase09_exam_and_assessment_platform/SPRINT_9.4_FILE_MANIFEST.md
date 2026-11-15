# Sprint 9.4 File Manifest

## Database Migrations & Configuration
- `services/api/src/main/resources/db/migration/platform/V057__timed_mock_exam_runtime_and_scratchpad_ingestion.sql`
- `services/api/src/main/resources/application.yml`
- `services/api/src/test/resources/application-test.yml`

## Backend Java (`services/api/src/main/java/com/verifiedai/`)
- `sharedkernel/error/ApiErrorCode.java` (Sprint 9.4 runtime error codes)
- `identity/application/AccountPrivacyApplicationService.java` (explicit `userRepository.save(user)` hardening after `markDeleted`)
- `exam/domain/ExamSessionStatus.java`
- `exam/domain/ExamItemResponseState.java`
- `exam/domain/ExamClockPolicy.java`
- `exam/domain/ExamScratchpadAsset.java`
- `exam/domain/ExamSessionItem.java`
- `exam/domain/ExamSession.java`
- `exam/domain/ExamSessionDomainModels.java` (`ServerClockSync`, `SectionProgressSummary`, `F28VerificationBatchEntry`, `F28VerificationBatchManifest`)
- `exam/events/ExamSessionStartedEvent.java`
- `exam/events/ExamItemAnsweredEvent.java`
- `exam/events/ExamScratchpadAttachedEvent.java`
- `exam/events/ExamSessionSubmittedEvent.java`
- `exam/application/ActiveExamNoHintGuard.java`
- `exam/application/ExamSessionRuntimeService.java`
- `exam/infrastructure/persistence/ExamSessionRepository.java`
- `exam/infrastructure/persistence/JdbcExamSessionRepository.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributor.java`
- `exam/api/ExamSessionRuntimeController.java`

## Backend Tests (`services/api/src/test/java/com/verifiedai/`)
- `exam/domain/ExamSessionTest.java`
- `exam/application/ExamSessionRuntimeServiceTest.java`
- `exam/persistence/V057MigrationSqlConsistencyTest.java`
- `exam/api/ExamSessionRuntimeControllerTest.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributorTest.java`
- `exam/integration/ExamSessionMigrationAndRepositoryTest.java`

## JSON Schemas, Fixtures & OpenAPI Contracts
- `packages/schemas/exam/v1/exam-session-start-request.schema.json`
- `packages/schemas/exam/v1/exam-session-response.schema.json`
- `packages/schemas/exam/v1/exam-session-item-autosave-request.schema.json`
- `packages/schemas/exam/v1/exam-session-item-autosave-response.schema.json`
- `packages/schemas/exam/v1/exam-scratchpad-upload-request.schema.json`
- `packages/schemas/exam/v1/exam-scratchpad-upload-response.schema.json`
- `packages/schemas/exam/v1/exam-session-submit-request.schema.json`
- `packages/schemas/exam/v1/exam-session-submit-response.schema.json`
- `packages/schemas/exam/v1/exam-no-hint-guard-response.schema.json`
- `packages/schemas/exam/v1/fixtures/exam-session-start-request.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-session-response.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-session-item-autosave-request.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-session-item-autosave-response.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-scratchpad-upload-request.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-scratchpad-upload-response.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-session-submit-request.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-session-submit-response.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-no-hint-guard-response.valid.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`

## iOS (`apps/ios/`)
- `VerifiedAI/Features/Exam/Domain/ExamModels.swift`
- `VerifiedAI/Features/Exam/Data/ExamAPIService.swift`
- `VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift`
- `VerifiedAI/Resources/Localizable.xcstrings`
- `VerifiedAI.xcodeproj/project.pbxproj`
- `VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift` (`DisabledExamService` Sprint 9.4 `ExamAPIServicing` conformance)
- `VerifiedAITests/Exam/ExamSessionViewModelTests.swift`
- `VerifiedAITests/Exam/ExamSessionSQLiteStoreTests.swift`
- `VerifiedAIUITests/ExamSessionRuntimeUITests.swift`

## Quality Gates & Documentation
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_9_4_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.4_TIMED_MOCK_EXAM_RUNTIME.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.4_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.4_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
