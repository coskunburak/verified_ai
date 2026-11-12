# Sprint 8.1 File Manifest

Paths are repository-relative. No files were deleted.

| Change | File | Purpose |
|---|---|---|
| Added | `services/api/src/main/resources/db/migration/platform/V043__next_best_action_recommendations.sql` | Flyway DDL creating `learner_recommendations` with check constraints and composite indexes. |
| Modified | `services/api/src/main/java/com/verifiedai/studyplan/package-info.java` | Spring Modulith module definition with allowed dependencies. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/RecommendationType.java` | Enum defining recommendation types and tie-breaking order. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/RecommendationRationaleCode.java` | Enum defining structured rationale codes. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/RecommendationStatus.java` | Enum defining lifecycle statuses (`ACTIVE`, `ACCEPTED`, `DISMISSED`, `EXPIRED`). |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/LearnerRecommendationEntity.java` | JPA entity mapped to `learner_recommendations` table with state transition methods. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/LearnerRecommendationRepository.java` | Spring Data JPA repository for recommendations with bulk expiration and GDPR deletion. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/RecommendationStateConflictException.java` | Exception for illegal recommendation lifecycle transitions (HTTP 409). |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/RecommendationNotFoundException.java` | Exception for non-existent recommendation lookups (HTTP 404). |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/domain/RecommendationAccessDeniedException.java` | Exception for BOLA authorization violations (HTTP 403). |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/engine/SkillCandidateSignal.java` | Record representing incoming candidate skill signals. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/engine/ScoredRecommendation.java` | Record representing calculated recommendations and rationale parameters. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/engine/NextBestActionRecommendationEngine.java` | Pure deterministic calculation engine implementing `INV-COACH-001`, `INV-COACH-002`, and `INV-COACH-003`. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/service/NextBestActionSignalProvider.java` | Interface for retrieving learner skill candidate signals. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/service/DefaultNextBestActionSignalProvider.java` | Component collecting database signals and providing cold-start defaults via `JdbcClient`. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/service/StudyPlanService.java` | Service orchestrating recommendation caching, 24h TTL, BOLA checks, and status mutations. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/privacy/StudyPlanPrivacyLifecycleContributor.java` | Implements `AccountDataLifecycleContributor` for single-query bulk purge and data export. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/api/dto/LearnerRecommendationDto.java` | DTO representing individual recommendations. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/api/dto/LearnerRecommendationsResponse.java` | Response wrapper with $0.00 token cost guarantee and NBA-v1.0 engine version. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/api/dto/RecommendationActionResponse.java` | Action response containing new status, session type, and deep link URI. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/api/dto/DismissRecommendationRequest.java` | Request body for recommendation dismissal. |
| Added | `services/api/src/main/java/com/verifiedai/studyplan/api/RecommendationController.java` | REST controller exposing `/v1/study-plan/recommendations` endpoints with BOLA validation. |
| Added | `services/api/src/test/java/com/verifiedai/studyplan/engine/NextBestActionRecommendationEngineTest.java` | Unit tests for mathematical formula precision, prerequisite override, and tie-breaking determinism. |
| Added | `services/api/src/test/java/com/verifiedai/studyplan/service/StudyPlanServiceIntegrationTest.java` | Integration tests for recommendation lifecycle, BOLA protection, and conflict handling. |
| Added | `services/api/src/test/java/com/verifiedai/studyplan/privacy/StudyPlanPrivacyLifecycleContributorTest.java` | Unit tests for $< 500\text{ ms}$ single-query purge and non-stigmatizing export. |
| Added | `services/api/src/test/java/com/verifiedai/studyplan/api/RecommendationControllerTest.java` | MockMvc contract tests for GET, accept, dismiss (200, 401, 403, 404, 409). |
| Added | `packages/schemas/studyplan/learner-recommendations-v1.schema.json` | JSON Schema Draft 2020-12 specification for next-best-action recommendations. |
| Added | `packages/schemas/studyplan/fixtures/learner-recommendations-v1.valid.json` | Canonical positive fixture for learner recommendations. |
| Modified | `packages/contracts/openapi/public-api.yaml` | Registered `/v1/study-plan/recommendations` endpoints, requests, and response schemas. |
| Modified | `scripts/quality/validate_json_schemas.py` | Added validation for Sprint 8.1 schema, valid fixture, negative mutations, and OpenAPI paths. |
| Added | `apps/ios/VerifiedAI/Features/Home/Domain/RecommendationModels.swift` | Swift domain and DTO models for recommendations, rationale codes, and statuses. |
| Added | `apps/ios/VerifiedAI/Features/Home/Cache/RecommendationSQLiteCache.swift` | Native SQLite3 offline cache for recommendations. |
| Added | `apps/ios/VerifiedAI/Features/Home/ViewModels/CoachHubViewModel.swift` | MainActor ViewModel for recommendations with offline fallback and optimistic transitions. |
| Added | `apps/ios/VerifiedAI/Features/Home/Views/RecommendationCardView.swift` | SwiftUI recommendation card with progress indicator, native math preview, and VoiceOver tags. |
| Modified | `apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift` | Integrated CoachHub recommendations section into home view. |
| Modified | `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | Added 12 new localization keys for CoachHub NBA in English and Turkish. |
| Added | `apps/ios/VerifiedAITests/Sprint81RecommendationTests.swift` | iOS unit tests for decoding, priority sorting, ViewModel mutations, and offline cache fallback. |
| Modified | `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` | Registered new Swift files and fixtures in Xcode project build phases. |
| Added | `scripts/quality/sprint_8_1_owner_acceptance.sh` | 7-stage deterministic owner acceptance suite. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.1_EXECUTION_REPORT.md` | Formal Sprint 8.1 execution report and Phase 08 Foundation certification. |
| Added | `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.1_FILE_MANIFEST.md` | Complete file inventory. |
