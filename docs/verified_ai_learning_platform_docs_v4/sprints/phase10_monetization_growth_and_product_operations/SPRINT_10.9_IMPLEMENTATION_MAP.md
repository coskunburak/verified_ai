# Sprint 10.9 Implementation Map — Organic Growth Loops, Sharing, Referral, and Store Optimization Hooks

## 1. `PRODUCTION_EXECUTION_V3` 12-Item Decomposition

1. **Owning Bounded Context / Module:**
   - Primary owning Spring Modulith module: `com.verifiedai.growth` (`@ApplicationModule(displayName = "Growth")`, narrow public SPI `com.verifiedai.growth.application` exposed via `@NamedInterface("application")`).
   - Allowed outbound dependencies (`INV-GRW-007`): `com.verifiedai.featureflags.application..` (`FeatureFlagEvaluationPort`), `com.verifiedai.billing.application..` (`EntitlementApplicationService` read-only if needed), and `com.verifiedai.sharedkernel..`.
   - Read-only cross-table eligibility checks (user account active state, account age, verified learning actions, active `exam_sessions`, recent `wrong_answer_reports` friction) execute strictly via `SELECT` queries in `com.verifiedai.growth.infrastructure.GrowthReadJdbcRepository`.
   - Zero peer modules may depend on `com.verifiedai.growth.{domain,infrastructure,api}..`, and `com.verifiedai.growth` never depends on `attempt`, `mistake`, `mastery`, `studyplan`, `exam`, `tutoring`, `notification`, `identity`, `analytics`, `admin`, `ai`, `solving`, `verification`, or `problem`.

2. **Domain States / Invariants Touched (`INV-GRW-001`..`INV-GRW-007` & `INV-MON-001`):**
   - `ShareableArtifactType`: `WEEKLY_PROGRESS_SUMMARY`, `MASTERY_MILESTONE`, `COUNTERFACTUAL_CHALLENGE_CARD`, `EXAM_READINESS_MILESTONE`.
   - `ShareableArtifactStatus`: `ACTIVE`, `REVOKED`, `EXPIRED`.
   - `ReferralAttributionSource`: `DEEP_LINK`, `SHARE_CARD`, `MANUAL_CODE`, `WEEKLY_PDF_QR`.
   - `ReferralAttributionStatus` & `ReferralAttributionStateMachine`:
     - `ATTRIBUTED -> {QUALIFIED, REJECTED_ABUSE, EXPIRED}`
     - `QUALIFIED -> {REWARD_GRANTED, REJECTED_ABUSE}`
     - `REWARD_GRANTED`, `REJECTED_ABUSE`, `EXPIRED` -> `{}` (terminal, immutable; illegal transitions throw `409 GROWTH_INVALID_TRANSITION`).
   - `StoreReviewTriggerMoment`: `INDEPENDENT_TWIN_MASTERED`, `MASTERY_MILESTONE_UNLOCKED`, `WEEKLY_PROGRESS_WIN`, `MOCK_EXAM_COMPLETED`.
   - `StoreReviewOutcome`: `PROMPT_SHOWN`, `PROMPT_DISMISSED`, `PROMPT_DEFERRED`, `PROMPT_DECLINED_PERMANENTLY`.
   - `StoreReviewIneligibilityReason`: `ELIGIBLE`, `INELIGIBLE_ACTIVE_TIMER`, `INELIGIBLE_USER_DECLINED`, `INELIGIBLE_RECENT_FRICTION`, `INELIGIBLE_INSUFFICIENT_ENGAGEMENT`, `INELIGIBLE_ANNUAL_CAP_REACHED`, `INELIGIBLE_COOLDOWN_ACTIVE`.
   - `INV-GRW-001`: Zero raw student content and zero PII in shareable artifacts or public deep links (`rawContentRedacted = true`, `trainingEligible = false`, strict per-type summary allowlist, rejection of emails/phones/URLs/JWTs/LaTeX/banned lexicon via `422 GROWTH_PII_OR_RAW_CONTENT_REJECTED`).
   - `INV-GRW-002`: Deterministic anti-abuse referral enforcement under PostgreSQL `pg_advisory_xact_lock` (no self-referral, single attribution per referred learner, no 2-cycle circular referral `A <-> B`, hashed device-fingerprint replay cap `<= 2`, monthly attribution cap `<= 10`, annual reward cap `<= 12`, server-verified milestone qualification without mutating `entitlements`).
   - `INV-GRW-003`: All mutating endpoints enforce `Idempotency-Key` (`^[A-Za-z0-9:._-]{8,128}$`) + SHA-256 `payload_hash` (`REPLAYED` on exact match, `409 GROWTH_IDEMPOTENCY_CONFLICT` on payload drift).
   - `INV-GRW-004` & `INV-MON-001`: Non-spammy store review prompt suppression when `activeTimerRunning == true` or active `exam_sessions` exists, user permanently declined, recent `wrong_answer_reports` friction (`7` days), insufficient engagement (`< 3` days age or `< 3` verified actions), `>= 3` shown prompts in 365 days, or `60`-day cooldown active.
   - `INV-GRW-005`: Fail-closed feature flags (`verifiedai.growth.*` defaulting to `false`, returning `503 GROWTH_FEATURE_DISABLED`) and `$0.00` incremental AI/LLM cost (`llmTokensUsed = 0`).
   - `INV-GRW-006`: `ADR-006` `CHECK (NOT training_eligible)` + `ON DELETE CASCADE` on `users(id)` across all `V073` tables + `GrowthPrivacyLifecycleContributor` (`@Order(34)`, `category() = "growth"`).
   - `INV-GRW-007`: Acyclic Spring Modulith boundaries enforced in `ModulithArchitectureTest`.

