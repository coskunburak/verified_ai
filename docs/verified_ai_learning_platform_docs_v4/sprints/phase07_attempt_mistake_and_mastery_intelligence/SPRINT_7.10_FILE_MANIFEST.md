# Sprint 7.10 File Manifest

Paths are repository-relative. No files were deleted.

| Change | File | Purpose |
|---|---|---|
| Added | `services/api/src/main/resources/db/migration/platform/V042__mastery_calibration_and_retention_snapshots.sql` | Flyway DDL for learner mastery calibration records with range check constraints and indexes. |
| Added | `services/api/src/main/java/com/verifiedai/analytics/BrierAndEceCalculator.java` | Pure psychometric engine with fixed-point BigDecimal arithmetic, 10-bin ECE, and Brier calculations. |
| Added | `services/api/src/main/java/com/verifiedai/analytics/LearnerMasteryCalibrationRecordEntity.java` | JPA Entity mapped to `learner_mastery_calibration_records`. |
| Added | `services/api/src/main/java/com/verifiedai/analytics/LearnerMasteryCalibrationRepository.java` | Spring Data JPA repository with bulk delete and range queries. |
| Added | `services/api/src/main/java/com/verifiedai/analytics/MasteryCalibrationSummaryDto.java` | Immutable DTO records for mastery calibration summary, reliability bins, and skill slices. |
| Added | `services/api/src/main/java/com/verifiedai/analytics/MasteryCalibrationEvaluationService.java` | Service managing idempotent ingestion and multi-skill temporal cohort evaluations. |
| Added | `services/api/src/main/java/com/verifiedai/analytics/AnalyticsPrivacyLifecycleContributor.java` | Implements `AccountDataLifecycleContributor` for single-query bulk deletion and export. |
| Added | `services/api/src/main/java/com/verifiedai/analytics/MasteryCalibrationAdminController.java` | Admin REST controller exposing `GET /v1/admin/calibration/mastery-summary` with `ROLE_ADMIN`. |
| Modified | `services/api/src/main/java/com/verifiedai/configuration/SecurityConfiguration.java` | Enables `@EnableMethodSecurity`, adds role converter, and protects `/v1/admin/**` with `ROLE_ADMIN`. |
| Added | `services/api/src/test/java/com/verifiedai/analytics/BrierAndEceCalculatorTest.java` | Unit tests for 10-bin boundary mapping, empty cohort, and calibration regime detection. |
| Added | `services/api/src/test/java/com/verifiedai/analytics/MasteryCalibrationEvaluationServiceTest.java` | Unit tests for idempotent record ingestion and multi-skill slice aggregation. |
| Added | `services/api/src/test/java/com/verifiedai/analytics/AnalyticsPrivacyLifecycleContributorTest.java` | Unit tests for $< 500\text{ ms}$ bulk purge and sanitized data export. |
| Added | `services/api/src/test/java/com/verifiedai/analytics/MasteryCalibrationAdminControllerTest.java` | MockMvc integration test verifying `ROLE_ADMIN` access control and contract-compliant JSON response. |
| Added | `packages/schemas/analytics/mastery-calibration-summary-v1.schema.json` | JSON Schema Draft 2020-12 specification for mastery calibration summary. |
| Added | `packages/schemas/analytics/fixtures/mastery-calibration-summary-v1.valid.json` | Canonical positive fixture for mastery calibration summary with 10 bins. |
| Modified | `packages/contracts/openapi/public-api.yaml` | Registered `/v1/admin/calibration/mastery-summary` endpoint and `ProblemForbidden` response. |
| Modified | `scripts/quality/validate_json_schemas.py` | Added validation for Sprint 7.10 schemas, fixtures, negative mutations, and OpenAPI paths. |
| Added | `services/math-verifier/tests/test_golden_trajectories_v710.py` | Golden trajectory tests for all 10 learner archetypes and cohort calibration gate proof. |
| Added | `apps/ios/VerifiedAI/Features/MasteryGraph/Domain/MasteryCalibrationModels.swift` | Swift domain models for mastery calibration summary and reliability bins. |
| Modified | `apps/ios/VerifiedAI/Features/AttemptAnalysis/Views/StepDiffNativeMathRenderer.swift` | Added `downstreamPropagation` non-penalizing highlight role. |
| Added | `apps/ios/VerifiedAITests/Sprint710MasteryCalibrationTests.swift` | Unit tests for model decoding, retention decay threshold, downstream non-penalizing state, and logout cache purge. |
| Modified | `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` | Registered new Swift files and fixtures in Xcode project build phases. |
| Added | `scripts/quality/sprint_7_10_owner_acceptance.sh` | 7-stage deterministic owner acceptance suite. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.10_EXECUTION_REPORT.md` | Formal Sprint 7.10 execution report and Phase 07 Exit Gate certification. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.10_FILE_MANIFEST.md` | Complete file inventory. |
