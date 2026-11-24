# Sprint 10.9 Execution Report: Organic Growth Loops, Sharing, Referral, and Store Optimization Hooks

- **Status:** `IMPLEMENTED_NOT_RUN` (implemented in workspace under `OWNER_RUNS_ALL_TESTS` policy; single-line owner acceptance command below)
- **Sprint Spec:** `sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.9_ORGANIC_GROWTH_LOOPS_SHARING_REFERRAL_AND_STORE_OPTIMIZATION_HOOKS.md`
- **Implementation Map:** `sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.9_IMPLEMENTATION_MAP.md`
- **Incremental AI/LLM Token Spend:** `0` (`$0.00` — pure deterministic PostgreSQL `JdbcClient` queries, `SecureRandom` Crockford/base64url token generation, SHA-256 payload/device hashing, advisory locks, and StoreKit/SwiftUI UI)

## Implemented Components

### 1. Database Schema & Migrations
- Preserved `V001`–`V072` byte-for-byte immutable (including `.target("70")`, `.target("71")`, and `.target("72")` migration pins, and zero edits to `V070` 56-event catalog or `V071` feature flag/experiment seed tables).
- Added forward migration `V073__sprint_10_9_organic_growth_sharing_referrals_and_review_hooks.sql` defining:
  - `growth_referral_codes` (one Crockford-safe code per learner, `ON DELETE CASCADE`).
  - `growth_referral_attributions` (`UNIQUE (referred_user_id)`, `ck_grw_ref_attr_no_self`, partial unique `uq_grw_ref_attr_referrer_device`, `Idempotency-Key` + 64-hex SHA-256 `payload_hash`, `ON DELETE CASCADE`).
  - `growth_shareable_artifacts` (non-enumerable base64url `share_token`, `ck_grw_share_redacted_mandatory`, `ck_grw_share_expiry_bounds` `<= 90 days`, `ON DELETE CASCADE`).
  - `growth_store_review_events` (idempotent outcome ledger `PROMPT_SHOWN`/`PROMPT_DISMISSED`/`PROMPT_DEFERRED`/`PROMPT_DECLINED_PERMANENTLY`, `ON DELETE CASCADE`).
  - Enforced `CHECK (NOT training_eligible)` across all four `V073` tables (`ADR-006`).

### 2. Backend Modulith (`com.verifiedai.growth`)
- Maintained acyclic Spring Modulith boundaries (`INV-GRW-007`): `com.verifiedai.growth` depends exclusively on `com.verifiedai.featureflags.application..`, `com.verifiedai.billing.application..`, and `com.verifiedai.sharedkernel..`, with constructor injection exclusively (`NO_FIELD_INJECTION`, zero `@Autowired`) and zero `@EnableScheduling` in `VerifiedAiApplication.java`.
- Implemented `GrowthContentSanitizer`, `ReferralCodeGenerator`, `ReferralAttributionStateMachine`, and `StoreReviewEligibilityPolicy`.
- Implemented `GrowthReadJdbcRepository` (strictly read-only `SELECT` checks for account age, verified learning actions, active `exam_sessions`, and recent `wrong_answer_reports` friction) and `GrowthWriteJdbcRepository` (strictly `V073` tables + `pg_advisory_xact_lock`).
- Implemented `ShareableArtifactService`, `ReferralAttributionService` (anti-abuse enforcement + server-verified milestone qualification without mutating `entitlements`), `StoreReviewPromptService`, `GrowthPrivacyLifecycleContributor` (`@Order(34)`, `category() = "growth"`), and `GrowthMetrics`.
- Exposed 9 REST endpoint pairs (`/v1/growth/...` and `/api/v1/growth/...`) across `GrowthSharingController`, `GrowthReferralController`, and `GrowthStoreReviewController` with `Cache-Control` headers, fail-closed `verifiedai.growth.*=false` flags in `application.yml`, 13 `GROWTH_*` `ApiErrorCode` entries, and `HttpMethod.GET` `permitAll()` ONLY for `/api/v1/growth/shares/public/*` and `/v1/growth/shares/public/*` in `SecurityConfiguration.java`.

### 3. Contracts & Schemas
- Added 8 Draft 2020-12 JSON schemas and 8 positive fixtures under `packages/schemas/growth/`.
- Synchronized OpenAPI dual mirrors `packages/contracts/openapi/public-api.yaml` and `packages/openapi/verified-ai-core-api.yaml` byte-for-byte with all 18 Sprint 10.9 paths and component schemas.

### 4. iOS Client (`apps/ios/VerifiedAI`, `SWIFT_STRICT_CONCURRENCY=complete`)
- Extended `AppRouter.swift` and `DeepLinkRouter.swift` with `verified-ai://share/<token>` and `verified-ai://referral/<code>` (with strict regex & path-traversal rejection).
- Added `Features/Growth/` (`GrowthModels.swift`, `GrowthServicing.swift`, `GrowthAPI.swift`, `StoreKitReviewPrompter.swift` with `GrowthDeviceFingerprintStore`, `FixtureGrowthAPI.swift`, `GrowthSharingViewModel.swift`, `GrowthShareAndReferralView.swift`, `SharedArtifactPreviewSheet.swift`) and wired `AppDependencies.swift`, `AccountSettingsView.swift`, `RootView.swift` (`safeToPrompt: { billingTimers.safeToPresent }` per `INV-MON-001` and full `reset()` on logout/deletion), EN/TR `Localizable.xcstrings` (`growth.*`), and `project.pbxproj`.

### 5. Verification Suites & Scripts
- Backend A–E suites: `GrowthContentSanitizerAndStateMachineTest`, `StoreReviewEligibilityPolicyTest`, `Sprint109GrowthMigrationTest`, `GrowthAntiAbuseAndGdprIT`, `GrowthApiSecurityAndRedactionIT`, plus `ModulithArchitectureTest`.
- iOS unit suite: `GrowthLoopsAndDeepLinkTests` (`SWIFT_STRICT_CONCURRENCY=complete`).
- Quality scripts: `scripts/quality/validate_sprint_10_9_contracts.py` (hooked into `validate_json_schemas.py`), `scripts/quality/sprint_10_9_owner_acceptance.sh`, and `scripts/quality/sprint_10_9_swift_check.sh`.

## Owner Acceptance Verification Command

Run locally from the repository root to execute automated backend, migration, Modulith, schema, OpenAPI, docs, and Swift strict-concurrency checks:

```bash
bash scripts/quality/sprint_10_9_owner_acceptance.sh && bash scripts/quality/sprint_10_9_swift_check.sh
```

## Rollback Plan

1. Set `VERIFIEDAI_GROWTH_SHARING_ENABLED=false`, `VERIFIEDAI_GROWTH_REFERRAL_ENABLED=false`, and `VERIFIEDAI_GROWTH_STORE_REVIEW_ENABLED=false` (fail-closed defaults return `503 GROWTH_FEATURE_DISABLED` across all sharing, referral, and store review endpoints).
2. Never drop `V073` tables in-place or modify immutable `V001`–`V072` migrations.

## Pending Manual Gates (`PENDING_MANUAL`)

- Owner local execution of `bash scripts/quality/sprint_10_9_owner_acceptance.sh && bash scripts/quality/sprint_10_9_swift_check.sh` with Docker/Testcontainers and Xcode Simulator.
- Staging deep-link and public share redaction smoke check (`GET /api/v1/growth/shares/public/{shareToken}` `200` redacted vs `410` after revoke).
- Physical iOS EN/TR VoiceOver verification at `320pt` and `xSmall`–`AX3` Dynamic Type plus real `StoreKit` `requestReview` sheet inspection.
