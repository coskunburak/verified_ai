# iOS Authentication, StoreKit, and Device Security

## Authentication

Primary: Sign in with Apple.

Client sends required Apple identity material to backend. Backend validates trusted token properties and issues platform access/refresh tokens.

## Access and refresh

Access token: short-lived.
Refresh token: rotated, revocable, reuse-detected and stored in Keychain.

## Logout

- revoke/clear server refresh token where appropriate,
- clear local tokens,
- update app authentication state,
- retain or purge local cache according to product/privacy policy.

## StoreKit 2

Client:
- fetch products,
- display StoreKit price/period,
- initiate purchase,
- observe transaction updates,
- finish transaction,
- call backend sync.

Backend:
- validate transaction state,
- process App Store server notifications,
- maintain entitlement.

## Premium gating

Client may optimistically render cached entitlement, but server remains authority for premium API work.

## Device security

Potential later use of App Attest/DeviceCheck as anti-abuse signals. Never make a fragile device heuristic the sole user-access decision without recovery.

## Permissions

Camera/photo permissions are requested just-in-time with clear purpose. Privacy manifest and usage descriptions are maintained.

## Logging

Never log identity tokens, refresh tokens, provider secrets, full transaction signatures or raw student images.