3. **iOS Screens / View-Models / Use Cases Touched (`apps/ios/VerifiedAI`):**
   - `AppRouter.swift` & `DeepLinkRouter.swift`: `AppRoute.sharedArtifact(token:)` (`verified-ai://share/<token>`) and `AppRoute.referralInvite(code:)` (`verified-ai://referral/<code>`) with strict regex & traversal rejection.
   - `Features/Growth/Domain/`: `GrowthModels.swift`, `GrowthServicing.swift`.
   - `Features/Growth/Data/`: `GrowthAPI.swift`, `StoreKitReviewPrompter.swift` (live `SKStoreReviewController` / `StoreKit.requestReview` wrapper + `StubStoreReviewPrompter` + backup-excluded SHA-256 `GrowthDeviceFingerprintStore`), `FixtureGrowthAPI.swift`.
   - `Features/Growth/Presentation/`: `GrowthSharingViewModel.swift` (`@MainActor @Observable`, `safeToPrompt` timer suppression, full `reset()`), `GrowthShareAndReferralView.swift` (Concept B flat editorial UI, `320pt` & `xSmall..AX3`, VoiceOver, Light/Dark, Reduce Motion), `SharedArtifactPreviewSheet.swift`.
   - `AppDependencies.swift`, `AccountSettingsView.swift`, `RootView.swift`, `Localizable.xcstrings` (`growth.share.*`, `growth.referral.*`, `growth.review.*` EN/TR), `project.pbxproj`.

