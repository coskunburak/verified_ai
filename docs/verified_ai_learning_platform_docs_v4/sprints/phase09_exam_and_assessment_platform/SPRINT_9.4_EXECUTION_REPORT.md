# Sprint 9.4 Execution Report — Timed Mock Exam Runtime, Server-Authoritative Clock & Photographed Deneme Scratchpad Ingestion (`F28`)

## Executive Summary
- **Status:** `LOCAL_ACCEPTANCE_PASSED` (`2026-11-15`; production rollout remains safely feature-flagged behind `verifiedai.exam.session-runtime-enabled: false` in `services/api/src/main/resources/application.yml` per `ADR-011`).
- Sprint 9.4 delivers `CNT-LOS-10`, `INV-EXAM-003`, and `F28` across PostgreSQL Flyway `V057`, Spring Modulith Java 21 (`com.verifiedai.exam`, Jackson 3 `tools.jackson.*` only), Draft 2020-12 JSON schemas + fixtures, byte-identical OpenAPI 3.1 twin specifications (`/v1/exams/sessions/**` and `/api/v1/exams/sessions/**`), and native Swift 6 Concept B iOS Screen 10 (`ExamSessionView` + `ExamSessionViewModel`).

## Implemented Invariants & Guardrails
- **Server-Authoritative Clock & 120s Offline Grace Buffer (`ExamClockPolicy`, `ExamSession`, `INV-EXAM-003`):** Countdown timers anchor strictly to server `started_at` and `server_deadline_at = started_at + effective_duration_seconds`. Client-side `clientTimestamp` manipulation cannot extend `server_deadline_at` or `grace_deadline_at`. Anomalies beyond +/-10,000 ms increment `verifiedai.exam.session.clock_skew_anomaly` telemetry without mutating server deadlines. Offline-replayed autosaves arriving within `<= 120s` grace transition the session to `OFFLINE_GRACE_BUFFER` (`grace_deadline_at = server_deadline_at + offline_grace_buffer_seconds`), while requests after `grace_deadline_at` auto-expire and seal the session (`EXPIRED_AUTO_SUBMITTED`).
- **Optimistic Concurrency & Idempotent Replay (`ExamSessionRuntimeService`, `ExamSessionItem`):** Every item mutation validates `expectedAutosaveVersion`, rejects stale updates with `409 EXAM_ITEM_STALE_VERSION`, persists canonical request payload SHA-256 hashes in `exam_session_idempotency_keys`, replays identical `Idempotency-Key` responses (`X-Idempotent-Replay: true`), and fails closed with `409 EXAM_SESSION_IDEMPOTENCY_CONFLICT` when an `Idempotency-Key` is reused with a different payload.
- **No-Hint Isolation & $0.00 Runtime LLM Spend (`ActiveExamNoHintGuard`, `ADR-005`, `ADR-006`):** Active sessions enforce `no_hint_policy_enforced = true`, blocking Socratic hints/walkthroughs (`403 EXAM_NO_HINT_MODE_ACTIVE` / `hintAllowed=false`) and keeping `llm_tokens_used = 0` and `training_eligible = false` across all runtime tables and API payloads. Post-exam review surfaces become eligible only once the session reaches a terminal state.
- **Photographed Deneme Scratchpad Ingestion (`ExamScratchpadAsset`, `F28`):** Enforces ownership-bound storage keys (`users/{userId}/exam-sessions/{sessionId}/scratchpad/...`), image MIME/size (`<= 10 MiB`) and SHA-256 hex bounds, up to 4 pages per item, and deterministic `F28VerificationBatchManifest` generation upon session submission (`QUEUED_FOR_F28_BATCH` / `EMPTY_NO_SCRATCHPADS`).
- **Native Swift 6 Concept B Screen 10 (`ExamSessionView`, `ExamSessionViewModel`, `ExamOfflineSQLiteStore`):** Flat zero-gradient, zero-shadow layout with remaining-time pill, `NO_HINT_MODE` badge, question navigator (`All`/`Flagged`/`Omitted`), strike-through elimination, paper deneme scratchpad capture (`PAPER_DENEME_BOOKLET`), local SQLite offline outbox (`exam_sessions_local`, `exam_autosave_outbox`, `exam_scratchpad_attachments_local`), multi-user BOLA SQLite isolation, and 21 EN/TR `Localizable.xcstrings` keys.

