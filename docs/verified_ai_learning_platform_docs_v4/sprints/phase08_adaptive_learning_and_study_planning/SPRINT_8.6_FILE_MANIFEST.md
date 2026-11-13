# Sprint 8.6 File Manifest

## Database Migrations & Canonical Catalog
- `services/api/src/main/resources/db/migration/platform/V049__micro_lesson_cards.sql`
- `services/api/src/main/resources/micro_lessons/micro_lesson_catalog_v1.json`

## Schemas, Fixtures & OpenAPI Contracts
- `packages/schemas/micro-lesson-card-v1.schema.json`
- `packages/schemas/micro-lesson-complete-request-v1.schema.json`
- `packages/schemas/micro-lesson-complete-response-v1.schema.json`
- `packages/schemas/fixtures/micro-lesson-card-tr-v1.valid.json`
- `packages/schemas/fixtures/micro-lesson-card-en-v1.valid.json`
- `packages/schemas/fixtures/micro-lesson-complete-request-v1.valid.json`
- `packages/schemas/fixtures/micro-lesson-complete-response-v1.valid.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`

## Python Math Verifier (`services/math-verifier`)
- `services/math-verifier/app/micro_lesson_verifier.py`
- `services/math-verifier/tests/test_micro_lesson_verifier.py`

## Spring Boot Backend (`services/api`)
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/AttemptDiagnosisOwnership.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonCard.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonCompletionRecord.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonCompletionRequest.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonCompletionResult.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonCoreRule.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonCounterexample.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonHashVerifier.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonQuickCheck.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonQuickCheckOption.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonRepository.java`
- `services/api/src/main/java/com/verifiedai/tutoring/domain/MicroLessonTriggerSource.java`
- `services/api/src/main/java/com/verifiedai/tutoring/application/MicroLessonApplicationService.java`
- `services/api/src/main/java/com/verifiedai/tutoring/application/MicroLessonDataLifecycleContributor.java`
- `services/api/src/main/java/com/verifiedai/tutoring/infrastructure/persistence/JdbcMicroLessonRepository.java`
- `services/api/src/main/java/com/verifiedai/tutoring/api/MicroLessonController.java`

## Spring Boot Tests (`services/api/src/test`)
- `services/api/src/test/java/com/verifiedai/tutoring/domain/MicroLessonHashVerifierTest.java`
- `services/api/src/test/java/com/verifiedai/tutoring/application/InMemoryMicroLessonRepository.java`
- `services/api/src/test/java/com/verifiedai/tutoring/application/MicroLessonApplicationServiceTest.java`
- `services/api/src/test/java/com/verifiedai/tutoring/application/MicroLessonPrivacyLifecycleTest.java`
- `services/api/src/test/java/com/verifiedai/tutoring/api/MicroLessonControllerTest.java`

## iOS App, Localization & XCTests (`apps/ios`)
- `apps/ios/VerifiedAI/Features/Tutor/Domain/MicroLessonModels.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Data/MicroLessonSQLiteCache.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonViewModel.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/MicroLessonDrawerView.swift`
- `apps/ios/VerifiedAI/Features/Tutor/Presentation/TutorView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/Sprint86MicroLessonTests.swift`

## Quality Gates & Documentation
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_8_6_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.6_AI_MICRO_LESSONS_TRIGGERED_BY_DIAGNOSED_WEAKNESSES.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.6_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.6_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
