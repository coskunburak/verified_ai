# Sprint 10.1 execution report

Owner local acceptance completed. Base main at 36566f9f2f2709c63a7900681cd851314b3c1a27. Local 9.10 evidence belongs to owner run s910-20261119T111201Z. Staging and physical-device evidence remain pending.

## Owner commands

```bash
bash scripts/quality/sprint_10_1_owner_acceptance.sh &&
bash scripts/quality/sprint_10_1_swift_check.sh
```

Owner local acceptance completed on 2026-11-20. Backend and contract evidence: `.generated/sprint-10-1-owner/20261120T120320Z-34808/`. Simulator evidence: `.generated/sprint-10-1-ios/20261120T120525Z-36077/Subscription.xcresult` and the adjacent `xcode.log`. Six Maven suites ran 31 tests with zero failures, errors or skips. The iOS simulator ran 18 tests with zero failures. JSON Schema, OpenAPI and the documentation check (443 Markdown files) passed. The documentation result precedes this evidence update. No tests, builds, linters, migrations or validators executed in preparation. NO_COMMIT, NO_PUSH, NO_DEPLOY. No LLM calls, credentials, production purchases or production prices are involved.

## Offer and access behavior

Authenticated GET /v1/billing/offers and /v1/billing/entitlements also accept the /api prefix. Bearer identity and active session are required for both. An omitted locale defaults to en-US; unsupported explicit locales return the existing 400 validation envelope. All successful responses are private, no-store. The disabled result is HTTP 200 with an empty list, enabled=false and FEATURE_DISABLED. The legacy /api/v1/me/entitlements, signed transaction and webhook endpoints keep their existing wire format.

V065 seeds five disabled offers without a product ID. StoreKit returns actual display prices and terms for matching configured IDs. Missing purchase metadata, unknown eligibility and unavailable legal documents suppress the corresponding sale. PRO_PLUS stays persisted and projects as COACH_PRO. A season pass has its own offer family and signed bounded term. Minimized source references are retained for refund and audit; the new catalog is global. Interactions increment aggregate counters; no paywall event stream or training dataset is stored.

The contextual invitation is attached to completed unassisted twins. The subscription management entry continues to support user-initiated restore. Known active or unresolved flow states suppress presentation. The accepted free diagnosis and counterexample rendering paths receive no commercial dependency. Contextual visibility and purchase use a fresh catalog; localized StoreKit price and actual period are required. Eligible paid introductory discounts are suppressed until their full legal disclosures are supported.

## Release blockers

- Production configuration has no verified App Store product identifiers. Obtain commercial metadata and deliberately map stable offer codes to approved products before activation. Test resource identifiers are synthetic.
- Supply approved HTTPS BillingTermsURL and BillingPrivacyURL in the shipping app and open both on-device. Contextual checkout remains disabled without them.
- The owner run covered the V064-to-V065 upgrade, signed-evidence replay, session parity and two-user retention suites. Confirm the existing minimized financial retention policy before release. No new retention period is asserted.
- Verify six-month validity against actual Apple signed periods, including accelerated sandbox testing. The supported contextual store product has an actual subscription period. A non-renewing product without independently verified expiry or display terms cannot be enabled.
- Confirm EN/TR, actual storefront price, VoiceOver, restore, compact accessibility layout and interrupted-flow continuity. Simulator evidence cannot close the physical-device gate.
- Keep verifiedai.billing.contextual-paywall-enabled=false until staging P95, device evidence, commercial review and owner sign-off. Revert the client/API independently with the feature disabled and leave the additive database objects in place.

## Changed paths

### Database

- services/api/src/main/resources/db/migration/platform/V065__sprint_10_1_paywall_offer_catalog.sql

### Backend and backend tests