4. **Backend Commands / Queries / Policies / Adapters Touched:**
   - **Domain:** `ShareableArtifactType`, `ShareableArtifactStatus`, `ReferralAttributionSource`, `ReferralAttributionStatus`, `ReferralAttributionStateMachine`, `StoreReviewTriggerMoment`, `StoreReviewOutcome`, `StoreReviewIneligibilityReason`, `GrowthContentSanitizer`, `ReferralCodeGenerator`, `StoreReviewEligibilityPolicy`.
   - **Application:** `ShareableArtifactService`, `ReferralAttributionService`, `StoreReviewPromptService`, `GrowthPrivacyLifecycleContributor`, `GrowthMetrics`, plus immutable views (`ShareableArtifactView`, `PublicShareArtifactView`, `ReferralSummaryView`, `ReferralAttributionResultView`, `StoreReviewEligibilityView`, `StoreReviewEventResultView`).
   - **Infrastructure:** `GrowthProperties`, `GrowthConfiguration`, `GrowthReadJdbcRepository`, `GrowthWriteJdbcRepository`.
   - **API:** `GrowthSharingController`, `GrowthReferralController`, `GrowthStoreReviewController`, and immutable DTOs in `com.verifiedai.growth.api.dto`.
   - **Shared / Security:** `ApiErrorCode` (13 `GROWTH_*` codes appended), `SecurityConfiguration` (`HttpMethod.GET` `permitAll()` ONLY for `/api/v1/growth/shares/public/*` and `/v1/growth/shares/public/*`).

5. **Database Migrations / Indexes Touched:**
   - `V001`–`V072` remain byte-for-byte immutable. Do NOT alter `V070` (56-event catalog) or insert rows into `V071` (`feature_flag_definitions`, `experiment_definitions`).
   - Forward migration `V073__sprint_10_9_organic_growth_sharing_referrals_and_review_hooks.sql` adds:
     - `growth_referral_codes` (`uq_growth_referral_codes_user`, `uq_growth_referral_codes_code`, `ck_grw_ref_code_training_ineligible`).
     - `growth_referral_attributions` (`uq_grw_ref_attr_referred_user`, `uq_grw_ref_attr_user_idem`, `ck_grw_ref_attr_no_self`, `uq_grw_ref_attr_referrer_device`, `idx_grw_ref_attr_referrer_time`, `idx_grw_ref_attr_device_hash`, `ck_grw_ref_attr_training_ineligible`).
     - `growth_shareable_artifacts` (`uq_grw_share_token`, `uq_grw_share_user_idem`, `ck_grw_share_redacted_mandatory`, `ck_grw_share_expiry_bounds`, `idx_grw_share_user_created`, `ck_grw_share_training_ineligible`).
     - `growth_store_review_events` (`uq_grw_review_user_idem`, `idx_grw_review_user_occurred`, `ck_grw_review_training_ineligible`).

6. **API / Event / Schema Changes:**
   - 9 endpoint pairs (18 paths across `/v1/growth/...` and `/api/v1/growth/...`) with `Cache-Control` response headers, `bearerAuth` (`security: []` ONLY on `GET /growth/shares/public/{shareToken}`), and `ProblemDetails` error responses.
   - 8 Draft 2020-12 JSON Schemas + 8 positive fixtures under `packages/schemas/growth/`.
   - Byte-for-byte identical OpenAPI dual mirrors: `packages/contracts/openapi/public-api.yaml` and `packages/openapi/verified-ai-core-api.yaml`.

7. **AI Capability / Route Implications:**
   - `$0.00` incremental AI/LLM spend (`llmTokensUsed = 0`, `inferenceCostUsd = 0.00`). Zero AI provider calls, zero prompt templates, and zero route changes.

8. **Privacy / Security Implications:**
   - Public deep-link redaction (`rawContentRedacted = true`, zero `userId` or raw student content), non-enumerable 192-bit base64url share tokens, BOLA-checked revocation (`404 GROWTH_SHARE_NOT_FOUND` on foreign owner), `410 Gone` (`GROWTH_SHARE_UNAVAILABLE`) on revoked/expired public lookups, SHA-256 hashed installation salt (never raw IDFV/IDFA/IMEI), `CHECK (NOT training_eligible)` across all 4 `V073` tables, `ON DELETE CASCADE` on `users(id)`, and `GrowthPrivacyLifecycleContributor` (`@Order(34)`).

9. **Telemetry / SLO / Cost Implications:**
   - Low-cardinality Micrometer counters via `GrowthMetrics`:
     - `verifiedai.growth.share.total{type,action}`
     - `verifiedai.growth.referral.total{outcome}`
     - `verifiedai.growth.review_prompt.total{trigger,outcome}`
   - Zero user IDs, share tokens, referral codes, device hashes, or student content in metric tags or structured logs.

