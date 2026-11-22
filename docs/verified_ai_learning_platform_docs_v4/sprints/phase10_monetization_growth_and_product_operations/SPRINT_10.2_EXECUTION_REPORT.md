# Sprint 10.2 execution report

Status: `IMPLEMENTED_NOT_RUN`. All Sprint 10.2 database migration, backend policy/guards, JSON Schema/OpenAPI contracts, iOS client/UI updates, A–F test sources, and owner acceptance scripts are implemented on `main` over `46d31b12bb230a027adbec92423530a0bd732c1a`, with **zero tests, builds, validators, or migrations executed by the agent** (`OWNER_RUNS_ALL_TESTS`, `NO_COMMIT`, `NO_PUSH`, `NO_DEPLOY`). Prior Sprint 9.10 and Sprint 10.1 owner acceptance artifacts are preserved untouched and are not carried forward as Sprint 10.2 passes.

## Owner commands

```bash
bash scripts/quality/sprint_10_2_owner_acceptance.sh --full && bash scripts/quality/sprint_10_2_swift_check.sh
```

## Implemented enforcement and downgrade behavior

- `V066__sprint_10_2_entitlement_state_and_transition_audit.sql` extends `entitlements` with NULL-safe `package_code` and `grace_period_expires_at` (backfilled non-destructively from existing rows and verified `offer_family = 'YKS_SEASON_PASS'` transactions), creates `billing_entitlement_transition_audit` (`training_eligible = false`, `ON DELETE CASCADE`), and adds `app_store_transactions.access_revoked` plus `app_store_subscriptions.state_signed_at`.
- `LearningOsTierCapabilityPolicy` projects `FREE`, `PRO`, `COACH_PRO`, and `YKS_SEASON_PASS` (`effectiveTier = COACH_PRO`) across the 8 `LearningOsCapability` actions while preserving the 7 legacy `PremiumCapability` items (`BASIC_SOLVE` baseline on `FREE`). `TUTOR_COPILOT_PACK` stays non-purchasable (`NOT_AVAILABLE`).
- `GRACE_PERIOD` grants paid access only while `gracePeriodExpiresAt > now`; `BILLING_RETRY` grants paid access only within verified expiry/grace bounds; `EXPIRED`, `REFUND`, and `REVOKED` downgrade immediately to `FREE`. Stale transaction/notification replays cannot resurrect a revoked transaction.
- `CapabilityAccessPolicy` (exported from `billing::application`) guards all 11 downstream services behind `verifiedai.billing.learning-os-tiers-enabled` (`false` by default) with fail-closed `503 ENTITLEMENT_SYNC_FAILED` on missing billing policy or database failure, while preserving non-punitive read-only summaries, active mock exam resume, `DIAGNOSTIC_BASELINE`, previously earned micro-lessons, and BOLA checks before entitlement gates.
- iOS `EntitlementViewModel` fetches `GET /v1/billing/entitlements` (server-authoritative), scopes `EntitlementDisplayCache` by `(user, session)` with async generation guards, suppresses contextual paywalls during active exam/diagnostic/twin timers, and renders flat Concept B banners and `LearningOsActionGate` notices across `en-US` and `tr-TR`.

## Explicit scope exclusions

- No Sprint 10.3 `entitlement_quota_ledgers`, daily/monthly solve or twin counters, AI cost budgets, or credit wallets.
- No Phase 12.7 `TUTOR_COPILOT_PACK` student data sharing or live App Store Connect SKU prices.

## Release blockers (owner execution and manual sign-off required)

- Execute `bash scripts/quality/sprint_10_2_owner_acceptance.sh --full` and `bash scripts/quality/sprint_10_2_swift_check.sh` on a clean owner host with Java, Maven, Docker (PostgreSQL 16 Testcontainers), Python venv dependencies, Xcode, and iOS Simulator.
- Keep `verifiedai.billing.learning-os-tiers-enabled: false` in production config until staging P95 latency (`< 350ms` exam / `< 150ms` entitlement), physical-device EN/TR VoiceOver (`320pt`, `xSmall`–`AX3`, light/dark, Bold Text, Reduce Motion), App Store Connect commercial metadata/legal links, and explicit owner sign-off complete (`PENDING_MANUAL`).

## Changed and added file groups

- **Migration:** `services/api/src/main/resources/db/migration/platform/V066__sprint_10_2_entitlement_state_and_transition_audit.sql`
- **Backend billing & shared kernel:** `CapabilityAccessPolicy.java`, `CapabilityDecision.java`, `EffectiveEntitlementAccess.java`, `LearningOsCapability.java`, `LearningOsTierCapabilityPolicy.java`, `TierResolutionPolicy.java`, `EntitlementApplicationService.java`, `EntitlementMetrics.java`, `AppleBillingApplicationService.java`, `BillingLifecycleContributor.java`, `BillingReadController.java`, `EntitlementJpaEntity.java`, `AppStoreBillingRepository.java`, `ApiErrorCode.java`, `application.yml`
- **Downstream guards (6 modules, 11 services + DTO/controller):** `AttemptEvaluationService.java`, `AttemptLearningService.java`, `AttemptWorkService.java`, `ExamReadinessProjectionService.java`, `ExamSessionRuntimeService.java`, `CounterfactualTwinChallengeService.java`, `LearnerMistakeDnaService.java`, `MistakeDnaController.java`, `MistakePassportReadModelService.java`, `MistakePassportResponseDto.java`, `DailyCoachSessionService.java`, `SpacedReviewService.java`, `MicroLessonApplicationService.java`
- **Contracts & validators:** `packages/schemas/entitlement-v2.schema.json`, `packages/schemas/fixtures/entitlement-v2.cases.json`, `packages/contracts/openapi/public-api.yaml`, `packages/openapi/verified-ai-core-api.yaml`, `scripts/quality/validate_sprint_10_2_contracts.py`, `scripts/quality/validate_json_schemas.py`
- **iOS app & tests:** `EntitlementModels.swift`, `EntitlementAPI.swift`, `EntitlementDisplayCache.swift`, `EntitlementViewModel.swift`, `PaywallViewModel.swift`, `CapabilityGate.swift`, `RootView.swift`, `CoachHubView.swift`, `DailyCoachSQLiteCache.swift`, `SpacedReviewView.swift`, `SpacedReviewSQLiteCache.swift`, `MistakePassportView.swift`, `MistakePassportViewModel.swift`, `ExamReadinessHubView.swift`, `ProveItSoloTwinView.swift`, `MicroLessonDrawerView.swift`, `Localizable.xcstrings`, `project.pbxproj`, `Sprint102.storekit`, plus `EntitlementViewModelTests.swift` and the 4 new `LearningOs*Tests.swift` suites
- **Backend test sources & owner scripts:** `LearningOsCapabilityPolicyTest.java`, `TierResolutionPolicyTest.java`, `EntitlementEffectiveAccessTest.java`, `ModulithArchitectureTest.java`, `Sprint102EntitlementMigrationTest.java`, `AppleBillingEntitlementTransitionTest.java`, `EntitlementDowngradeLifecycleTest.java`, `LearningOsEntitlementControllerTest.java`, `LearningOsFeatureGuardMockMvcTest.java`, `LearningOsNonPunitiveDowngradeTest.java`, `sprint_10_2_owner_acceptance.sh`, `sprint_10_2_swift_check.sh`
