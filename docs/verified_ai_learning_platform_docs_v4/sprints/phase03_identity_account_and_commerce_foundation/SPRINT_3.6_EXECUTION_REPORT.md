# Sprint 3.6 Execution Report

Status: COMPLETE with external App Store Server API and Server Notifications V2 validation blocked.

## Dependency Governance

- Dependency: `com.apple.itunes.storekit:app-store-server-library:5.2.0`.
- Purpose: official App Store Server API client and signed-data verification for transaction JWS, renewal JWS, and Server Notifications V2 `signedPayload`.
- Java compatibility: Java 11+ library used under Java 21.
- License: MIT.
- Security posture: official Apple-maintained implementation is preferred over custom certificate-chain/JWS validation. Only the current major release receives security updates per Apple repository guidance.
- Secret handling: issuer ID, key ID, private key PEM, root certificates, bundle ID, App Apple ID, and environment are bound through typed Spring configuration. No `.p8` or private key material is committed.

## Implementation

### App Store Server API

- Added `AppStoreServerGateway` port.
- Added Apple library-backed gateway for:
  - Get Transaction Info
  - Get Transaction History V2
  - Get All Subscription Statuses
  - Request Test Notification
  - Get Test Notification Status
- Added fail-fast production configuration validation for Apple billing credentials and production App Apple ID.

### Server Notifications V2

- Added public webhook: `POST /api/v1/webhooks/apple/app-store`.
- Endpoint does not use user bearer authentication; authenticity comes from Apple signed-data verification.
- Added notification inbox table with unique `notification_uuid`, processing status, payload digest, signed date, environment, type, subtype, and failure fields.
- Duplicate notification UUIDs are idempotent and do not re-apply entitlement changes.
- Inner `signedTransactionInfo` and `signedRenewalInfo` are independently verified by the Apple signed-data verifier adapter.

### Subscription Lifecycle

- Added subscription projection states:
  - `ACTIVE`
  - `GRACE_PERIOD`
  - `BILLING_RETRY`
  - `EXPIRED`
  - `REVOKED`
- Apple notification raw status values are mapped to the subscription state where present.
- Transaction expiration, revocation/refund-style events, renewal billing retry, and grace period fields converge through a single backend entitlement recalculation path.

### Entitlement Reconciliation

```text
Verified Apple transaction / notification
  -> app_store_transactions
  -> app_store_subscriptions
  -> EntitlementApplicationService
  -> backend-authoritative capability policy
```

Accessible subscription states map to entitlement `ACTIVE`, `GRACE_PERIOD`, or `BILLING_RETRY`. `EXPIRED` and `REVOKED` remove the App Store paid entitlement and fall back to default FREE unless another entitlement source is later introduced.

## Validation

| Check | Result |
| --- | --- |
| Backend compile | PASS |
| Backend billing slice | PASS, 12 tests across billing/entitlement |
| Clean PostgreSQL migration through V005 | PASS via Testcontainers |
| Notification expiry -> FREE lifecycle test | PASS |
| Transaction ownership theft rejection test | PASS |
| App Store Server API sandbox | BLOCKED: credentials unavailable |
| Server Notifications V2 test notification | BLOCKED: credentials and public HTTPS endpoint unavailable |

## Observability

- Added Micrometer counters for purchase evidence accepted/rejected and notification processing status.
- No metric labels include user ID, raw JWS, Apple transaction ID, or email.

## Technical Debt

New external validation items are recorded in the technical debt register:

- `TD-BILL-001`: real StoreKit sandbox purchase validation pending.
- `TD-BILL-002`: App Store Server Notifications V2 public endpoint validation pending.
- `TD-BILL-003`: App Store Server API production/sandbox credential validation pending.

## Exit Decision

Sprint 3.6 implementation is complete for repository-local code paths: official Apple server library integration, signed notification verification adapter, durable inbox, dedupe, subscription projection, lifecycle-to-entitlement mapping, and reconciliation by subscription status, transaction history, and transaction info. External Apple sandbox/API validation remains blocked until App Store Connect credentials and a public webhook are available.
