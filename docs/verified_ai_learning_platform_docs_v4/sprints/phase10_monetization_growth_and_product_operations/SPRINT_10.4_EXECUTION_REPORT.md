# Sprint 10.4 execution report

Status: `LOCAL_AUTOMATED_GATES_PASSED_MANUAL_GATES_PENDING` (`OWNER_RUNS_ALL_TESTS | NO_COMMIT | NO_PUSH | NO_DEPLOY | ZERO_SECRETS`). All Sprint 10.4 Flyway migration (`V068`), domain policy & state machine, PostgreSQL JDBC persistence & `FOR UPDATE SKIP LOCKED` dispatch worker, Spring Modulith `@NamedInterface("application")` SPI (`NotificationSchedulingPort`), GDPR export/deletion contributor, REST controllers, Draft 2020-12 JSON schemas & fixtures, byte-for-byte OpenAPI dual mirrors, iOS Concept B Preference Center (`SWIFT_STRICT_CONCURRENCY=complete`), A–E automated tests, quality contract scripts, and canonical docs are implemented on top of Sprint 10.3 (`V067`) and passed owner-executed automated local backend, contract, documentation, and iOS simulator gates.

## Owner commands

```bash
bash scripts/quality/sprint_10_4_owner_acceptance.sh && bash scripts/quality/sprint_10_4_swift_check.sh
```

## Owner local acceptance evidence

- **Backend, migration, concurrency, API, contracts, and docs (`2026-11-22`):** `bash scripts/quality/sprint_10_4_owner_acceptance.sh` completed with `PASS preflight`, `PASS backend` (`NotificationDeliveryStateMachineTest`, `QuietHoursPolicyTest`, `NotificationPolicyEvaluatorTest`, `Sprint104NotificationMigrationTest`, `NotificationSchedulingConcurrencyIT`, `NotificationApiSecurityAndGdprIT`, `Sprint103QuotaMigrationTest`, `ModulithArchitectureTest`, `ModularityTest`), `PASS contracts` (`validate_json_schemas.py`, `validate_sprint_10_3_contracts.py`, `validate_sprint_10_4_contracts.py`, `check_contracts.py`), `PASS documentation` (`450 Markdown files`), and final marker `SPRINT_10_4_OWNER_LOCAL_AUTOMATED_GATES_PASSED_MANUAL_GATES_PENDING` (`.generated/sprint-10-4-owner/20261122T112347Z-92835`).
- **iOS (`SWIFT_STRICT_CONCURRENCY=complete`, `2026-11-22`):** `bash scripts/quality/sprint_10_4_swift_check.sh` completed with `** TEST SUCCEEDED **` (`14` tests, `0` failures across `NotificationPreferencesViewModelTests`, `AccountSettingsViewModelTests`, and `AppRouterTests`) and `PASS local Sprint 10.4 notification simulator tests` (`.generated/sprint-10-4-ios/20261122T112600Z-93969/Sprint104Notifications.xcresult`).

## Implemented controls and invariants

- **Migration immutability & regression fix:** `V068__sprint_10_4_notification_infrastructure_and_preferences.sql` adds `notification_preferences`, `notification_devices`, `notification_deliveries`, and `notification_delivery_events` with `CHECK (NOT training_eligible)`, `ON DELETE CASCADE`, `uq_notification_devices_active_token_sha256`, `uq_notification_deliveries_user_idem`, and `idx_notification_deliveries_due_dispatch`. `V001`–`V067` (`V065`, `V066`, `V067`) remain byte-for-byte untouched, and `Sprint103QuotaMigrationTest.java` now pins `.target("67")` on its `upgrade` Flyway builder.
- **Zero-secret & fail-closed configuration:** `verifiedai.notification.enabled`, `verifiedai.notification.dispatch-worker-enabled`, and `verifiedai.notification.apns-live-delivery-enabled` default to `false` in `application.yml`. `NotificationProperties` fails closed at startup if `apnsLiveDeliveryEnabled` is `true` or bounds are violated. `VerifiedAiApplication.java` has no `@EnableScheduling`; scheduling is isolated to `NotificationDispatchSchedulerConfig` gated on `dispatch-worker-enabled=true`. Live push uses deterministic `NoOpPushDeliveryGateway` with zero external network calls and `$0.00` LLM cost.
- **Privacy, PII minimization & GDPR lifecycle:** `NotificationPayloadHasher` rejects forbidden PII keys (`email`, `phone`, `deviceToken`, `rawProblemText`, `studentName`, `jwt`, etc.) and enforces `<= 8` keys, `<= 80` chars/value, and `<= 2048` bytes. Raw `device_token` is never exposed in API DTOs, logs, Micrometer tags, or `NotificationLifecycleContributor.exportUserData(UUID)` (only `tokenLast4`). Cross-user token registration atomically revokes prior active ownership (`takeover_revoked`). `deleteUserData` explicitly deletes all 4 tables alongside `ON DELETE CASCADE`.
- **DST-deterministic quiet hours & concurrency:** `QuietHoursPolicy` evaluates `ZonedDateTime` with `withLaterOffsetAtOverlap()` for fall-back DST overlaps and forward gap resolution for spring-forward transitions, suppressing expired deferrals (`INTENT_EXPIRED`). `NotificationSchedulingService` serializes per user via `pg_advisory_xact_lock` in `REQUIRES_NEW` transactions; `NotificationDispatchService` claims due rows via `FOR UPDATE SKIP LOCKED`, checks local-calendar-day `daily_cap`, handles stale locks, and schedules exponential retry backoff.
- **iOS Preference Center (`SWIFT_STRICT_CONCURRENCY=complete`):** `NotificationPreferencesViewModel` and `NotificationPreferencesView` provide calm opt-in defaults, pre-prompt & `.denied` OS permission handling (via `PushPermissionManaging` without calling `UNUserNotificationCenter` in XCTests), `409 OPTIMISTIC_CONFLICT` automatic reload recovery, `ViewThatFits` / `minHeight` responsive layouts (`320pt` / `xSmall`–`AX3`), complete `en`/`tr` strings in `Localizable.xcstrings` (zero stigmatizing words), and `verified-ai://notifications` & `verified-ai://notification-preferences` deep links.

