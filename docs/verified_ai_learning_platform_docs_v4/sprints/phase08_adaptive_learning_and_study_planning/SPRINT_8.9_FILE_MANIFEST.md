# Sprint 8.9 File Manifest

## Database Migrations
- `services/api/src/main/resources/db/migration/platform/V052__weekly_learner_digests.sql`

## Contracts, Schemas & OpenAPI
- `packages/schemas/weekly-digest-v1.schema.json`
- `packages/schemas/weekly-share-packet-v1.schema.json`
- `packages/schemas/fixtures/weekly_digest_v1.valid.json`
- `packages/schemas/fixtures/weekly_share_packet_v1.valid.json`
- `packages/contracts/openapi/public-api.yaml`
- `packages/openapi/verified-ai-core-api.yaml`

## Backend (`services/api`)
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/application-test.yml`
- `services/api/src/main/java/com/verifiedai/analytics/WeeklyDigestStatus.java`
- `services/api/src/main/java/com/verifiedai/analytics/RecommendationReasonCode.java`
- `services/api/src/main/java/com/verifiedai/analytics/ShareRecipientRole.java`
- `services/api/src/main/java/com/verifiedai/analytics/WeeklyLearnerDigest.java`
- `services/api/src/main/java/com/verifiedai/analytics/WeeklyReportDtos.java`
- `services/api/src/main/java/com/verifiedai/analytics/WeeklyReportExceptions.java`
- `services/api/src/main/java/com/verifiedai/analytics/WeeklyLearnerDigestRepository.java`
- `services/api/src/main/java/com/verifiedai/analytics/WeeklyLearnerDigestService.java`
- `services/api/src/main/java/com/verifiedai/analytics/WeeklyReportController.java`
- `services/api/src/main/java/com/verifiedai/analytics/AnalyticsPrivacyLifecycleContributor.java`
- `services/api/src/test/java/com/verifiedai/analytics/WeeklyLearnerDigestServiceTest.java`
- `services/api/src/test/java/com/verifiedai/analytics/WeeklyReportControllerSecurityTest.java`
- `services/api/src/test/java/com/verifiedai/analytics/WeeklyLearnerDigestMigrationIntegrationTest.java`

## Native iOS (`apps/ios`)
- `apps/ios/VerifiedAI/Features/Reports/WeeklyReportModels.swift`
- `apps/ios/VerifiedAI/Features/Reports/WeeklyReportService.swift`
- `apps/ios/VerifiedAI/Features/Reports/TutorSharePacketPDFRenderer.swift`
- `apps/ios/VerifiedAI/Features/Reports/WeeklyProgressReportViewModel.swift`
- `apps/ios/VerifiedAI/Features/Reports/WeeklyProgressReportView.swift`
- `apps/ios/VerifiedAI/App/AppDependencies.swift`
- `apps/ios/VerifiedAI/App/RootView.swift`
- `apps/ios/VerifiedAI/Features/Account/Presentation/AccountSettingsView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/Sprint89WeeklyReportTests.swift`
- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`

## Quality Gates & Acceptance
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_8_9_owner_acceptance.sh`

## Canonical Documentation
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.9_WEEKLY_LEARNING_REPORT_AND_EXPLAINABLE_RECOMMENDATIONS.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.9_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.9_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