## Verification Results Observed in Workspace Sandbox (`BypassSandbox: false`)
- **Backend Unit, WebMvc, SQL Consistency, Privacy & Modulith/AI Boundary Suite:**
  - Command: `mvn -f services/api/pom.xml test -Dtest=ExamSessionTest,ExamSessionRuntimeServiceTest,V057MigrationSqlConsistencyTest,ExamSessionRuntimeControllerTest,ExamPrivacyLifecycleContributorTest,ModulithArchitectureTest,AiProviderBoundaryTest,RepositoryHierarchyTest`
  - Result: **20 / 20 tests passed, 0 failures, 0 errors, 0 skipped (`BUILD SUCCESS`).**
  - Note: `ExamSessionMigrationAndRepositoryTest.java` targets live PostgreSQL via Testcontainers and requires Docker daemon access outside the sandbox.
- **JSON Schemas, Negative Mutations, OpenAPI Mirrors & Localization:**
  - `python3 scripts/quality/validate_json_schemas.py`: **PASS** (`Sprint 8.1..9.4 JSON schemas, negative mutations, OpenAPI 3.1 endpoints, and 342 EN/TR localizations verified`).
  - `cmp -s packages/contracts/openapi/public-api.yaml packages/openapi/verified-ai-core-api.yaml`: **0 bytes diff.**
  - `python3 scripts/quality/check_contracts.py`, `python3 scripts/quality/check_sprint_6_10_localization.py`, `bash scripts/quality/secret_scan.sh`, & `python3 scripts/quality/docs_check.py`: **PASS.**
- **Swift 6 Compilation & Strict-Concurrency Typecheck:**
  - `xcrun --sdk iphonesimulator swiftc -typecheck -strict-concurrency=complete` across Core DesignSystem, Networking, Observability, Exam models/data/viewmodels/views, plus `ExamSessionViewModelTests.swift`, `ExamSessionSQLiteStoreTests.swift`, and `ExamSessionRuntimeUITests.swift`: **0 errors, 0 warnings.**
  - Note: CoreSimulatorService is sandbox-restricted on macOS inside the agent sandbox; run `bash scripts/quality/sprint_9_4_owner_acceptance.sh --full` on the host Mac for live Xcode Simulator test execution.
  - Acceptance runner: `bash scripts/quality/sprint_9_4_owner_acceptance.sh` completed cleanly and printed `LOCAL_ACCEPTANCE_PASSED`.

## Host Mac Full-Suite Verification & Cross-Module Hardening
- **Full Backend Maven Suite (`make test-api` with pinned `verified-ai/minio-test:2024-07-16-3535197`):**
  - `Tests run: 1227, Failures: 0, Errors: 0, Skipped: 16` (`BUILD SUCCESS`, zero Sprint 9.4 tests skipped).
  - `ExamSessionMigrationAndRepositoryTest`: `1 / 1` passed against PostgreSQL 16 (Flyway `V001..V057`, child-first deletion of `10` rows verified).
- **Host Xcode Simulator (`VerifiedAI` scheme, `iPhone 16 Pro`):**
  - Prior host run: all `VerifiedAITests` unit suites passed and `33 / 34` `VerifiedAIUITests` passed.
  - Targeted UI re-run after tab-navigation, hittability scrolling, and accessibility container fix (`xcodebuild ... -only-testing:VerifiedAIUITests/ExamSessionRuntimeUITests`): `** TEST SUCCEEDED **`.
- **Cross-Module Hardening Applied During Host Triage:**
  - `services/api/src/test/resources/application-test.yml`: merged base `test` profile settings (`app.ai.usage-ledger.enabled: false`, `verifiedai.studyplan.*`, `verifiedai.analytics.*`) so it no longer shadows `src/main/resources/application-test.yml`.
  - `services/api/src/main/java/com/verifiedai/identity/application/AccountPrivacyApplicationService.java`: added explicit `userRepository.save(user)` after `user.markDeleted(now)` so a contributor clearing the JPA persistence context (`clearAutomatically = true`) cannot leave `users.status` at `DELETION_IN_PROGRESS`.
  - `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`: reassigned Sprint 9.4 `ExamSession*` PBX object IDs to `A941...`/`B941...` (0 duplicate IDs across 618 objects, `plutil -lint` OK).
  - `apps/ios/VerifiedAITests/Exam/Sprint91ExamCatalogAndTargetTests.swift`: updated `DisabledExamService` stub to conform to the 6 Sprint 9.4 `ExamAPIServicing` methods.
