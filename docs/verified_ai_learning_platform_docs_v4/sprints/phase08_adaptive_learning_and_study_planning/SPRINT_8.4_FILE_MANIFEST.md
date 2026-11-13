# Sprint 8.4 File Manifest

All files created or updated for **Sprint 8.4 — Daily Coach Hub & Deterministic Study Plan Generation**.

## Layer 1 — PostgreSQL / Flyway Migration
- `services/api/src/main/resources/db/migration/platform/V047__daily_coach_sessions.sql` — `daily_coach_sessions` and `daily_coach_tasks` tables, bounded `CHECK` constraints, unique constraints, and BOLA/session lookup indexes.

## Layer 2 — JSON Schemas, Fixtures & OpenAPI 3.1 Contracts
- `packages/schemas/coach/daily-coach-v1.schema.json` — Draft 2020-12 contract with `additionalProperties: false`.
- `packages/schemas/coach/fixtures/daily-coach-v1.valid.json` — Validated reference payload fixture.
- `packages/contracts/openapi/public-api.yaml` — Added `GET /v1/coach/today-summary`, `PATCH /v1/coach/preferences`, `POST /v1/coach/missions/{id}/complete`, `POST /v1/coach/missions/{id}/downshift` and DTO schemas.
- `packages/openapi/verified-ai-core-api.yaml` — Synchronized copy of `public-api.yaml`.

## Layer 3 — Python Math Verifier SymPy Pre-Warmer
- `services/math-verifier/app/daily_coach_prewarmer.py` — Deterministic SymPy problem twin pre-warmer (`DailyCoachTwinPrewarmer`, zero LLM/HTTP dependencies).
- `services/math-verifier/tests/test_daily_coach_prewarmer.py` — 6-test pytest verification suite (`< 25 ms` batch execution).

## Layer 4 — Spring Modulith Backend (`com.verifiedai.studyplan.coach`)
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachMode.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionStatus.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachTaskType.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachTaskStatus.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionEntity.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachTaskEntity.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachTaskRepository.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionNotFoundException.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/CoachBriefingExplainer.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DeterministicCoachBriefingBuilder.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachSessionService.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachPrivacyPurgeContributor.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/DailyCoachController.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/dto/DailyCoachSummaryResponse.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/dto/DailyCoachTaskDto.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/dto/TrapRadarPreviewDto.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/dto/ColdStartDemoCardDto.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/dto/DailyCoachPreferencesRequest.java`
- `services/api/src/main/java/com/verifiedai/studyplan/coach/dto/DailyCoachTaskCompleteRequest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/coach/DailyCoachSessionServiceTest.java`
- `services/api/src/test/java/com/verifiedai/studyplan/coach/DailyCoachControllerTest.java`

## Layer 5 — Native iOS SwiftUI & Offline SQLite Persistence
- `apps/ios/VerifiedAI/Features/Home/Data/DailyCoachSQLiteCache.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/DailyCoachViewModel.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/DualModeSwitcher.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/CoachHubView.swift`
- `apps/ios/VerifiedAI/Features/StudyPlan/Presentation/DailyStudyPlanView.swift`
- `apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/Sprint84CoachHubTests.swift`

## Layer 6 — Quality & Automated Acceptance Tooling
- `scripts/quality/validate_json_schemas.py`
- `scripts/quality/sprint_8_4_owner_acceptance.sh`

## Layer 7 — Documentation & Manifest
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.4_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase08_adaptive_learning_and_study_planning/SPRINT_8.4_FILE_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/00_SPRINT_MASTER_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
