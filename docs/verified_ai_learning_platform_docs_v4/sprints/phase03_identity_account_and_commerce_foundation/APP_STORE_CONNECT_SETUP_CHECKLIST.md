# App Store Connect Setup Checklist

Status: external validation blocker for Sprint 3.5 and Sprint 3.6.

This checklist is intentionally separate from local StoreKit Test. Local StoreKit configuration can validate client purchase UX and StoreKit transaction delivery on a simulator, but it does not prove App Store Connect product state, sandbox purchase behavior, App Store Server API credentials, or public Server Notifications V2 delivery.

## Required App Store Connect Items

| Item | Required Value / Evidence | Current Status |
| --- | --- | --- |
| Bundle ID | `com.verifiedai.learning` or the production bundle configured for release | BLOCKED: not verified in App Store Connect |
| App Apple ID | Numeric production App Apple ID for server JWS verification | BLOCKED: not available in repository or environment |
| Subscription group | Production V1 subscription group for Verified AI plans | BLOCKED: not verified in App Store Connect |
| Product IDs | Canonical product IDs mapped to internal plans and entitlement tiers | BLOCKED: not verified in App Store Connect |
| Product states | Ready for Submit / Approved / Sandbox-testable state per product | BLOCKED: not verified in App Store Connect |
| Sandbox tester | Account able to purchase all configured subscription products | BLOCKED: not available in workspace |
| In-App Purchase key | Issuer ID, key ID, and private `.p8` outside Git | BLOCKED: not available in workspace |
| Apple root certificates | Current Apple root certificates configured outside Git | BLOCKED: not available in workspace |
| Public HTTPS webhook | Reachable `POST /api/v1/webhooks/apple/app-store` endpoint | BLOCKED: not deployed in this workspace |
| Test notification | App Store Server API Request Test Notification / Get Test Notification Status evidence | BLOCKED: credentials and public endpoint unavailable |

## Product Mapping Rules

The backend must remain the trusted mapping source:

```text
App Store product ID
  -> internal commercial plan ID
  -> entitlement tier
  -> subscription group
```

iOS may display only StoreKit-provided localized metadata for products returned by the backend configuration endpoint. iOS must not send or infer entitlement tier authority.

## Required Validation Evidence Before Production

1. Real sandbox purchase for each configured product.
2. StoreKit verified transaction submitted to backend with backend-owned `appAccountToken`.
3. Backend signed-data verification using Apple root certificates, bundle ID, App Apple ID where required, and configured environment.
4. App Store Server API transaction lookup for a sandbox transaction.
5. App Store Server API transaction history reconciliation for the subscription lineage.
6. Server Notifications V2 test notification delivered to the public HTTPS webhook.
7. Duplicate notification delivery verified as idempotent.
8. Refund/revocation and expiration lifecycle event validated against backend entitlement recalculation.

Do not mark Apple sandbox, App Store Server API sandbox, or Server Notifications V2 external validation as `PASS` until the evidence above exists.
