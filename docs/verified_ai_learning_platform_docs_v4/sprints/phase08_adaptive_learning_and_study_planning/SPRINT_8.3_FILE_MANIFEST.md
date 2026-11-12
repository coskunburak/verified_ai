# Sprint 8.3 Deliverable File Manifest

2026-11-13 · `SPRINT_8_3_DELIVERABLES`

| Category | File Path | Status | Purpose / Invariants |
| :--- | :--- | :--- | :--- |
| **Database Migration** | `services/api/src/main/resources/db/migration/platform/V046__spaced_review_queue.sql` | `CREATED` | Flyway migration for `spaced_review_queue` table and indexes (`INV-SR-001..006`) |
| **JSON Schema** | `packages/schemas/studyplan/spaced-queue-v1.schema.json` | `CREATED` | JSON Schema Draft 2020-12 contract (`additionalProperties: false`) |
| **Schema Fixtures** | `packages/schemas/studyplan/fixtures/spaced-queue-v1.valid.json` | `CREATED` | Valid fixture representing a 3-item interleaved queue |
| **Schema Examples** | `packages/schemas/studyplan/examples/spaced-queue-v1.valid.json` | `CREATED` | Mirrored valid fixture example |
| **Schema Validator** | `scripts/quality/validate_json_schemas.py` | `MODIFIED` | Draft 2020-12 validator with 5 negative mutation tests for Sprint 8.3 |
| **OpenAPI Contract** | `packages/contracts/openapi/public-api.yaml` | `MODIFIED` | Added 3 spaced queue endpoints and 4 component schemas |
| **OpenAPI Mirror** | `packages/openapi/verified-ai-core-api.yaml` | `MODIFIED` | Mirrored public API contract |
| **Math Verifier Engine** | `services/math-verifier/app/spaced_twin_scheduler.py` | `CREATED` | Tier-1 SymPy twin scheduler for 7-day trap immunity with `$0.00` LLM cost |
| **Math Verifier Tests** | `services/math-verifier/tests/test_spaced_twin_pipeline.py` | `CREATED` | 6 tests verifying twin determinism, 168h window helper, and structural families |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedTriggerType.java` | `CREATED` | `RETENTION_DECAY`, `TRAP_IMMUNITY_7D` |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedQueueStatus.java` | `CREATED` | `PENDING`, `SERVED`, `COMPLETED`, `DISMISSED`, `EXPIRED` |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/MistakeResolutionStatus.java` | `CREATED` | `NONE`, `STABLE_RESOLVED`, `RELAPSED` |
| **Backend Domain Model** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewCandidate.java` | `CREATED` | Domain record for candidate prioritization and interleaving |
| **Backend Scheduler** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedRepetitionScheduler.java` | `CREATED` | Pure domain service for $R(t)$, $t_{\text{due}}$ inversion, and interleaving |
| **Backend Entity** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewQueueEntity.java` | `CREATED` | JPA entity mapping `spaced_review_queue` table |
| **Backend Repository** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewQueueRepository.java` | `CREATED` | Spring Data JPA repository with BOLA queries and single-query purge |
| **Backend Exception** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedQueueItemNotFoundException.java` | `CREATED` | Mapped to HTTP 404 RFC 9457 `ProblemDetail` (BOLA protection) |
| **Backend Service** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewService.java` | `CREATED` | Queue synchronization, candidate interleaving, and completion state machine |
| **Privacy Contributor** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewPrivacyPurgeContributor.java` | `CREATED` | Implements `AccountDataLifecycleContributor` ($< 500\text{ ms}$ purge) |
| **Backend DTOs** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/dto/*.java` | `CREATED` | 5 request/response DTOs matching JSON Schema contract |
| **REST Controller** | `services/api/src/main/java/com/verifiedai/studyplan/spaced/SpacedReviewController.java` | `CREATED` | REST controller for `/v1/study-plan/spaced-queue` endpoints |
| **Backend Tests** | `services/api/src/test/java/com/verifiedai/studyplan/spaced/SpacedRepetitionSchedulerTest.java` | `CREATED` | 5 unit tests for decay curve, inversion, 168h window, and half-life updates |
| **Backend Tests** | `services/api/src/test/java/com/verifiedai/studyplan/spaced/SpacedReviewServiceTest.java` | `CREATED` | 4 unit tests for 47-item cap, state transitions, BOLA 404, and privacy purge |
| **Backend Tests** | `services/api/src/test/java/com/verifiedai/studyplan/spaced/SpacedReviewControllerTest.java` | `CREATED` | 4 MockMvc tests for 200, 404 (BOLA), and 422 (rapid guess) |
| **Backend Tests** | `services/api/src/test/java/com/verifiedai/studyplan/spaced/ModularityTests.java` | `CREATED` | Spring Modulith architectural boundary verification test |
| **iOS Models** | `apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewModels.swift` | `CREATED` | Swift Codable/Sendable models for spaced repetition and immunity |
| **iOS SQLite Cache** | `apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewSQLiteCache.swift` | `CREATED` | Native `sqlite3` offline cache with `SQLITE_TRANSIENT` and $< 50\text{ ms}$ purge |
| **iOS ViewModel** | `apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewViewModel.swift` | `CREATED` | MainActor ViewModel with 3-item cap defense and offline fallback |
| **iOS Views** | `apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/SpacedReviewCardView.swift` | `CREATED` | Accessible card view using `NativeMathLabel` and non-stigmatizing badges |
| **iOS Views** | `apps/ios/VerifiedAI/Features/StudyPlan/SpacedReview/DailyStudyPlanView.swift` | `CREATED` | Daily study plan container view mounting `SpacedReviewCardView` |
| **iOS Views** | `apps/ios/VerifiedAI/Features/Home/Views/CoachHubView.swift` | `CREATED` | Coach hub view mounting recommendations (Block 1) & spaced review (Block 2) |
| **iOS Views** | `apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift` | `MODIFIED` | Added `spacedReviewSection` with `SpacedReviewCardView` |
| **iOS Localization** | `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | `MODIFIED` | 6 new non-stigmatizing keys in English and Turkish |
| **iOS Tests** | `apps/ios/VerifiedAITests/Sprint83SpacedRepetitionTests.swift` | `CREATED` | 4 XCTest unit test suites for decoding, cache, ViewModel, and NativeMathLabel |
| **Quality Gate Runner**| `scripts/quality/sprint_8_3_owner_acceptance.sh` | `CREATED` | 7-stage deterministic owner acceptance runner (`--full`, `--quick`, `--stage`) |
| **Execution Report** | `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.3_EXECUTION_REPORT.md` | `CREATED` | Complete execution report and invariant validation matrix |
| **File Manifest** | `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.3_FILE_MANIFEST.md` | `CREATED` | Complete manifest of all Sprint 8.3 deliverables |
| **Sprint Master Plan** | `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md` | `MODIFIED` | Marked Sprint 8.3 as completed with reference to execution report |
