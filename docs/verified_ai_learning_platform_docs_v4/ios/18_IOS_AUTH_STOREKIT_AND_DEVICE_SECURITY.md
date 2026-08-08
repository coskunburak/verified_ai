# iOS Authentication, StoreKit, and Device Security

## Authentication

Primary: Sign in with Apple.

Client sends required Apple identity material to backend. Backend validates trusted token properties and issues platform access/refresh tokens.

Implementation contract:
- iOS generates a cryptographically random raw nonce.
- The Apple authorization request receives the SHA-256 nonce.
- The backend receives the raw nonce with the Apple identity token and verifies the Apple `nonce` claim after cryptographic token validation.
- iOS never treats Apple credential receipt as platform authentication until the backend exchange succeeds.

## Access and refresh

Access token: short-lived.
Refresh token: rotated, revocable, reuse-detected and stored in Keychain.

iOS stores only the current backend-issued access token, refresh token, user ID, session ID and expiry timestamps in Keychain-backed secure storage. API requests attach the access token. A `AUTH_TOKEN_EXPIRED` response triggers at most one shared refresh task; eligible safe requests retry once after refresh. Unsafe mutations are not blindly replayed.

## Logout

- revoke/clear server refresh token where appropriate,
- clear local tokens,
- update app authentication state,
- retain or purge local cache according to product/privacy policy.

## Account deletion

Account deletion is initiated from authenticated account settings and completed only after backend confirmation. The iOS client does not send a `userId`; the backend derives the account from the access token. After the backend returns deleted state, iOS clears Keychain-backed session material and resets authenticated view models so no revoked access/refresh token remains available for future requests.

## StoreKit 2

Client:
- fetch backend Apple billing configuration,
- request StoreKit `Product` metadata only for backend-configured product IDs,
- display StoreKit display name, description, localized price and subscription period,
- initiate purchase with the backend-issued `appAccountToken`,
- accept only StoreKit verified transactions,
- submit verified transaction JWS evidence to the backend,
- observe unfinished transactions and `Transaction.updates`,
- call restore via `AppStore.sync()` and current entitlement scanning,
- finish transactions only after backend acknowledgement.

Backend:
- verify Apple-signed transaction JWS,
- enforce product mapping and `appAccountToken` ownership,
- process App Store Server Notifications V2,
- reconcile through the App Store Server API,
- maintain entitlement.

The transaction observer starts once for an authenticated session. It first drains unfinished transactions, then listens for StoreKit transaction updates that may arrive from outside the foreground purchase flow. Pending purchases are not treated as access. Unverified StoreKit results are rejected client-side and never produce paid access.

## Premium gating

Client may optimistically render cached entitlement, but server remains authority for premium API work.

## Device security

Potential later use of App Attest/DeviceCheck as anti-abuse signals. Never make a fragile device heuristic the sole user-access decision without recovery.

## Permissions

Camera/photo permissions are requested just-in-time with clear purpose. Privacy manifest and usage descriptions are maintained.

## Logging

Never log identity tokens, refresh tokens, provider secrets, full transaction signatures or raw student images.
