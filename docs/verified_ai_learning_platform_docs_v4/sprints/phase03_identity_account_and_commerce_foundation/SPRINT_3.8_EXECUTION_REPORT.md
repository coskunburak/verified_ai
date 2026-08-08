# Sprint 3.8 Execution Report

## Status

Complete for current Phase 3 auth, billing, privacy, and abuse surfaces. `TD-AUTH-002` is closed.

## Implemented Controls

- Added Redis-backed fixed-window rate limiting through `RateLimiter`, `RateLimitPolicy`, `RateLimitDecision`, and `RedisRateLimiter`.
- Added request policies for Apple sign-in, refresh, logout, purchase evidence, data export, deletion request/confirmation, and App Store webhook ingestion.
- Added explicit fail-closed policies for sign-in, refresh, and deletion request flows; fail-open policies increment degraded-open metrics.
- Added `RequestBoundsFilter` for sensitive JSON body limits and oversized header rejection.
- Sanitized inbound correlation IDs before MDC/log use.
- Added backend session-state validation for protected `/api/v1/**` requests so revoked sessions and deleted/non-active accounts cannot continue using unexpired access tokens.
- Moved session-state checks behind identity application services to preserve Spring Modulith boundaries.
- Moved Apple identity timestamp validation under the verifier's injected clock while preserving signature, issuer, audience, expiry, issued-at, and nonce checks.

## Metrics and Audit

- `security.rate_limit.denied.total`
- `security.rate_limit.degraded_open.total`
- `security.request.rejected.total`
- existing auth, billing, and privacy events/counters remain low-cardinality and secret-safe.

## Validation

- Backend full suite: `mvn -q test` passed, 42 tests, including rate-limit, request-bound, revoked-token, modularity, Apple verifier, and privacy lifecycle tests.
- iOS full test invocation passed, 30 tests.
- OpenAPI YAML parse passed.

## Debt

- Closed `TD-AUTH-002`.
- Kept `TD-AUTH-001`, `TD-BILL-001`, `TD-BILL-002`, `TD-BILL-003`, and `TD-BILL-004` open because they require external Apple Developer/App Store Connect/public-webhook evidence.
