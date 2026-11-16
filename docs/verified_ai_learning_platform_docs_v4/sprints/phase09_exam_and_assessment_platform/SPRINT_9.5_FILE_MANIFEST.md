# Sprint 9.5 File Manifest

## Database Migrations & Configuration
- `services/api/src/main/resources/db/migration/platform/V058__create_exam_scoring_and_evidence_tables.sql`
- `services/api/src/main/resources/application.yml` (`verifiedai.exam.scoring-engine-enabled: false`)
- `services/api/src/test/resources/application-test.yml` (`verifiedai.exam.scoring-engine-enabled: true`)

## Backend Java (`services/api/src/main/java/com/verifiedai/`)
- `sharedkernel/error/ApiErrorCode.java` (Sprint 9.5 scoring error codes)
- `exam/domain/ScoringFormulaPolicy.java`
- `exam/domain/DeterministicExamScoringPolicy.java`
- `exam/domain/ExamSessionScoreSnapshot.java`
- `exam/domain/ExamSessionItemEvaluation.java`
- `exam/domain/ExamScoreRegradeAudit.java`
- `exam/domain/TopicScoreBreakdown.java`
- `exam/domain/DifficultyScoreBreakdown.java`
- `exam/domain/ItemEvaluationOutcome.java`
- `exam/domain/F28ScratchpadTraceStatus.java`
- `exam/domain/ScoreRegradeReasonCode.java`
- `exam/events/ExamScoreComputedEvent.java`
- `exam/application/ExamRepository.java`
- `exam/application/ExamScoringEngineService.java`
- `exam/application/ExamSessionRuntimeService.java`
- `exam/infrastructure/persistence/ExamJdbcRepository.java`
- `exam/infrastructure/persistence/QuestionBankJdbcRepository.java`
- `exam/infrastructure/persistence/ExamScoringRepository.java`
- `exam/infrastructure/persistence/JdbcExamScoringRepository.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributor.java`
- `exam/api/ExamScoringController.java`

## Backend Tests (`services/api/src/test/java/com/verifiedai/`)
- `exam/domain/ScoringFormulaPolicyTest.java`
- `exam/domain/DeterministicExamScoringPolicyTest.java`
- `exam/application/ExamScoringEngineServiceTest.java`
- `exam/application/ExamSessionRuntimeServiceTest.java`
- `exam/persistence/V058MigrationSqlConsistencyTest.java`
- `exam/api/ExamScoringControllerWebMvcTest.java`
- `exam/infrastructure/privacy/ExamPrivacyLifecycleContributorTest.java`
- `exam/integration/ExamScoringMigrationAndRepositoryTest.java`

## JSON Schemas, Fixtures & OpenAPI Contracts
- `packages/schemas/exam/v1/exam-score-summary-response.schema.json`
- `packages/schemas/exam/v1/exam-score-regrade-request.schema.json`
- `packages/schemas/exam/v1/exam-score-regrade-response.schema.json`
- `packages/schemas/exam/v1/fixtures/exam-score-summary-response.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-score-regrade-request.valid.json`
- `packages/schemas/exam/v1/fixtures/exam-score-regrade-response.valid.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`

## iOS (`apps/ios/`)
- `VerifiedAI/Features/Exam/Domain/ExamModels.swift`
- `VerifiedAI/Features/Exam/Data/ExamAPIService.swift`
- `VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamSessionViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift`
- `VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift`
- `VerifiedAI/Features/Exam/Presentation/CandidateProfileViewModel.swift`
- `VerifiedAI/Resources/Localizable.xcstrings`
- `VerifiedAI.xcodeproj/project.pbxproj`
- `VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift` (`DisabledExamService` Sprint 9.5 `ExamAPIServicing` conformance)
- `VerifiedAITests/Exam/Sprint95ExamScoringAndEvidenceTests.swift`
- `VerifiedAIUITests/ExamSessionRuntimeUITests.swift`

## Quality Gates & Documentation
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_9_5_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.5_SCORING_ENGINE_AND_EVIDENCE_TRACEABILITY.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.5_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.5_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