10. **Test / Evaluation Evidence Required (Suites A–E + iOS):**
    - Suite A: `GrowthContentSanitizerAndStateMachineTest`, `StoreReviewEligibilityPolicyTest`.
    - Suite B: `Sprint109GrowthMigrationTest` (Testcontainers PostgreSQL 16, `.target("72") -> .target("73")`).
    - Suite C: `GrowthAntiAbuseAndGdprIT` (Testcontainers PostgreSQL 16).
    - Suite D: `GrowthApiSecurityAndRedactionIT` (`@SpringBootTest` + MockMvc + Testcontainers).
    - Suite E: `ModulithArchitectureTest`, `ModularityTest`, `Sprint108AdminMigrationTest`, `Sprint107FeatureFlagMigrationTest`, `Sprint106AnalyticsMigrationTest`, plus `validate_sprint_10_9_contracts.py`, `validate_json_schemas.py`, `check_contracts.py`, `docs_check.py`.
    - iOS: `GrowthLoopsAndDeepLinkTests` (`SWIFT_STRICT_CONCURRENCY=complete`) + `plutil -lint`.

11. **Rollout / Feature-Flag Strategy:**
    - Three fail-closed flags in `application.yml` defaulting to `false`:
      - `verifiedai.growth.sharing-enabled` (`VERIFIEDAI_GROWTH_SHARING_ENABLED:false`)
      - `verifiedai.growth.referral-enabled` (`VERIFIEDAI_GROWTH_REFERRAL_ENABLED:false`)
      - `verifiedai.growth.store-review-enabled` (`VERIFIEDAI_GROWTH_STORE_REVIEW_ENABLED:false`)
    - Optional runtime kill-switch evaluation via `FeatureFlagEvaluationPort.evaluateBoolean(...)` with safe fallback (`true`) so V071 seeded flag counts remain untouched while operators retain kill-switch leverage if configured.

12. **Rollback / Recovery Plan:**
    - Set `VERIFIEDAI_GROWTH_SHARING_ENABLED=false`, `VERIFIEDAI_GROWTH_REFERRAL_ENABLED=false`, and `VERIFIEDAI_GROWTH_STORE_REVIEW_ENABLED=false` to fail-close all growth endpoints with `503 GROWTH_FEATURE_DISABLED` without dropping `V073` tables or modifying `V001`–`V072`.

---

## 2. AI Economics / Proprietary-Model Guardrail (7 Explicit Answers)

1. **Does this add or multiply an inference call?**
   - No. Sprint 10.9 executes `0` AI/LLM inference calls (`llmTokensUsed = 0`, `inferenceCostUsd = 0.00`).
2. **What is the expected invocation rate per learner action?**
   - `0` inference invocations per share creation/lookup/revocation, referral code generation/attribution/qualification, or store review eligibility check/event record.
3. **Can deterministic code/cache remove the call?**
   - Yes; every Sprint 10.9 operation is 100% deterministic Java, PostgreSQL `JdbcClient` queries, `SecureRandom` Crockford/base64url token generation, SHA-256 hashing, advisory locks, and pure Swift/StoreKit UI.
4. **Can an economy-tier approved route satisfy the task?**
   - Not applicable; zero LLM routes are called (`$0.00` incremental cost).
5. **Is a secondary solver actually required, and what triggers it?**
   - No; Sprint 10.9 never invokes primary or secondary solvers.
6. **What telemetry will reveal cost regression?**
   - `GrowthMetrics` tracks share/referral/review outcomes with `$0.00` AI cost, while `public-share-artifact-v1` and `store-review-eligibility-v1` assert `llmTokensUsed = 0` at the contract level.
7. **Does this create data that might later be useful for training? If yes, it remains not training eligible by default and requires separate governed eligibility.**
   - No. Growth share cards, referral attributions, and store review prompt events are operational/growth records and strictly enforce `training_eligible = FALSE` (`CHECK (NOT training_eligible)` across all four `V073` tables and `"const": false` in all schemas).

