# Sprint 10.3 execution report

Status: `LOCAL_AUTOMATED_GATES_PASSED_MANUAL_GATES_PENDING`. All Sprint 10.3 Flyway migration, fair-use policy, PostgreSQL quota & AI cost circuit-breaker ledger, spend guard / solve integrations, GDPR export/deletion, JSON Schema / OpenAPI dual-mirror contracts, iOS Concept B non-anxious UI, A–E test/contract sources, and owner acceptance scripts are implemented on top of Sprint 10.1 (`V065`) and Sprint 10.2 (`V066`) and passed owner-executed automated local backend, contract, documentation, and iOS simulator gates (`NO_COMMIT`, `NO_PUSH`, `NO_DEPLOY`, `ZERO_SECRETS`).

## Owner commands

```bash
bash scripts/quality/sprint_10_3_owner_acceptance.sh && bash scripts/quality/sprint_10_3_swift_check.sh
```

## Owner local acceptance evidence

- **Backend, migration, concurrency, API, contracts, and docs (`2026-11-22`):** `bash scripts/quality/sprint_10_3_owner_acceptance.sh` completed with `PASS preflight`, `PASS backend`, `PASS contracts` (`validate_json_schemas.py`, `validate_sprint_10_2_contracts.py`, `validate_sprint_10_3_contracts.py`, `check_contracts.py`), `PASS documentation` (`448 Markdown files`), and final marker `SPRINT_10_3_OWNER_LOCAL_AUTOMATED_GATES_PASSED_MANUAL_GATES_PENDING` (`.generated/sprint-10-3-owner/20261122T091350Z-56271`).
- **iOS (`SWIFT_STRICT_CONCURRENCY=complete`, `2026-11-22`):** `bash scripts/quality/sprint_10_3_swift_check.sh` completed with `** TEST SUCCEEDED **` and `PASS local Sprint 10.3 subscription simulator tests` (`.generated/sprint-10-3-ios/20261122T092136Z-59400/Sprint103Subscription.xcresult`).

## Implemented quota, circuit-breaker, and fair-use controls

- `V067__sprint_10_3_entitlement_quota_ledgers.sql` introduces `entitlement_quota_ledgers` (with `accumulated_ai_cost_usd` generated column, `CHECK (NOT training_eligible)`, unique `(user_id, period_key)`, and `ON DELETE CASCADE`) and `entitlement_quota_consumptions` (`UNIQUE (user_id, idempotency_key)` preventing double deduction). `V065` and `V066` remain byte-for-byte untouched.
- `FairUsePolicy` and `BillingQuotaProperties` project deterministic UTC windows (`YYYY-MM-DD` daily for `FREE`; `YYYY-MM` monthly for `PRO`, `COACH_PRO`, `YKS_SEASON_PASS`), limits (`3` daily checks / `1` handwriting diagnostic / `$0.08` monthly cap for `FREE`; `150` / `$2.50` for `PRO`; `400` / `$4.20` for `COACH_PRO` and `YKS_SEASON_PASS`), and unconditional exemption for `DETERMINISTIC_TWIN` and `DETERMINISTIC_SYMBOLIC_VERIFY` (`INV-QUOTA-001`).
- `QuotaLedgerService` enforces atomic concurrency via `pg_advisory_xact_lock` + `SELECT ... FOR UPDATE` inside `REQUIRES_NEW` transactions, idempotency (`INV-QUOTA-004`), actual-cost reconciliation (`reconcileActualCost`), pre-dispatch release (`releaseReservation`), and Micrometer counters (`quota_consumed`, `quota_exhausted_paywall_triggered`, `user_ai_cost_circuit_breaker_tripped`).
- `PostgresAiSpendGuard`, `PrimarySolveService`, and `SecondarySolveService` integrate via `@Autowired(required = false)` setters (Spring Modulith `NO_FIELD_INJECTION` compliant), coordinate on shared key `ai-op:<operationId>`, and return ethical HTTP 429 `ProblemDetails` (`ApiErrorCode.QUOTA_EXHAUSTED`, `ApiErrorCode.AI_COST_CIRCUIT_BREAKER_TRIPPED`) or graceful deterministic fallback without overage fees.
- `BillingLifecycleContributor` includes `quotaLedgers` and `quotaConsumptions` in GDPR `exportUserData` and deletes both tables in `deleteUserData`.
- `EntitlementApplicationService` populates additive `quotaSummary` on `GET /v1/billing/entitlements` and `/api/v1/billing/entitlements` (returning deterministic defaults with zero DB writes when `verifiedai.billing.quota-ledger-enabled=false`) while leaving `/api/v1/me/entitlements` unchanged.
- `entitlement-v2.schema.json`, both OpenAPI mirrors (`public-api.yaml`, `verified-ai-core-api.yaml`), and `entitlement-v2.cases.json` (16 existing + 4 new Sprint 10.3 fixtures = 20 fixtures) are synchronized.
- iOS `EntitlementModels.swift`, `EntitlementViewModel.swift`, `CapabilityGate.swift` (`QuotaUsageCardView` with `ViewThatFits` for `320pt` and `xSmall`–`AX3`), `AccountSettingsView.swift`, `PaywallView.swift`, `RootView.swift`, `Localizable.xcstrings` (`en` + `tr`), and `LearningOsQuotaSummaryTests.swift` are updated for `SWIFT_STRICT_CONCURRENCY=complete`.

