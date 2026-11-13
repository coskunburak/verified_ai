# Sprint 8.5 File Manifest

## Database Migrations
- `services/api/src/main/resources/db/migration/platform/V048__study_plan_rebalance_events.sql`

## Schemas, Fixtures & OpenAPI Contracts
- `packages/schemas/studyplan/recovery-status-v1.schema.json`
- `packages/schemas/studyplan/rebalance-response-v1.schema.json`
- `packages/schemas/studyplan/fixtures/recovery-status-v1.valid.json`
- `packages/schemas/studyplan/fixtures/rebalance-response-v1.valid.json`
- `packages/schemas/coach/daily-coach-v1.schema.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`

## Spring Boot Backend (`services/api`)
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionStatus.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachTaskRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionService.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/RebalanceTriggerType.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/PruneReasonCode.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/StudyPlanRecoveryNotFoundException.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/RebalanceEvent.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/RebalanceEventEntity.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/RebalanceEventRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/MissedDayRecoveryPolicy.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/RebalancePrivacyPurgeContributor.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/StudyPlanRebalancingService.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/RebalanceController.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/dto/StudyPlanRecoveryStatusResponse.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/dto/StudyPlanRebalanceRequest.java`
- `services/api/src/main/java/com/verifiedai/studyplan/rebalance/dto/StudyPlanRebalanceResponse.java`

## Spring Boot Tests (`services/api/src/test`)
- `services/api/src/test/java/com/verifiedai/studyplan/rebalance/MissedDayRecoveryPolicyTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/rebalance/StudyPlanRebalancingServiceTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/rebalance/RebalanceControllerTest.java`

## iOS App, Localization & XCTests (`apps/ios`)
- `apps/ios/VerifiedAI/Features/Home/Data/RebalanceSQLiteCache.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/StudyPlanRebalanceViewModel.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/PlanRebalanceNotificationSheet.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/Sprint85RebalancingTests.swift`

## Quality Gates & Documentation
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_8_5_owner_acceptance.sh`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.5_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.5_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
