# Sprint 3.2 Execution Report — Backend Sessions, Access Tokens, Refresh Rotation, and Revocation

## Scope

Implemented backend-authoritative sessions, short-lived access tokens, opaque rotating refresh tokens, refresh-token reuse detection, logout/session revocation, Keychain-backed iOS token storage and single-flight refresh.

## NotebookLM Sources

NotebookLM MCP status: `CONNECTED_WITH_LOCAL_SOURCE_FALLBACK`.

Important source-of-truth documents consulted:
- `ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md`
- `ios/18_IOS_AUTH_STOREKIT_AND_DEVICE_SECURITY.md`
- `backend/21_AUTHORIZATION_RATE_LIMITING_AND_ABUSE.md`
- `data/22_POSTGRESQL_DATA_MODEL.md`
- `security/35_SECURITY_THREAT_MODEL.md`
- `security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md`
- `quality/40_TEST_STRATEGY.md`
- `quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`

## Requirements

- `REQ-AUTH-001`: Satisfied by refresh rotation, revocation, reuse detection and tests.
- `REQ-AUTH-002`: Foundation advanced by authenticated `/api/v1/**` policy.

## Capabilities

- `CAP-ID-002`: Complete.
- `CAP-ID-003`: Partial. Session records and revocation exist; user-facing device/session management remains Sprint 3.8.

## Architecture Impact

- Backend owns session lifecycle and refresh-token family authority.
- Access tokens are backend-issued JWTs signed with RSA.
- Refresh tokens are opaque high-entropy secrets stored hashed in PostgreSQL.
- iOS owns local Keychain storage and refresh coordination only.

## Files Created

- `AccessTokenIssuer.java`
- `RefreshTokenGenerator.java`
- `RefreshTokenHasher.java`
- session/refresh JPA entities and repositories
- `AuthenticationSessionStore.swift`
- `AuthenticationAPI.swift`
- `AuthenticationSessionStoreTests.swift`

## Files Modified

- Spring Security route policy and bearer-token validation.
- iOS `APIClient`, `RequestInterceptor`, `AuthTokenProvider`, app dependencies and root auth routing.
- OpenAPI public API contract.
- canonical data/backend/iOS docs, capability matrix, traceability matrix and debt register.

## Migrations

- `sessions`: server-authoritative session state.
- `refresh_tokens`: hashed opaque refresh-token chain with `used_at`, `revoked_at` and `replaced_by_id`.
- `auth_security_events`: bounded privacy-safe auth/security events.

## Endpoints

- `POST /api/v1/auth/refresh`: permit all with valid refresh-token flow.
- `GET /api/v1/auth/session`: authenticated.
- `POST /api/v1/auth/logout`: authenticated, revokes current server session.

## Security Controls

- Short-lived JWT access tokens.
- RSA signing key configuration with local/test generated dev keys and production/staging fail-fast behavior.
- Opaque CSPRNG refresh tokens.
- SHA-256 refresh token hashes in storage.
- Transactional one-time refresh rotation.
- Refresh-token reuse detection with session/family revocation.
- Logout revokes backend session and active refresh tokens.
- iOS Keychain storage.
- iOS single-flight refresh and safe one-time GET retry after `AUTH_TOKEN_EXPIRED`.
- Privacy-safe auth metrics and audit records.

## Tests

- Backend: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn -q test` — PASS.
- iOS: `xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination 'platform=iOS Simulator,name=iPhone 16 Pro'` — PASS.

## Integration Validation

Validated controlled flow through automated fixtures:
- Apple credential fixture verified cryptographically against a local JWKS.
- Backend user/identity/session/refresh token persistence through PostgreSQL Testcontainers.
- Refresh rotation and reuse-detection revocation.
- iOS Keychain-backed session persistence.
- iOS single-flight refresh and retry of eligible authenticated request.

## Known Limitations

- Distributed auth endpoint rate limiting is deferred to Sprint 3.8 and tracked as `TD-AUTH-002`.
- Device/session management UX and revoke-other-sessions are not implemented in Sprint 3.2.

## Exit Gate

`SPRINT_3.2 = COMPLETE`