---

## 3. Anti-Abuse & Privacy Threat Matrix

| Threat Vector | Attack / Failure Mode | Deterministic Control (`V073` + Domain + API) | Verification |
|---|---|---|---|
| **Self-referral** | Learner submits their own referral code to farm solve credits | `ReferralAttributionService` checks `referrerUserId.equals(referredUserId)` -> `409 GROWTH_SELF_REFERRAL_FORBIDDEN` + DB `CHECK (referrer_user_id <> referred_user_id)` | `GrowthAntiAbuseAndGdprIT`, `Sprint109GrowthMigrationTest` |
| **Circular referral (`A <-> B`)** | Two accounts refer each other to mutually harvest rewards | Advisory lock ordered by sorted UUIDs + `existsAttributionBetween(referredUserId, referrerUserId)` -> `409 GROWTH_CIRCULAR_REFERRAL_FORBIDDEN` | `GrowthAntiAbuseAndGdprIT` |
| **Duplicate referred-user attribution** | Referred learner tries attaching multiple referral codes | `UNIQUE (referred_user_id)` (`uq_grw_ref_attr_referred_user`) + service check -> `409 GROWTH_REFERRAL_ALREADY_ATTRIBUTED` (exact same key/payload replays `200 OK`) | `GrowthAntiAbuseAndGdprIT`, `Sprint109GrowthMigrationTest` |
| **Device replay & quota farming** | Multiple accounts created on one device attribute the same referrer or spam platform | Client sends SHA-256 installation salt (`^[0-9a-f]{64}$`); partial unique index `uq_grw_ref_attr_referrer_device` + device cap (`<= 2`) + monthly referrer cap (`<= 10`) + annual reward cap (`<= 12`) -> `409 GROWTH_ABUSE_LIMIT_EXCEEDED` | `GrowthAntiAbuseAndGdprIT`, `Sprint109GrowthMigrationTest` |
| **Client-forged qualification / entitlement mutation** | Malicious client calls `qualify` before completing learning work or tries flipping `entitlements` | Qualification queries server-owned `learning_profiles` / `problem_sessions` / `analytics_events` for >=1 completed learning action (`409 GROWTH_INVALID_TRANSITION` if unmet); reward recorded strictly in `growth_referral_attributions` without mutating `entitlements` | `GrowthAntiAbuseAndGdprIT` |
| **Public deep-link PII / raw work leakage** | Public share token exposes `userId`, email, raw OCR/problem text, object keys, or revoked cards | `GrowthContentSanitizer` rejects PII/URLs/JWTs/LaTeX/banned lexicon (`422 GROWTH_PII_OR_RAW_CONTENT_REJECTED`); `PublicShareArtifactView` omits `userId` and sets `rawContentRedacted = true`; revoked/expired return `410 GROWTH_SHARE_UNAVAILABLE` | `GrowthContentSanitizerAndStateMachineTest`, `GrowthApiSecurityAndRedactionIT` |
| **Review prompt spam / mid-exam interruption (`INV-MON-001`)** | Prompt fires during active timer/exam, after defect report, or repeatedly | Priority gates: active timer or `exam_sessions IN_PROGRESS` -> `INELIGIBLE_ACTIVE_TIMER`; permanent decline -> `INELIGIBLE_USER_DECLINED`; recent `wrong_answer_reports` -> `INELIGIBLE_RECENT_FRICTION`; `<3` days/actions -> `INELIGIBLE_INSUFFICIENT_ENGAGEMENT`; `>=3`/yr -> `INELIGIBLE_ANNUAL_CAP_REACHED`; `60`-day cooldown -> `INELIGIBLE_COOLDOWN_ACTIVE`; iOS checks `billingTimers.safeToPresent` | `StoreReviewEligibilityPolicyTest`, `GrowthApiSecurityAndRedactionIT`, `GrowthLoopsAndDeepLinkTests` |
