# Sprint 8.8 File Manifest

## Database Migrations
- `services/api/src/main/resources/db/migration/platform/V051__gamification_streaks_and_achievements.sql`

## JSON Schemas & Fixtures
- `packages/schemas/gamification-activity-record-request.schema.json`
- `packages/schemas/gamification-summary-response.schema.json`
- `packages/schemas/fixtures/gamification-activity-record-request.valid.json`
- `packages/schemas/fixtures/gamification-summary-response.valid.json`

## OpenAPI & Localization
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`

## Backend (`services/api`)
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/application-test.yml`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/DailyStreakStatus.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/GamificationController.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/GamificationDtos.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/GamificationEvidenceType.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/GamificationExceptions.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/GamificationModels.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/GamificationPolicyService.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/JdbcGamificationRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/PedagogicalBadgeCategory.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/PedagogicalBadgeCode.java`
- `services/api/src/main/java/com/verifiedai/studyplan/gamification/StreakStatus.java`
- `services/api/src/main/java/com/verifiedai/studyplan/privacy/GamificationPrivacyContributor.java`
- `services/api/src/test/java/com/verifiedai/studyplan/gamification/GamificationControllerIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/gamification/GamificationPolicyServiceTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/gamification/GamificationPostgresIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/gamification/GamificationSpringWiringTest.java`

## Native iOS Client, Project Wiring & Tests (`apps/ios`)
- `apps/ios/VerifiedAI/Features/Gamification/GamificationModels.swift`
- `apps/ios/VerifiedAI/Features/Gamification/GamificationService.swift`
- `apps/ios/VerifiedAI/Features/Gamification/StreakBadgeView.swift`
- `apps/ios/VerifiedAI/Features/Gamification/MilestoneSheetView.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift`
- `apps/ios/VerifiedAITests/Sprint88GamificationTests.swift`
- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`

## Quality & Acceptance Scripts
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_8_8_owner_acceptance.sh`

## Documentation & Manifests
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.8_GOALS_STREAKS_ACHIEVEMENTS_AND_NON_MANIPULATIVE_GAMIFICATION.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.8_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.8_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
