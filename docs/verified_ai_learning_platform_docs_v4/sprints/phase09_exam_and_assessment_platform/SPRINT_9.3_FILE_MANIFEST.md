# Sprint 9.3 File Manifest

## Database Migrations & Configuration
- `services/api/src/main/resources/db/migration/platform/V056__question_bank_blueprints_and_assessment_assembly.sql`
- `services/api/src/main/resources/application.yml`
- `services/api/src/test/resources/application-test.yml`

## Backend Java (`services/api/src/main/java/com/verifiedai/`)
- `sharedkernel/error/ApiErrorCode.java` (15 Sprint 9.3 error codes)
- `exam/domain/QuestionItemFormat.java`
- `exam/domain/QuestionDifficultyBand.java`
- `exam/domain/CognitiveTaxonomyLevel.java`
- `exam/domain/QuestionItemStatus.java`
- `exam/domain/QuestionProvenanceSourceType.java`
- `exam/domain/QuestionLicenseClassification.java`
- `exam/domain/ProvenanceVerificationStatus.java`
- `exam/domain/AssessmentBlueprintMode.java`
- `exam/domain/AssessmentBlueprintStatus.java`
- `exam/domain/AssembledAssessmentStatus.java`
- `exam/domain/QuestionOption.java`
- `exam/domain/QuestionProvenanceRecord.java`
- `exam/domain/QuestionBankItem.java`
- `exam/domain/BlueprintTopicRule.java`
- `exam/domain/AssessmentBlueprint.java`
- `exam/domain/AppliedAccommodationSnapshot.java`
- `exam/domain/AssemblyDistributionSummary.java`
- `exam/domain/AssembledAssessmentItem.java`
- `exam/domain/AssembledAssessment.java`
- `exam/domain/DeterministicAssessmentAssemblyEngine.java`
- `exam/domain/QuestionBankRepository.java`
- `exam/domain/AssessmentBlueprintRepository.java`
- `exam/domain/AssembledAssessmentRepository.java`
- `exam/events/QuestionBankItemPublishedEvent.java`
- `exam/events/AssessmentBlueprintActivatedEvent.java`
- `exam/events/AssessmentAssembledEvent.java`
- `exam/application/ExamAssessmentAssemblyService.java`
- `exam/infrastructure/persistence/QuestionBankJdbcRepository.java`
- `exam/infrastructure/persistence/AssessmentBlueprintJdbcRepository.java`
- `exam/infrastructure/persistence/AssembledAssessmentJdbcRepository.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributor.java`
- `exam/api/ExamAssessmentAssemblyController.java`

## Backend Tests (`services/api/src/test/java/com/verifiedai/`)
- `exam/persistence/V056MigrationSqlConsistencyTest.java`
- `exam/domain/QuestionBankItemDomainTest.java`
- `exam/domain/AssessmentBlueprintDomainTest.java`
- `exam/domain/DeterministicAssessmentAssemblyEngineTest.java`
- `exam/api/ExamAssessmentAssemblyControllerTest.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributorTest.java`
- `exam/integration/ExamMigrationV056IntegrationTest.java`

## JSON Schemas, Fixtures & OpenAPI Contracts
- `packages/schemas/exam/v1/question-bank-item-create-request.schema.json`
- `packages/schemas/exam/v1/question-bank-item-response.schema.json`
- `packages/schemas/exam/v1/assessment-blueprint-create-request.schema.json`
- `packages/schemas/exam/v1/assessment-blueprint-response.schema.json`
- `packages/schemas/exam/v1/assessment-assembly-request.schema.json`
- `packages/schemas/exam/v1/assembled-assessment-response.schema.json`
- `packages/schemas/exam/v1/fixtures/question-bank-item-create-request.fixture.json`
- `packages/schemas/exam/v1/fixtures/question-bank-item-response.fixture.json`
- `packages/schemas/exam/v1/fixtures/assessment-blueprint-create-request.fixture.json`
- `packages/schemas/exam/v1/fixtures/assessment-blueprint-response.fixture.json`
- `packages/schemas/exam/v1/fixtures/assessment-assembly-request.fixture.json`
- `packages/schemas/exam/v1/fixtures/assembled-assessment-response.fixture.json`
- `packages/schemas/exam/v1/examples/question-bank-item-create-request.example.json`
- `packages/schemas/exam/v1/examples/question-bank-item-response.example.json`
- `packages/schemas/exam/v1/examples/assessment-blueprint-create-request.example.json`
- `packages/schemas/exam/v1/examples/assessment-blueprint-response.example.json`
- `packages/schemas/exam/v1/examples/assessment-assembly-request.example.json`
- `packages/schemas/exam/v1/examples/assembled-assessment-response.example.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`

## iOS (`apps/ios/`)
- `VerifiedAI/Features/Exam/Domain/ExamModels.swift`
- `VerifiedAI/Features/Exam/Data/ExamAPIService.swift`
- `VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamBlueprintAssemblyView.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift`
- `VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift`
- `VerifiedAI/Features/Home/Presentation/CoachHubView.swift`
- `VerifiedAI/App/AppDependencies.swift`
- `VerifiedAI/App/RootView.swift`
- `VerifiedAI/Resources/Localizable.xcstrings`
- `VerifiedAI.xcodeproj/project.pbxproj`
- `VerifiedAITests/Exam/ExamBlueprintAssemblyViewModelTests.swift`
- `VerifiedAITests/Exam/ExamOfflineSQLiteStoreTests.swift`
- `VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift`
- `VerifiedAIUITests/ExamCatalogUITests.swift`

## Quality Gates & Documentation
- `scripts/quality/secret_scan.sh`
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_9_3_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.3_QUESTION_BANK_BLUEPRINT_AND_ASSESSMENT_ASSEMBLY_RULES.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.3_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.3_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