- services/api/src/main/java/com/verifiedai/billing/api/BillingReadController.java
- services/api/src/main/java/com/verifiedai/billing/application/AppleBillingApplicationService.java
- services/api/src/main/java/com/verifiedai/billing/application/AppleBillingMetrics.java
- services/api/src/main/java/com/verifiedai/billing/application/AppleProductCatalog.java
- services/api/src/main/java/com/verifiedai/billing/application/BillingLifecycleContributor.java
- services/api/src/main/java/com/verifiedai/billing/application/BillingOfferCatalogService.java
- services/api/src/main/java/com/verifiedai/billing/application/EntitlementApplicationService.java
- services/api/src/main/java/com/verifiedai/billing/application/TierResolutionPolicy.java
- services/api/src/main/java/com/verifiedai/billing/domain/model/VerifiedAppStoreTransaction.java
- services/api/src/main/java/com/verifiedai/billing/infrastructure/appstore/AppleAppStoreSignedDataVerifier.java
- services/api/src/main/java/com/verifiedai/billing/infrastructure/configuration/AppleBillingConfiguration.java
- services/api/src/main/java/com/verifiedai/billing/infrastructure/persistence/AppStoreBillingRepository.java
- services/api/src/main/java/com/verifiedai/configuration/AccountSessionStateFilter.java
- services/api/src/main/resources/application.yml
- services/api/src/test/java/com/verifiedai/billing/api/EntitlementControllerTest.java
- services/api/src/test/java/com/verifiedai/billing/application/AppleBillingApplicationServiceTest.java
- services/api/src/test/java/com/verifiedai/billing/application/BillingOfferCatalogTest.java
- services/api/src/test/java/com/verifiedai/billing/application/BillingOfferUpgradeTest.java

### OpenAPI and JSON Schema

- packages/contracts/openapi/public-api.yaml
- packages/openapi/verified-ai-core-api.yaml
- packages/schemas/billing-offers-v1.schema.json
- packages/schemas/entitlement-v2.schema.json
- packages/schemas/fixtures/billing-offers-v1.cases.json
- packages/schemas/fixtures/entitlement-v2.cases.json

### iOS and simulator tests

- apps/ios/VerifiedAI/App/RootView.swift
- apps/ios/VerifiedAI/Features/Exam/Presentation/ExamSessionView.swift
- apps/ios/VerifiedAI/Features/Home/Presentation/ColdStartDiagnosticFlowView.swift
- apps/ios/VerifiedAI/Features/Subscription/Data/AppleBillingAPI.swift
- apps/ios/VerifiedAI/Features/Subscription/Data/EntitlementAPI.swift
- apps/ios/VerifiedAI/Features/Subscription/Data/StoreKitProductRepository.swift
- apps/ios/VerifiedAI/Features/Subscription/Domain/SubscriptionCommerceModels.swift
- apps/ios/VerifiedAI/Features/Subscription/Presentation/ContextualPaywallSheetView.swift
- apps/ios/VerifiedAI/Features/Subscription/Presentation/PaywallTriggerCoordinator.swift
- apps/ios/VerifiedAI/Features/Subscription/Presentation/PaywallView.swift
- apps/ios/VerifiedAI/Features/Subscription/Presentation/PaywallViewModel.swift
- apps/ios/VerifiedAI/Features/TwinChallenge/Presentation/ProveItSoloTwinView.swift
- apps/ios/VerifiedAI/Resources/Localizable.xcstrings
- apps/ios/VerifiedAI.xcodeproj/project.pbxproj
- apps/ios/VerifiedAITests/Subscription/PaywallViewModelTests.swift
- apps/ios/VerifiedAITests/Subscription/Sprint101.storekit

### Owner scripts and validators

- scripts/quality/sprint_10_1_owner_acceptance.sh
- scripts/quality/sprint_10_1_swift_check.sh
- scripts/quality/validate_json_schemas.py
- scripts/quality/validate_sprint_10_1_contracts.py

### Documentation

- docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md
- docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md
- docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md
- docs/verified_ai_learning_platform_docs_v4/ios/18_IOS_AUTH_STOREKIT_AND_DEVICE_SECURITY.md
- docs/verified_ai_learning_platform_docs_v4/product/05_MONETIZATION_AND_ENTITLEMENTS.md
- docs/verified_ai_learning_platform_docs_v4/roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/PHASE_10_MONETIZATION_GROWTH_AND_PRODUCT_OPERATIONS.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.1_ACCEPTANCE_MATRIX.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.1_EXECUTION_REPORT.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.1_IMPLEMENTATION_MAP.md
- docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.1_PAYWALL_ARCHITECTURE_OFFER_PRESENTATION_AND_PRICING_CONFIGURATION.md
