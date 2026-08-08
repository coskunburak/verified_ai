# Sprint 3.4 Execution Report — Entitlement Domain and Server-Authoritative Access Policy

## Status

Sprint 3.4 status: `COMPLETE`

NotebookLM MCP status: `CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`

## Source Evidence

- NotebookLM identified Sprint 3.4 ownership as server-authoritative entitlement semantics, with the backend as the access-policy authority and iOS limited to presentation and refresh state.
- Local canonical reads supplied the full source detail from `product/05_MONETIZATION_AND_ENTITLEMENTS.md`, `domain/07_DOMAIN_MODEL_AND_AGGREGATES.md`, `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`, `architecture/14_API_DESIGN_AND_CONTRACTS.md`, `ios/15_IOS_ARCHITECTURE_AND_FILE_HIERARCHY.md`, `ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`, `ios/18_IOS_AUTH_STOREKIT_AND_DEVICE_SECURITY.md`, `backend/20_BACKEND_MODULE_CONTRACTS.md`, `backend/21_AUTHORIZATION_RATE_LIMITING_AND_ABUSE.md`, `data/22_POSTGRESQL_DATA_MODEL.md`, `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md`, `roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`, and `quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`.

## Capability and Requirement Closure

- `CAP-BILL-001`: moved to `Complete`.
- `CAP-BILL-004`: moved to `Partial` because durable lifecycle statuses and access semantics exist, while StoreKit/App Store transition processing remains Sprint 3.5/3.6.
- `REQ-BILL-001`: moved to `Satisfied`.
- `REQ-BILL-002`: moved to `Foundation`.

## Backend Implementation

- Added the `billing` bounded-context package with API, application, domain, and persistence layers.
- Added the authenticated current-user endpoint:
  - `GET /api/v1/me/entitlements`
- Added typed entitlement tier, status, source, and premium capability models.
- Added centralized `requireCapability(UUID, PremiumCapability)` policy enforcement in the application layer.
- Default FREE entitlement resolution is lazy, idempotent, and protected by a PostgreSQL advisory transaction lock.
- Normal clients have no public entitlement mutation endpoint.

## Database Migration

- Added `V004__create_entitlements.sql`.
- Added `entitlements` with one row per user, `user_id` foreign key to `users`, unique `user_id`, explicit tier/source/status checks, effective and expiry timestamps, indexes for current-user/status access, timestamps, and optimistic `version`.

## iOS Implementation

- Added `Features/Subscription/Domain/EntitlementModels.swift`.
- Added `Features/Subscription/Data/EntitlementAPI.swift`.
- Added `Features/Subscription/Data/EntitlementDisplayCache.swift`.
- Added `Features/Subscription/Presentation/EntitlementViewModel.swift`.
- Added `Features/Subscription/Presentation/CapabilityGate.swift`.
- Updated `AppDependencies` with entitlement API and display cache wiring.
- Updated `RootView` so authenticated, onboarded users bootstrap entitlement state before reaching the home shell.
- Updated `HomePlaceholderView` to present current tier state and route gated capability affordances through `CapabilityGate`.

## Security and Privacy

- Entitlement reads require authenticated sessions.
- Entitlement ownership is derived only from the authenticated access-token principal.
- iOS entitlement cache is display-only and overwritten by server refresh.
- Backend policy, not client UI, decides whether a premium capability is allowed.
- Entitlement metrics use low-cardinality tier, status, and capability labels only.

## Observability

- Backend counters added:
  - `entitlement.resolution.total`
  - `entitlement.access.allowed.total`
  - `entitlement.access.denied.total`
- iOS logs privacy-safe event names for entitlement bootstrap, refresh, offline-cache fallback, server overwrite, and failed refresh paths.

## Tests

Backend focused tests:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin:$PATH mvn -q -Dtest=EntitlementApplicationServiceTest,EntitlementControllerTest test
```

Result: `PASS`

iOS focused tests:

```text
xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -derivedDataPath .generated/DerivedData -only-testing:VerifiedAITests/EntitlementViewModelTests
```

Result: `PASS`

## Integration Demo Evidence

- Backend tests demonstrate authenticated default FREE resolution, centralized premium capability denial, lifecycle-state access for grace and billing-retry states, PRO_PLUS access, public self-promotion rejection, and concurrent one-row initialization with PostgreSQL Testcontainers.
- iOS tests demonstrate server entitlement load, FREE capability gating, offline cached presentation state, no-cache failure behavior, and server refresh overwriting a tampered cached PRO_PLUS state.

## Technical Debt

- No new Sprint 3.4 debt introduced.
- StoreKit purchase/restore UX, App Store Server API validation, signed notification processing, and reconciliation are intentionally deferred to Sprint 3.5/3.6.
- Existing `TD-AUTH-001` and `TD-AUTH-002` remain open and are not closed by this sprint.

## Exit Decision

Sprint 3.4 is complete. Sprint 3.5 may begin.
