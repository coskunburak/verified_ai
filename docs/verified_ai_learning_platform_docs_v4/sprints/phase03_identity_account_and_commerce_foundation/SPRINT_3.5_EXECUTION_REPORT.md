# Sprint 3.5 Execution Report

Status: COMPLETE with external App Store Connect validation blocked.

## Evidence

- NotebookLM MCP status: `CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`.
- Canonical docs read: monetization and entitlements, iOS StoreKit/security, backend module contracts, entitlement state machine, API contracts, data model, privacy/security, observability, RTM, capability coverage, Sprint 3.5 and 3.6 specs.
- Apple documentation checked: StoreKit `Product.products(for:)`, `Product.purchase(options:)`, `appAccountToken`, `Transaction.updates`, `Transaction.currentEntitlements`, `Transaction.unfinished`, `Transaction.finish()`, `AppStore.sync()`, App Store Server API, Server Notifications V2, and Apple App Store Server Java Library 5.2.0.

## Implementation

### iOS

- Added `SubscriptionCommerceModels.swift` for StoreKit products, transaction evidence, purchase outcomes, and service protocols.
- Added `StoreKitProductRepository.swift` using StoreKit 2 product loading, purchase with backend-owned `appAccountToken`, verified transaction JWS extraction, current entitlement collection, unfinished transaction recovery, transaction updates, and finish-after-backend-ack.
- Added `AppleBillingAPI.swift` for:
  - `GET /api/v1/me/billing/apple/configuration`
  - `POST /api/v1/me/billing/apple/transactions`
- Added `StoreKitTransactionObserver.swift`, started once after authenticated session restore.
- Added `PaywallViewModel.swift` with explicit states: `idle`, `loadingProducts`, `ready`, `purchasing`, `pending`, `verifyingDeviceTransactions`, `submittingToBackend`, `refreshingEntitlement`, `completed`, `cancelled`, `offline`, `empty`, `failed`.
- Added `PaywallView.swift` and wired subscription entry from `HomePlaceholderView`.

### Backend

- Added authenticated purchase evidence endpoint: `POST /api/v1/me/billing/apple/transactions`.
- Added backend configuration endpoint: `GET /api/v1/me/billing/apple/configuration`.
- Added backend-owned stable `commerce_account_tokens.app_account_token`.
- Added Apple signed-data verifier port and official Apple library-backed adapter.
- Added backend product catalog mapping App Store product ID to internal plan and entitlement tier.
- Added ownership checks for `appAccountToken`, transaction ID, and original transaction ID.
- Added idempotency key handling and duplicate-safe transaction/subscription persistence.

### Database

- Added `V005__create_app_store_billing.sql`.
- Added decoded transaction fields, environment separation, payload digest storage, and uniqueness on `(environment, transaction_id)`.
- Raw StoreKit/App Store JWS is not retained. The system stores SHA-256 payload digests plus decoded commercial fields required for audit, reconciliation, and entitlement policy.

## Validation

| Check | Result |
| --- | --- |
| iOS app build | PASS |
| iOS subscription tests | PASS, 9 tests |
| Backend billing tests | PASS |
| Clean PostgreSQL migration through V005 | PASS via Testcontainers |
| Local StoreKit Test | NOT_RUN |
| Apple sandbox purchase | BLOCKED: App Store Connect configuration unavailable |

## External Blockers

See `APP_STORE_CONNECT_SETUP_CHECKLIST.md`.

## Exit Decision

Sprint 3.5 has the production architecture foundation in code: StoreKit 2 product loading, purchase/restore states, transaction observation, backend evidence submission, backend JWS verification adapter, App Account Token ownership, persistence, idempotency, and server-authoritative entitlement update. Real App Store sandbox validation remains external and must not be reported as passed.
