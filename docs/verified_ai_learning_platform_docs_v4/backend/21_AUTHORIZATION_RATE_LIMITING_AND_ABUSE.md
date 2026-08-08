# Authorization, Rate Limiting, and Abuse Protection

## Authorization

Every resource access verifies ownership/role. Never trust client-supplied userId or UUID obscurity.

Authenticated principal determines the current user.

## Roles

Initial:
- USER
- ADMIN_SUPPORT
- ADMIN_ENGINEERING

Apply least privilege. Support access to raw student content is restricted and auditable.

## Premium authorization

Costly operations use server capability guards such as `PremiumCapability.VERIFIED_SOLVE`. Guard evaluates entitlement + quota.

## Rate limits

Dimensions:
- user,
- IP,
- endpoint,
- cost class,
- risk/device signal.

Protect auth, upload, solve, tutor, document import and billing sync.

Sprint 3.8 implementation adds Redis-backed fixed-window rate limiting for sensitive Phase 3 endpoints. Keys are derived from policy name and client IP, then hashed before Redis storage. Redis is disposable infrastructure; PostgreSQL remains the durable source of truth.

| Policy | Limit | Failure posture |
|---|---:|---|
| `POST /api/v1/auth/apple` | 10/minute | fail closed |
| `POST /api/v1/auth/refresh` | 20/minute | fail closed |
| `POST /api/v1/auth/logout` | 30/minute | fail open |
| `POST /api/v1/me/billing/apple/transactions` | 20/5 minutes | fail open |
| `POST /api/v1/me/data-exports` | 3/hour | fail open |
| `POST /api/v1/me/deletion-request*` | 5/hour | fail closed |
| `POST /api/v1/webhooks/apple/app-store` | 120/minute | fail open |

Denied requests return Problem Details with `RATE_LIMIT_EXCEEDED` and `Retry-After`. Redis outages increment degraded-open metrics where policy allows fail-open behavior.

Request-bound hardening rejects oversized sensitive request bodies and oversized `Authorization`, `X-Request-Id`, or `Idempotency-Key` headers with stable `REQUEST_TOO_LARGE` problem details. Correlation IDs are sanitized before log/MDC use.

## Abuse scenarios

### Automated solving/scraping
Quotas, anomaly detection, throttling and optional attestation signals.

### Prompt injection in problem text
Treat all content as untrusted data. Do not concatenate into system instructions. Tool calls remain allowlisted.

### Oversized/malicious upload
MIME/type/size/dimension/page limits; document scanning/isolation policy.

### Entitlement replay
Server verifies and idempotently records external transaction/events.

### Refresh-token theft
Rotation, family tracking, reuse detection and revocation.

Sprint 3.2 implementation records backend sessions and refresh token chains in PostgreSQL. Refresh tokens are opaque high-entropy values; only SHA-256 hashes are stored. A consumed refresh token presented again revokes the session and active family members, emits `REFRESH_REUSE_DETECTED`, and returns a stable authentication error without continuing the session.

## Security logging

Record suspicious auth, refresh reuse, unusual solve volume, privileged admin access and billing anomalies without secrets or raw tokens.

Authentication logs and audit rows must not contain identity tokens, authorization codes, raw nonces, access tokens, refresh tokens, token hashes, Apple subjects as labels, or email/name profile data.

Sprint 3.8 session-state filter checks every protected `/api/v1/**` access token against the current backend session and user status. A revoked session cannot continue using an unexpired access token; deleted/non-active accounts are blocked with stable account-state errors.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI cost-abuse controls

Expensive AI endpoints require layered limits by authenticated account, IP/device risk where appropriate, entitlement, and operation. Rate limits protect infrastructure; product quotas protect economics. They are related but not interchangeable.

Detect repeated retries, image flooding, automated solving abuse, account sharing patterns, and route-escalation abuse. A malicious user must not be able to force premium/secondary routes by crafting untrusted client flags.
<!-- HYBRID_AI_STRATEGY_V3:END -->
