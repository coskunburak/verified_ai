# Sprint 10.1 implementation map

Base: main, 36566f9f2f2709c63a7900681cd851314b3c1a27. Two owner-provided untracked files in prompts/. No checkout change. Owner run s910-20261119T111201Z on 2026-11-19 covers local 9.10 only.

| Evidence | Observed contract | Implementation decision |
|---|---|---|
| V004 lines 1–24, V005 lines 1–55, migration directory ends at V064 | persisted FREE/PRO/PRO_PLUS; transaction expiry and immutable product ID | ADR-101-01/03/04. Add independent V065 catalog. Preserve prior migrations, project coaching for PRO_PLUS. Season requires a verified bounded term. |
| application.yml lines 208–220 | app.billing.apple.products is empty | ADR-101-02/08. Plans start disabled without SKU. App Store Connect metadata UNVERIFIED. No catalog prices. |
| EntitlementController lines 11–24, SecurityConfiguration lines 93–95 | JWT-scoped legacy read, both versioned prefixes authenticated | ADR-101-05/06. Add twin offers and entitlements routes with private no-store. Preserve legacy JSON. |
| AppleBillingApplicationService lines 70–111 | signed evidence, ownership, idempotent transaction projection | reuse purchase/restore path under /api/v1/me/billing/apple. No client grants. |
| BillingLifecycleContributor lines 28–52 | per-user export, minimized financial retention | Global catalog has no subject data. No behavioral event table. |
| RootView lines 224,273–277; ExamSessionView 112; ColdStartDiagnosticFlowView 15–24; ProveItSoloTwinView 35–46 | profile purchase sheet, exam session status, diagnostic flow stage and twin status | ADR-101-07. Gate navigation on timer state and preserve accepted free outputs. |
| Sprint 9.10 acceptance matrix lines 15–16 | staging and device evidence pending | ADR-101-09. Default disabled. Local automation cannot unlock rollout. |
| roadmap/88 lines 159–165; architecture/90 line 104 | value-first offers, CNT-LOS-12 | ADR-101-10. Exclude spend, quotas and new global enforcement. |

Rollback: disable contextual display first, roll back clients/API independently and leave the unused additive catalog in place. Never rewrite historical receipts. PREVIEW_IN_TEST requires synthetic fixtures. ENABLED_BY_OWNER requires verified commercial metadata and owner release sign-off.

## Runtime dependencies and route matrix

| Route pair or dependency | Implementation | Contract |
|---|---|---|
| GET /v1/billing/offers and /api/v1/billing/offers | BillingReadController → BillingOfferCatalogService → global V065 catalog and approved Apple mappings | billing-offers-v1; no prices, tr-TR/en-US |
| GET /v1/billing/entitlements and /api/v1/billing/entitlements | JWT → EntitlementApplicationService → TierResolutionPolicy | entitlement-v2; effective tier, legacy tier, state, source, expiry and capabilities |
| POST /v1/billing/interactions/{kind} and /api/v1 equivalent | Authenticated allowlisted UI signals → Micrometer | Counts only; disabled contextual impressions ignored |
| Billing session-validation filter | Both prefixes enter AccountSessionStateFilter | Revoked session regression case |
| ContextualPaywallSheetView → PaywallViewModel → StoreKitProductRepository | Fresh feed and entitlement read, StoreKit price/period, current app account token | Verified signed transaction and existing server sync before finish |
| BillingLifecycleContributor | Transactional, subject-scoped deletion/export | Immutable receipt history retained; no per-user paywall telemetry |