## Rollback plan

Keep `verifiedai.notification.enabled=false`, `verifiedai.notification.dispatch-worker-enabled=false`, and `verifiedai.notification.apns-live-delivery-enabled=false` (default) to fail-close all notification REST routes (`503 NOTIFICATION_FEATURE_DISABLED`) and SPI `schedule(...)` calls (`SUPPRESSED` / `FEATURE_DISABLED`) immediately without reverting `V068` or modifying `V065`–`V067`.

## Release blockers (manual sign-off required — `PENDING_MANUAL`)

- Staging P95 latency verification under realistic load.
- Physical iOS device accessibility & layout inspection (EN/TR VoiceOver, `320pt`, `xSmall`–`AX3`, Light/Dark, Bold Text, Reduce Motion).
- Production Apple APNs `.p8` key / Team ID / Key ID owner-only provisioning in secret manager (never stored in repository).

## Changed and added file groups

- **Migration & configuration:** `V068__sprint_10_4_notification_infrastructure_and_preferences.sql`, `application.yml`, `ApiErrorCode.java`
- **Backend `com.verifiedai.notification`:** `domain/**` (13 files), `application/**` (16 files including `@NamedInterface("application")` & `NotificationSchedulingPort`), `infrastructure/**` (8 files including `NoOpPushDeliveryGateway` & isolated `NotificationDispatchSchedulerConfig`), `api/**` (10 files including `dto/*` and 3 controllers)
- **Contracts & validators:** `packages/schemas/notification/*.schema.json` (5 schemas), `packages/schemas/notification/fixtures/*.valid.json` (6 fixtures), `packages/contracts/openapi/public-api.yaml`, `packages/openapi/verified-ai-core-api.yaml`, `scripts/quality/validate_sprint_10_4_contracts.py`, `scripts/quality/validate_json_schemas.py`
- **iOS app & tests:** `apps/ios/VerifiedAI/Features/Notifications/**` (7 Swift files), `AppDependencies.swift`, `AppRouter.swift`, `DeepLinkRouter.swift`, `RootView.swift`, `AccountSettingsView.swift`, `Localizable.xcstrings`, `project.pbxproj`, `apps/ios/VerifiedAITests/Notifications/NotificationPreferencesViewModelTests.swift`
- **Backend tests & owner scripts:** `NotificationDeliveryStateMachineTest.java`, `QuietHoursPolicyTest.java`, `NotificationPolicyEvaluatorTest.java`, `Sprint104NotificationMigrationTest.java`, `NotificationSchedulingConcurrencyIT.java`, `NotificationApiSecurityAndGdprIT.java`, `Sprint103QuotaMigrationTest.java` (`.target("67")` pin), `ModulithArchitectureTest.java`, `sprint_10_4_owner_acceptance.sh`, `sprint_10_4_swift_check.sh`
- **Documentation:** `SPRINT_10.4_IMPLEMENTATION_MAP.md`, `SPRINT_10.4_EXECUTION_REPORT.md`, `data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`, `DOCUMENTATION_MANIFEST.md`
