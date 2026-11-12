# Sprint 8.2 Deliverable File Manifest

2026-11-12 · `SPRINT_8_2_DELIVERABLES`

| Category | File Path | Status | Purpose / Invariants |
| :--- | :--- | :--- | :--- |
| **Database Migration** | `services/api/src/main/resources/db/migration/platform/V044__adaptive_practice_sessions.sql` | `CREATED` | Flyway schema for `practice_sessions` & `practice_session_items` (`INV-PRAC-001..006`) |
| **JSON Schema** | `packages/schemas/studyplan/practice-session-v1.schema.json` | `CREATED` | JSON Schema Draft 2020-12 contract (`additionalProperties: false`) |
| **Schema Fixtures** | `packages/schemas/studyplan/fixtures/practice-session-v1.valid.json` | `CREATED` | Valid fixture representing a 4-item `DAILY_1_2_1` session |
| **Schema Examples** | `packages/schemas/studyplan/examples/practice-session-v1.valid.json` | `CREATED` | Mirrored fixture example |
| **OpenAPI Specification** | `packages/contracts/openapi/public-api.yaml` | `MODIFIED` | 4 practice session endpoints and component schemas |
| **OpenAPI Mirror** | `packages/openapi/verified-ai-core-api.yaml` | `CREATED` | Mirrored OpenAPI specification |
| **Schema Validator** | `scripts/quality/validate_json_schemas.py` | `MODIFIED` | Draft 2020-12 validator with 5 negative mutations for Sprint 8.2 |
| **Math Verifier Engine** | `services/math-verifier/app/twin_generator.py` | `CREATED` | Tier-1 deterministic twin generator for 4 structural trap families |
| **Math Verifier Evaluator** | `services/math-verifier/app/answer_evaluator.py` | `CREATED` | SymPy solution equivalence checker & trap diagnostic detector |
| **Math Verifier Router** | `services/math-verifier/app/routers/practice_verifier.py` | `CREATED` | FastAPI endpoints for twin synthesis and submission evaluation |
| **Math Verifier App** | `services/math-verifier/app/main.py` | `MODIFIED` | Mounted `practice_verifier` router |
| **Math Verifier Tests** | `services/math-verifier/tests/test_twin_structural_invariants.py` | `CREATED` | 100 twin invariant verification tests across 25 deterministic seeds |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionMode.java` | `CREATED` | `DAILY_1_2_1`, `TRAP_VERIFICATION_DRILL`, `SPACED_REVIEW`, `EXAM_ROI_SYNTHESIS` |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionStatus.java` | `CREATED` | `IN_PROGRESS`, `COMPLETED`, `ABANDONED` |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSlotRole.java` | `CREATED` | `TRAP_CLOSURE`, `SPACED_DRILL`, `EXAM_SYNTHESIS` |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeQuestionSource.java` | `CREATED` | `CURATED_BANK`, `DETERMINISTIC_TWIN_TIER1`, `SYNTHESIZED_TWIN_TIER2` |
| **Backend Domain Enums** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeItemStatus.java` | `CREATED` | `PENDING`, `SUBMITTED_CORRECT`, `SUBMITTED_INCORRECT`, `SKIPPED` |
| **Backend Entities** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionEntity.java` | `CREATED` | JPA entity for practice session |
| **Backend Entities** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionItemEntity.java` | `CREATED` | JPA entity for practice session items |
| **Backend Repositories** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionRepository.java` | `CREATED` | Spring Data repository with BOLA queries and bulk purge |
| **Backend Repositories** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionItemRepository.java` | `CREATED` | Spring Data repository for session items |
| **Backend Exceptions** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionNotFoundException.java` | `CREATED` | Mapped to HTTP 404 (BOLA security enforcement) |
| **Backend Exceptions** | `services/api/src/main/java/com/verifiedai/studyplan/practice/RapidGuessGuardException.java` | `CREATED` | Mapped to HTTP 422 (`RAPID_GUESS_GUARD_TRIGGERED`) |
| **Curated Bank** | `services/api/src/main/java/com/verifiedai/studyplan/practice/CuratedPracticeQuestion.java` | `CREATED` | Curated question record definition |
| **Curated Bank** | `services/api/src/main/java/com/verifiedai/studyplan/practice/CuratedPracticeQuestionBank.java` | `CREATED` | In-memory verified algebra & calculus question catalog |
| **Twin Verifier Client** | `services/api/src/main/java/com/verifiedai/studyplan/practice/SymPyTwinVerifierClient.java` | `CREATED` | Twin synthesis & evaluation client with local math engine |
| **Adaptive Selector** | `services/api/src/main/java/com/verifiedai/studyplan/practice/AdaptivePracticeQuestionSelector.java` | `CREATED` | 1-2-1 selector with BigDecimal ZPD calculation & candidate utility scoring |
| **Backend Service** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionService.java` | `CREATED` | Orchestration, 60-second lock, rapid-guess guard, BOLA checks |
| **Privacy Contributor** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionPrivacyPurgeContributor.java` | `CREATED` | Platform privacy lifecycle contributor for single-query deletion |
| **REST Controller** | `services/api/src/main/java/com/verifiedai/studyplan/practice/PracticeSessionController.java` | `CREATED` | REST endpoints under `/v1/study-plan/practice/sessions` |
| **Backend DTOs** | `services/api/src/main/java/com/verifiedai/studyplan/practice/dto/*.java` | `CREATED` | 8 request/response DTOs matching JSON Schema contract |
| **iOS Models** | `apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionModels.swift` | `CREATED` | Swift Codable/Sendable models for practice sessions and items |
| **iOS SQLite Cache** | `apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSQLiteCache.swift` | `CREATED` | Native `sqlite3` C API offline caching & user purge |
| **iOS ViewModel** | `apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionViewModel.swift` | `CREATED` | MainActor ViewModel with 1s countdown timer and urgency phases |
| **iOS Views** | `apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeQuestionCardView.swift` | `CREATED` | Accessible card view with NativeMathLabel and 60s countdown ring |
| **iOS Views** | `apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionView.swift` | `CREATED` | Complete 1-2-1 practice session flow with zero WebViews |
| **iOS Localization** | `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | `MODIFIED` | 11 new non-stigmatizing keys in English and Turkish |
| **Backend Tests** | `services/api/src/test/java/com/verifiedai/studyplan/practice/AdaptivePracticeSelectorTest.java` | `CREATED` | 5 unit tests for 1-2-1 allocation, ZPD clamping, and fail-closed fallback |
| **Backend Tests** | `services/api/src/test/java/com/verifiedai/studyplan/practice/PracticeSessionServiceTest.java` | `CREATED` | 5 unit tests for 60s lock, rapid-guess guard, trap closure, BOLA, purge |
| **Backend Tests** | `services/api/src/test/java/com/verifiedai/studyplan/practice/PracticeSessionControllerTest.java` | `CREATED` | 6 MockMvc tests for all 4 endpoints (201, 200, 404, 422) |
| **iOS Tests** | `apps/ios/VerifiedAITests/Sprint82PracticeTests.swift` | `CREATED` | Unit tests for JSON decoding, countdown transitions, hint lock, SQLite |
| **Execution Report** | `SPRINT_8.2_EXECUTION_REPORT.md` | `CREATED` | Executive summary, invariant validation, and test results |
