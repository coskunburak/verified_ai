# Sprint 9.6 File Manifest

**Sprint:** `SPRINT_9.6` — Exam Readiness Model, 90% Credible-Interval Net Projection & Active-Trap Penalty Semantics (`CNT-LOS-11`)  
**Status:** `IMPLEMENTED & VERIFIED`

## Database Migrations (Flyway PostgreSQL)
- **Created:** `services/api/src/main/resources/db/migration/platform/V059__sprint_9_6_exam_readiness_projections.sql` (`exam_readiness_projections`, `exam_readiness_topic_allocations`, CHECK constraints `chk_exam_readiness_net_interval_order`, `chk_exam_readiness_net_bounds`, `chk_exam_readiness_preliminary_guard`, `training_eligible = FALSE`, `llm_tokens_used = 0`, `inference_cost_usd = 0.000000`)

## Backend (`services/api/`)
- **Modified:** `services/api/src/main/java/com/verifiedai/exam/package-info.java` (`allowedDependencies` includes `"mastery"`, `"mistake"`)
- **Modified:** `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java` (`EXAM_READINESS_DISABLED`)
- **Modified:** `services/api/src/main/resources/application.yml` (`verifiedai.exam.readiness-enabled: ${VERIFIEDAI_EXAM_READINESS_ENABLED:false}`)
- **Modified:** `services/api/src/test/resources/application-test.yml` (`verifiedai.exam.readiness-enabled: true`)
- **Created:** `services/api/src/main/java/com/verifiedai/exam/config/ExamProperties.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessConfidenceTier.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessRationaleCode.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/ActiveTrapImpactItem.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/SectionReadinessBreakdown.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessTopicAllocation.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessProjection.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessRepository.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/domain/ExamReadinessCalculator.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/events/ExamReadinessComputedEvent.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/application/ExamReadinessSignalReader.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/application/ExamReadinessProjectionService.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/infrastructure/persistence/JdbcExamReadinessRepository.java`
- **Modified:** `services/api/src/main/java/com/verifiedai/exam/infrastructure/privacy/ExamPrivacyLifecycleContributor.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/api/ExamReadinessResponse.java`
- **Created:** `services/api/src/main/java/com/verifiedai/exam/api/ExamReadinessController.java`

## Shared Schemas & OpenAPI 3.1 Contracts
- **Created:** `packages/schemas/exam/v1/exam-readiness-projection.schema.json`
- **Created:** `packages/schemas/exam/v1/exam-readiness-recompute-request.schema.json`
- **Created:** `packages/schemas/exam/v1/fixtures/exam-readiness-projection-valid.json`
- **Created:** `packages/schemas/exam/v1/fixtures/exam-readiness-projection-preliminary-valid.json`
- **Created:** `packages/schemas/exam/v1/fixtures/exam-readiness-recompute-request-valid.json`
- **Modified:** `packages/contracts/openapi/public-api.yaml`
- **Modified (byte-identical twin):** `packages/openapi/verified-ai-core-api.yaml`

## Native iOS (`apps/ios/`) — Concept B Design System 2.0 (Screen 9)
- **Modified:** `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` (+34 `exam.readiness.*` EN/TR keys)
- **Modified:** `apps/ios/VerifiedAI/Features/Exam/Domain/ExamModels.swift`
- **Modified:** `apps/ios/VerifiedAI/Features/Exam/Data/ExamAPIService.swift`
- **Modified:** `apps/ios/VerifiedAI/Features/Exam/Data/ExamOfflineSQLiteStore.swift` (`cached_exam_readiness_projections`)
- **Created:** `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessViewModel.swift`
- **Created:** `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamReadinessHubView.swift`
- **Modified:** `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetViewModel.swift`
- **Modified:** `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamCatalogAndTargetView.swift`
- **Modified:** `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryViewModel.swift`
- **Modified:** `apps/ios/VerifiedAI/Features/Exam/Presentation/ExamScoreSummaryView.swift`
- **Modified:** `apps/ios/VerifiedAI/App/AppDependencies.swift`
- **Modified:** `apps/ios/VerifiedAI/App/MainTabView.swift`
- **Modified:** `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`

## Automated Tests (Category A–E)
- **Created (Category A):** `services/api/src/test/java/com/verifiedai/exam/domain/ExamReadinessCalculatorTest.java`
- **Created (Category B SQL):** `services/api/src/test/java/com/verifiedai/exam/persistence/V059MigrationSqlConsistencyTest.java`
- **Created (Category B Testcontainers):** `services/api/src/test/java/com/verifiedai/exam/integration/ExamReadinessMigrationAndRepositoryTest.java`
- **Created (Category C WebMvc):** `services/api/src/test/java/com/verifiedai/exam/api/ExamReadinessControllerWebMvcTest.java`
- **Created (Category C Privacy):** `services/api/src/test/java/com/verifiedai/exam/infrastructure/privacy/ExamReadinessPrivacyLifecycleContributorTest.java`
- **Modified (iOS Stub Sync):** `apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift`
- **Created (Category D XCTest):** `apps/ios/VerifiedAITests/Exam/Sprint96ExamReadinessTests.swift`
- **Created (Category E XCUITest):** `apps/ios/VerifiedAIUITests/ExamReadinessHubUITests.swift`

## Quality Gates & Documentation
- **Modified:** `scripts/quality/validate_json_schemas.py`
- **Created:** `scripts/quality/sprint_9_6_owner_acceptance.sh`
- **Modified:** `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.6_EXAM_READINESS_MODEL_AND_CONFIDENCE_SEMANTICS.md`
- **Modified:** `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
- **Created:** `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.6_EXECUTION_REPORT.md`
- **Created:** `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.6_FILE_MANIFEST.md`
- **Modified:** `docs/verified_ai_learning_platform_docs_v4/quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`
- **Modified:** `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