## Release blockers (manual sign-off required)

- Keep `verifiedai.billing.quota-ledger-enabled: false` and `verifiedai.billing.learning-os-tiers-enabled: false` in default config until staging P95 latency verification, physical-device EN/TR VoiceOver (`320pt`, `xSmall`–`AX3`, light/dark), live App Store Connect commerce validation, and explicit owner rollout sign-off complete (`PENDING_MANUAL`).

## Changed and added file groups

- **Migration:** `services/api/src/main/resources/db/migration/platform/V067__sprint_10_3_entitlement_quota_ledgers.sql`
- **Backend billing, AI & solving:** `BillingQuotaProperties.java`, `QuotaOperationType.java`, `QuotaSummary.java`, `QuotaConsumptionDecision.java`, `FairUsePolicy.java`, `QuotaLedgerService.java`, `EffectiveEntitlementAccess.java`, `EntitlementApplicationService.java`, `BillingLifecycleContributor.java`, `AppleBillingConfiguration.java`, `PostgresAiSpendGuard.java`, `PrimarySolveService.java`, `SecondarySolveService.java`, `ApiErrorCode.java`, `application.yml`
- **Contracts & validators:** `packages/schemas/entitlement-v2.schema.json`, `packages/schemas/fixtures/entitlement-v2.cases.json`, `packages/contracts/openapi/public-api.yaml`, `packages/openapi/verified-ai-core-api.yaml`, `scripts/quality/validate_sprint_10_3_contracts.py`, `scripts/quality/validate_json_schemas.py`
- **iOS app & tests:** `EntitlementModels.swift`, `EntitlementViewModel.swift`, `CapabilityGate.swift`, `AccountSettingsView.swift`, `PaywallView.swift`, `RootView.swift`, `Localizable.xcstrings`, `project.pbxproj`, `LearningOsQuotaSummaryTests.swift`
- **Backend tests & owner scripts:** `FairUsePolicyTest.java`, `QuotaLedgerServiceUnitTest.java`, `Sprint103QuotaMigrationTest.java`, `QuotaLedgerConcurrencyIT.java`, `BillingReadControllerQuotaIT.java`, `Sprint102EntitlementMigrationTest.java` (`.target("66")`), `sprint_10_3_owner_acceptance.sh`, `sprint_10_3_swift_check.sh`
- **Documentation:** `SPRINT_10.3_IMPLEMENTATION_MAP.md`, `SPRINT_10.3_EXECUTION_REPORT.md`, `DOCUMENTATION_MANIFEST.md`
